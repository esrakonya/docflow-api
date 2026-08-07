package io.docflow.api.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.docflow.api.core.common.entity.OutboxMessage;
import io.docflow.api.core.common.repository.OutboxRepository;
import io.docflow.api.core.document.dto.DocumentUploadedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OutboxRelayTest {
    @Mock private OutboxRepository outboxRepository;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock private ObjectMapper objectMapper;
    @InjectMocks private OutboxRelay outboxRelay;

    @Test
    @DisplayName("İşlenmemiş mesajları Kafka'ya basmalı ve processed=true yapmalı")
    void shouldRelayPendingMessages() throws Exception {
        UUID messageId = UUID.randomUUID();
        OutboxMessage message = OutboxMessage.builder()
                .id(messageId)
                .topic("document-uploaded")
                .payload("{\"documentId\":\"...\"}")
                .processed(false)
                .build();

        when(outboxRepository.findTop100PendingMessages()).thenReturn(List.of(message));

        DocumentUploadedEvent mockEvent = new DocumentUploadedEvent(UUID.randomUUID(), "path", "type");
        when(objectMapper.readValue(anyString(), eq(DocumentUploadedEvent.class))).thenReturn(mockEvent);

        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(anyString(), any())).thenReturn(future);

        outboxRelay.relayMessages();

        verify(kafkaTemplate, times(1)).send(eq("document-uploaded"), any());
        assertTrue(message.isProcessed(), "Mesaj processed=true olarak işaretlenmeliydi!");
        verify(outboxRepository, times(1)).save(message);
    }
}
