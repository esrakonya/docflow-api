package io.docflow.api.core.document;

import io.docflow.api.BaseIntegrationTest;
import io.docflow.api.core.document.repository.DocumentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;

import static org.assertj.core.api.Assertions.assertThat;


public class DocumentIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private PostgreSQLContainer<?> postgreSQLContainer;

    @Autowired
    private KafkaContainer kafkaContainer;

    @Test
    @DisplayName("Uygulama bağlamı başarıyla yüklenmeli")
    void contextLoads() {
        assertThat(postgreSQLContainer.isRunning()).isTrue();
        assertThat(kafkaContainer.isRunning()).isTrue();
    }

    @Test
    @DisplayName("Başlangıçta veritabanı temiz olmalı")
    void shouldVerifyDatabaseIsEmptyAtStart() {
        var documents = documentRepository.findAll();
        assertThat(documents).isEmpty();
    }
}
