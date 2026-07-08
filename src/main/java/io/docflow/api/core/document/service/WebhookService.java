package io.docflow.api.core.document.service;

import io.docflow.api.core.document.dto.DocumentWebhookEvent;
import io.docflow.api.infrastructure.util.HashUtils;
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
@Slf4j
public class WebhookService {

    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 5000;

    private final RestClient restClient = RestClient.builder()
            .requestFactory(createTimeoutRequestFactory())
            .build();

    private static SimpleClientHttpRequestFactory createTimeoutRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return factory;
    }

    private boolean isUrlAllowed(String url) {
        try {
            URI uri = new java.net.URI(url);
            String host = uri.getHost();
            String scheme = uri.getScheme();

            if (host == null || scheme == null) return false;

            scheme = scheme.toLowerCase();
            if (!"http".equals(scheme) && !"https".equals(scheme)) return false;

            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) return false;

            for (InetAddress address : addresses) {
                if (isBlockedAddress(address)) {
                    log.warn("SSFR: şüphesi: {} hostname'i engellenmiş bir IP'ye ({}) çözüldü", host, address.getHostAddress());
                    return false;
                }
            }

            return true;
        } catch (UnknownHostException e) {
            log.warn("Webhook hostname'i çözülemedi, engellendi: {}", url);
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isBlockedAddress(InetAddress address) {
        if (address.isLoopbackAddress()) return true;
        if (address.isLinkLocalAddress()) return true;
        if (address.isSiteLocalAddress()) return true;
        if (address.isAnyLocalAddress()) return true;
        if (address.isMulticastAddress()) return true;

        if (address instanceof Inet4Address && "169.254.169.254".equals(address.getHostAddress())) {
            return true;
        }

        return false;
    }

    public void sendCallback(String callbackUrl, String secret, DocumentWebhookEvent event) {
        if (callbackUrl == null || callbackUrl.isBlank()) {
            return;
        }

        if (!isUrlAllowed(callbackUrl)) {
            log.warn("GÜVENLİ OLMAYAN WEBHOOK ADRESİ ENGELLENDİ: {}", callbackUrl);
            return;
        }

        try {
            String signature = HashUtils.hmacSha256(event.documentId().toString(), secret);
            log.info("Webhook gönderiliyor: {} -> {}", callbackUrl, event.documentId());

            restClient.post()
                    .uri(callbackUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Invox-Signature", signature)
                    .body(event)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Webhook başarıyla ulaştı. İmza: {}", signature);
        } catch (Exception e) {
            log.error("Webhook gönderilemedi! Hedef URL: {}", callbackUrl, e);
            // Could be Retry mechanism
        }
    }
}
