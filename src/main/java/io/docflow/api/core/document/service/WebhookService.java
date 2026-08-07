package io.docflow.api.core.document.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.docflow.api.core.document.dto.DocumentWebhookEvent;
import io.docflow.api.infrastructure.exception.WebhookDeliveryException;
import io.docflow.api.infrastructure.util.HashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private final ObjectMapper objectMapper;

    public void sendCallback(String callbackUrl, String secret, DocumentWebhookEvent event) {
        if (callbackUrl == null || callbackUrl.isBlank()) {
            return;
        }

        try {
            URI uri = new URI(callbackUrl);
            String host = uri.getHost();

            InetAddress safeAddress = getSafeAddress(host)
                    .orElseThrow(() -> new RuntimeException("Unsafe or unresolvable destination: " + host));

            try (CloseableHttpClient httpClient = createSecureClient(host, safeAddress)) {

                RestClient secureRestClient = RestClient.builder()
                        .requestFactory(new HttpComponentsClientHttpRequestFactory(httpClient))
                        .build();

                String jsonPayload = objectMapper.writeValueAsString(event);
                String signature = HashUtils.hmacSha256(jsonPayload, secret);

                log.info("Sending Secure Webhook to {} (Pinned IP: {})", host, safeAddress.getHostAddress());

                secureRestClient.post()
                        .uri(uri)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-DocFlow-Signature", signature)
                        .body(jsonPayload)
                        .retrieve()
                        .toBodilessEntity();
            }

            log.info("Webhook delivered successfully.");
        } catch (Exception e) {
            log.error("Webhook delivery failed for URL: {}. Reason: {}", callbackUrl, e.getMessage());
            throw new WebhookDeliveryException("Webhook failed, scheduling retry...", e);
        }
    }

    private CloseableHttpClient createSecureClient(String host, InetAddress ip) {
        PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(new org.apache.hc.client5.http.DnsResolver() {
                    @Override
                    public InetAddress[] resolve(String hostName) throws java.net.UnknownHostException {
                        if (hostName.equalsIgnoreCase(host)) {
                            return new InetAddress[]{ip};
                        }
                        return InetAddress.getAllByName(hostName);
                    }

                    @Override
                    public String resolveCanonicalHostname(String hostName) throws java.net.UnknownHostException {
                        return hostName;
                    }
                })
                .build();

        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectTimeout(Timeout.of(3, TimeUnit.SECONDS))
                        .setResponseTimeout(Timeout.of(5, TimeUnit.SECONDS))
                        .build())
                .build();
    }

    private Optional<InetAddress> getSafeAddress(String host) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) return Optional.empty();

            InetAddress target = addresses[0];

            if (isBlockedAddress(target)) {
                log.warn("SECURITY ALERT: SSRF attempt blocked for host: {} (Resolved to: {})", host, target.getHostAddress());
                return Optional.empty();
            }

            return Optional.of(target);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private boolean isBlockedAddress(InetAddress address) {
        return address.isLoopbackAddress() ||
                address.isLinkLocalAddress() ||
                address.isSiteLocalAddress() ||
                address.isAnyLocalAddress() ||
                (address instanceof Inet4Address && "169.254.169.254".equals(address.getHostAddress()));
    }
}
