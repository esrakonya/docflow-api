package io.docflow.api.infrastructure.util;

import java.util.UUID;

public final class ApiKeyGenerator {
    private ApiKeyGenerator() { }

    public static String generateRawKey() {
        return "invox_live_" + UUID.randomUUID().toString().replace("-", "");
    }
}
