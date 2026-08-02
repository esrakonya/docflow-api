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

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;

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

    private boolean isUrlAllowed(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            String scheme = uri.getScheme();

            if (host == null || scheme == null) return false;
            if (!"http".equals(scheme.toLowerCase()) && !"https".equals(scheme.toLowerCase())) return false;

            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) return false;

            for (InetAddress address : addresses) {
                if (isBlockedAddress(address)) {
                    log.warn("SSRF ATTEMPT: Host {} resolved to blocked IP {}", host, address.getHostAddress());
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isBlockedAddress(InetAddress address) {
        return address.isLoopbackAddress() ||
                address.isLinkLocalAddress() ||
                address.isSiteLocalAddress() ||
                address.isAnyLocalAddress() ||
                (address instanceof Inet4Address && "169.254.169.254".equals(address.getHostAddress()));
    }

    public void sendCallback(String callbackUrl, String secret, DocumentWebhookEvent event) {
        if (callbackUrl == null || callbackUrl.isBlank()) {
            return;
        }

        if (!isUrlAllowed(callbackUrl)) {
            log.warn("SSRF PROTECTION: Blocked unsafe webhook target: {}", callbackUrl);
            return;
        }

        try {
            String jsonPayload = objectMapper.writeValueAsString(event);

            String signature = HashUtils.hmacSha256(jsonPayload, secret);

            log.info("Sending Webhook: {} (DocId: {})", callbackUrl, event.documentId());

            restClient.post()
                    .uri(callbackUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-DocFlow-Signature", signature)
                    .body(jsonPayload)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Webhook delivered successfully.");
        } catch (Exception e) {
            log.error("Webhook delivery failed for URL: {}. Reason: {}", callbackUrl, e.getMessage());
            throw new WebhookDeliveryException("Webhook failed, scheduling retry...", e);
        }
    }
}
