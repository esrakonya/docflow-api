package io.docflow.api.core.document.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.docflow.api.core.document.dto.DocumentWebhookEvent;
import io.docflow.api.infrastructure.exception.WebhookDeliveryException;
import io.docflow.api.infrastructure.util.HashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private final ObjectMapper objectMapper;

    private final RestClient restClient = RestClient.builder()
            .requestFactory(createTimeoutRequestFactory())
            .build();

    private static SimpleClientHttpRequestFactory createTimeoutRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        return factory;
    }

    public void sendCallback(String callbackUrl, String secret, DocumentWebhookEvent event) {
        if (callbackUrl == null || callbackUrl.isBlank()) {
            return;
        }

        try {
            URI originalUri = new URI(callbackUrl);

            InetAddress safeAddress = getSafeAddress(originalUri.getHost())
                    .orElseThrow(() -> new RuntimeException("Unsafe or unresolvable destination: " + callbackUrl));

            URI safeUri = UriComponentsBuilder.fromUri(originalUri)
                    .host(safeAddress.getHostAddress())
                    .build(true)
                    .toUri();

            String jsonPayload = objectMapper.writeValueAsString(event);
            String signature = HashUtils.hmacSha256(jsonPayload, secret);

            log.info("Sending Webhook to IP: {} (Original Host: {})", safeAddress.getHostAddress(), originalUri.getHost());

            restClient.post()
                    .uri(safeUri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-DocFlow-Signature", signature)
                    .header("Host", originalUri.getHost())
                    .body(jsonPayload)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Webhook delivered successfully.");
        } catch (Exception e) {
            log.error("Webhook delivery failed for URL: {}. Reason: {}", callbackUrl, e.getMessage());
            throw new WebhookDeliveryException("Webhook failed, scheduling retry...", e);
        }
    }

    private Optional<InetAddress> getSafeAddress(String host) {
        try {
            if (host == null) return Optional.empty();

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
