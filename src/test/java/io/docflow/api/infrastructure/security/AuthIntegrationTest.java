package io.docflow.api.infrastructure.security;

import io.docflow.api.TestcontainersConfiguration;
import io.docflow.api.core.billing.entity.Plan;
import io.docflow.api.core.billing.entity.PlanTier;
import io.docflow.api.core.billing.repository.PlanRepository;
import io.docflow.api.core.client.dto.ApiKeySummaryResponse;
import io.docflow.api.core.client.dto.CreateApiKeyRequest;
import io.docflow.api.core.client.dto.CreateApiKeyResponse;
import io.docflow.api.core.client.entity.ApiClient;
import io.docflow.api.core.client.entity.ClientStatus;
import io.docflow.api.core.client.repository.ApiClientRepository;
import io.docflow.api.core.client.service.ApiKeyService;
import io.docflow.api.core.client.service.RateLimitingService;
import io.docflow.api.core.storage.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests for all three authentication mechanisms in the app, driven
 * over real HTTP against a running server ({@code webEnvironment = RANDOM_PORT})
 * rather than through mocked collaborators:
 * <ul>
 *     <li>API key auth (dashboardless REST clients, {@code X-API-KEY} header)</li>
 *     <li>Dashboard session auth (browser cookie + CSRF)</li>
 *     <li>Admin JWT auth ({@code Authorization: Bearer ...})</li>
 * </ul>
 * <p>
 * This class deliberately does <b>not</b> extend {@code BaseIntegrationTest}
 * and does <b>not</b> mock {@link io.docflow.api.core.client.service.ClientCacheService}
 * (unlike {@code BaseIntegrationTest}, which does — see its
 * {@code @MockitoBean protected ClientCacheService clientCacheService} field).
 * The real {@code RedisClientCacheService} bean runs here, so every auth
 * decision (active-key lookup, revocation, client status, expiry) is made by
 * the real {@code ApiKeyAuthenticationFilter} + service + repository chain
 * against the real Postgres testcontainer, not asserted via
 * {@code when(...).thenReturn(...)}. Extending {@code BaseIntegrationTest}
 * would silently re-introduce that mock and make every test below pass or
 * fail for the wrong reason (an unstubbed mock returns {@code Optional.empty()},
 * which looks identical to "key not found").
 * <p>
 * One infra constraint we can't route around: {@code application-test.yml}
 * excludes {@code RedisAutoConfiguration} (so tests don't depend on a live
 * Redis wiring for unrelated features), which means there's no real
 * {@link RedisConnectionFactory} bean for {@code RedisConfig} to build a
 * {@link RedisTemplate} from. We double only that minimal seam — every call
 * against it fails and is caught by {@code RedisClientCacheService}'s own
 * try/catch, which is exactly its designed "Redis is down, fall back to the
 * database" path. So this suite still exercises that fallback path for real,
 * it just never gets a cache hit.
 * <p>
 * {@link RateLimitingService} is mocked too, but for an unrelated reason: it
 * depends on a real {@code StringRedisTemplate} bean (also absent under this
 * profile), and none of the endpoints exercised here go through it anyway
 * (it's only wired into {@code DocumentController}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ApiClientRepository apiClientRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private ApiKeyService apiKeyService;

    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockitoBean
    private RedisTemplate<String, Object> redisTemplate;

    @MockitoBean
    private RateLimitingService rateLimitingService;

    @MockitoBean
    private S3Client s3Client;

    @MockitoBean
    private StorageService storageService;

    @Value("${app.admin.username}")
    private String seededAdminUsername;

    @Value("${app.admin.password}")
    private String seededAdminPassword;

    private Plan freePlan;

    @BeforeEach
    void setUp() {
        freePlan = planRepository.findByName(PlanTier.FREE)
                .orElseGet(() -> planRepository.save(Plan.builder()
                        .name(PlanTier.FREE)
                        .monthlyQuota(100)
                        .rateLimitPerMin(10)
                        .build()));
    }

    // ------------------------------------------------------------------
    // API key auth (stateless chain, X-API-KEY header)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("API-KEY: a freshly issued key authenticates through the real cache-service + DB lookup")
    void validApiKey_authenticatesForReal() {
        ApiClient client = registerActiveClient("Real Auth Co");
        CreateApiKeyResponse created = apiKeyService.createKey(client.getId(), new CreateApiKeyRequest("integration-test-key"));

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/me/keys", HttpMethod.GET, new HttpEntity<>(withApiKey(created.rawKey())), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("integration-test-key");
    }

    @Test
    @DisplayName("API-KEY: an unknown key is rejected with 401, not a 500")
    void unknownApiKey_isRejected() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/me/keys", HttpMethod.GET, new HttpEntity<>(withApiKey("does-not-exist-anywhere")), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("API-KEY: a revoked key stops authenticating on the very next request")
    void revokedApiKey_isRejected() {
        ApiClient client = registerActiveClient("Revoke Co");
        CreateApiKeyResponse toRevoke = apiKeyService.createKey(client.getId(), new CreateApiKeyRequest("key-1"));
        apiKeyService.createKey(client.getId(), new CreateApiKeyRequest("key-2")); // so revoking key-1 isn't blocked as "last active key"

        ApiKeySummaryResponse summary = apiKeyService.listKeys(client.getId()).stream()
                .filter(k -> "key-1".equals(k.label()))
                .findFirst()
                .orElseThrow();
        apiKeyService.revokeKey(client.getId(), summary.id());

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/me/keys", HttpMethod.GET, new HttpEntity<>(withApiKey(toRevoke.rawKey())), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("API-KEY: a suspended client's otherwise-valid key is still rejected")
    void suspendedClient_isRejectedEvenWithValidKey() {
        ApiClient client = apiClientRepository.save(ApiClient.builder()
                .companyName("Suspended Co")
                .plan(freePlan)
                .status(ClientStatus.SUSPENDED)
                .build());
        CreateApiKeyResponse created = apiKeyService.createKey(client.getId(), new CreateApiKeyRequest("still-technically-valid"));

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/me/keys", HttpMethod.GET, new HttpEntity<>(withApiKey(created.rawKey())), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("SUSPENDED");
    }

    @Test
    @DisplayName("API-KEY: a missing header never reaches a business endpoint")
    void missingApiKey_isRejected() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/me/keys", String.class);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }

    // ------------------------------------------------------------------
    // Dashboard session auth (cookie + CSRF)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("DASHBOARD: an unauthenticated visitor is bounced to the login page, not let through")
    void unauthenticatedDashboard_redirectsToLogin() {
        ResponseEntity<String> response = followRedirects(restTemplate.getForEntity("/dashboard/keys", String.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Sign in to docFlow");
    }

    @Test
    @DisplayName("DASHBOARD: cookie+CSRF login grants access to a protected page, and logout revokes it again")
    void dashboardSessionLifecycle_endToEnd() {
        ApiClient client = registerActiveClient("Session Co");
        CreateApiKeyResponse created = apiKeyService.createKey(client.getId(), new CreateApiKeyRequest("dashboard-login-key"));

        // 1. A real browser would embed the CSRF token into the login form automatically;
        // a plain HTTP client has to fetch it first, over the same session cookie.
        ResponseEntity<Map> csrfResponse = restTemplate.getForEntity("/dashboard/csrf", Map.class);
        String csrfToken = (String) csrfResponse.getBody().get("token");
        String csrfHeaderName = (String) csrfResponse.getBody().get("headerName");
        String sessionCookie = extractSessionCookie(csrfResponse, null);
        assertThat(sessionCookie).isNotNull();

        // 2. Log in with that token, over that same session.
        HttpHeaders loginHeaders = jsonHeaders();
        loginHeaders.set(HttpHeaders.COOKIE, sessionCookie);
        loginHeaders.set(csrfHeaderName, csrfToken);
        ResponseEntity<String> loginResponse = restTemplate.postForEntity(
                "/dashboard/login", new HttpEntity<>("{\"apiKey\":\"" + created.rawKey() + "\"}", loginHeaders), String.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String authenticatedCookie = extractSessionCookie(loginResponse, sessionCookie);

        // 3. That session now sees the real, DB-backed protected page.
        HttpHeaders pageHeaders = new HttpHeaders();
        pageHeaders.set(HttpHeaders.COOKIE, authenticatedCookie);
        ResponseEntity<String> keysPage = restTemplate.exchange(
                "/dashboard/keys", HttpMethod.GET, new HttpEntity<>(pageHeaders), String.class);
        assertThat(keysPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(keysPage.getBody()).contains("dashboard-login-key");

        // 4. Log out — needs its own fresh CSRF token bound to the now-authenticated session.
        ResponseEntity<Map> logoutCsrfResponse = restTemplate.exchange(
                "/dashboard/csrf", HttpMethod.GET, new HttpEntity<>(pageHeaders), Map.class);
        String logoutCsrfToken = (String) logoutCsrfResponse.getBody().get("token");

        HttpHeaders logoutHeaders = new HttpHeaders();
        logoutHeaders.set(HttpHeaders.COOKIE, authenticatedCookie);
        logoutHeaders.set(csrfHeaderName, logoutCsrfToken);
        ResponseEntity<Void> logoutResponse = restTemplate.exchange(
                "/dashboard/logout", HttpMethod.POST, new HttpEntity<>(logoutHeaders), Void.class);
        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // 5. The exact same cookie no longer opens the protected page.
        ResponseEntity<String> afterLogout = followRedirects(restTemplate.exchange(
                "/dashboard/keys", HttpMethod.GET, new HttpEntity<>(pageHeaders), String.class));
        assertThat(afterLogout.getBody()).contains("Sign in to docFlow");
    }

    // ------------------------------------------------------------------
    // Admin JWT auth (Authorization: Bearer ...)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("ADMIN: a real login issues a JWT that a real protected endpoint accepts, and rejects garbage tokens")
    void adminJwtLifecycle_endToEnd() {
        // Seeded on startup from ADMIN_USERNAME/ADMIN_PASSWORD in application-test.yml.
        ResponseEntity<Map> loginResponse = restTemplate.postForEntity(
                "/api/v1/auth/login",
                new HttpEntity<>("{\"username\":\"" + seededAdminUsername + "\",\"password\":\"" + seededAdminPassword + "\"}", jsonHeaders()),
                Map.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = (String) loginResponse.getBody().get("token");
        assertThat(token).isNotBlank();

        HttpHeaders authHeaders = jsonHeaders();
        authHeaders.setBearerAuth(token);
        ResponseEntity<String> registerResponse = restTemplate.postForEntity(
                "/api/v1/admin/clients", new HttpEntity<>("{\"name\":\"Admin Created Co\"}", authHeaders), String.class);
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(registerResponse.getBody()).contains("Admin Created Co");

        HttpHeaders garbageAuthHeaders = jsonHeaders();
        garbageAuthHeaders.setBearerAuth("not-a-real-jwt");
        ResponseEntity<String> rejected = restTemplate.postForEntity(
                "/api/v1/admin/clients", new HttpEntity<>("{\"name\":\"Should Not Exist\"}", garbageAuthHeaders), String.class);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("ADMIN: a wrong password is rejected without issuing a token")
    void adminLogin_wrongPassword_isRejected() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/auth/login",
                new HttpEntity<>("{\"username\":\"" + seededAdminUsername + "\",\"password\":\"definitely-wrong\"}", jsonHeaders()),
                String.class);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private ApiClient registerActiveClient(String companyName) {
        return apiClientRepository.save(ApiClient.builder()
                .companyName(companyName)
                .plan(freePlan)
                .status(ClientStatus.ACTIVE)
                .build());
    }

    private HttpHeaders withApiKey(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-KEY", apiKey);
        return headers;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String extractSessionCookie(ResponseEntity<?> response, String fallback) {
        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (setCookies == null) {
            return fallback;
        }
        return setCookies.stream()
                .filter(c -> c.startsWith("JSESSIONID"))
                .findFirst()
                .map(c -> c.split(";", 2)[0])
                .orElse(fallback);
    }

    /**
     * TestRestTemplate's redirect-following behaviour depends on which HTTP
     * client is on the classpath, so we don't rely on it — we follow
     * {@code Location} manually, at most a few hops.
     */
    private ResponseEntity<String> followRedirects(ResponseEntity<String> response) {
        ResponseEntity<String> current = response;
        int hops = 0;
        while (current.getStatusCode().is3xxRedirection() && hops++ < 5) {
            URI location = current.getHeaders().getLocation();
            if (location == null) {
                break;
            }
            current = restTemplate.getForEntity(location, String.class);
        }
        return current;
    }
}