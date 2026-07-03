package io.docflow.api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app")
@Getter @Setter
public class AppProperties {

    private final Security security = new Security();
    private final Storage storage = new Storage();

    @Getter @Setter
    public static class Security {
        private int freeTierLimit = 3;
        private int proTierLimit = 100;
    }

    @Getter @Setter
    public static class Storage {
        private String type = "minio";
    }

    public static final String DOCUMENT_UPLOADED_TOPIC = "document-uploaded";
}
