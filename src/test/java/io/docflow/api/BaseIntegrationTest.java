package io.docflow.api;

import io.docflow.api.core.billing.entity.Plan;
import io.docflow.api.core.billing.entity.PlanTier;
import io.docflow.api.core.billing.repository.PlanRepository;
import io.docflow.api.core.client.repository.ApiClientRepository;
import io.docflow.api.core.client.repository.UsageRecordRepository;
import io.docflow.api.core.client.service.ClientCacheService;
import io.docflow.api.core.client.service.RateLimitingService;
import io.docflow.api.core.document.repository.DocumentRepository;
import io.docflow.api.core.storage.service.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.s3.S3Client;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
public abstract class BaseIntegrationTest {
    @Autowired protected ApiClientRepository apiClientRepository;
    @Autowired protected DocumentRepository documentRepository;
    @Autowired protected UsageRecordRepository usageRecordRepository;
    @Autowired protected PlanRepository planRepository;
    @Autowired protected MockMvc mockMvc;


    @MockitoBean protected ClientCacheService clientCacheService;
    @MockitoBean protected RedisTemplate<String, Object> redisTemplate;
    @MockitoBean protected RedisConnectionFactory redisConnectionFactory;
    @MockitoBean protected RateLimitingService rateLimitingService;
    @MockitoBean protected S3Client s3Client;
    @MockitoBean protected StorageService storageService;

    protected Plan getTestPlan(String planName) {
        return planRepository.findByName(planName)
                .orElseGet(() -> planRepository.save(Plan.builder()
                        .name(planName)
                        .monthlyQuota(100)
                        .rateLimitPerMin(10)
                        .build()));
    }


    protected Plan getFreePlan() {
        return getTestPlan(PlanTier.FREE);
    }
}
