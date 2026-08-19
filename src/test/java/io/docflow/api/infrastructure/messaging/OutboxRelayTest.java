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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the Outbox Pattern implementation.
 * Verifies that pending messages in the database are correctly read,
 * serialized, dispatched to Kafka, and marked as processed atomically.
 */
@ExtendWith(MockitoExtension.class)
public class OutboxRelayTest {
    @Mock private OutboxRepository outboxRepository;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock private ObjectMapper objectMapper;
    @InjectMocks private OutboxRelay outboxRelay;

    @Test
    @DisplayName("Should relay pending messages to Kafka and set processed=true")
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
        assertTrue(message.isProcessed(), "Outbox message should be marked as processed after a successful relay!");
        verify(outboxRepository, times(1)).save(message);
    }

    @Test
    @DisplayName("A failing (poison) message should NOT block other pending messages in the same batch")
    void shouldNotBlockOtherMessagesWhenOneFails() throws Exception {
        OutboxMessage poisonMessage = OutboxMessage.builder()
                .id(UUID.randomUUID())
                .topic("topic-fail")
                .payload("{\"documentId\":\"broken\"}")
                .processed(false)
                .retryCount(0)
                .failed(false)
                .build();

        OutboxMessage healthyMessage = OutboxMessage.builder()
                .id(UUID.randomUUID())
                .topic("topic-ok")
                .payload("{\"documentId\":\"fine\"}")
                .processed(false)
                .retryCount(0)
                .failed(false)
                .build();

        when(outboxRepository.findTop100PendingMessages())
                .thenReturn(List.of(poisonMessage, healthyMessage));

        DocumentUploadedEvent mockEvent = new DocumentUploadedEvent(UUID.randomUUID(), "path", "type");
        when(objectMapper.readValue(anyString(), eq(DocumentUploadedEvent.class))).thenReturn(mockEvent);

        CompletableFuture<SendResult<String, Object>> successFuture = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(anyString(), any())).thenAnswer(invocation -> {
            String topic = invocation.getArgument(0);
            if ("topic-fail".equals(topic)) {
                throw new RuntimeException("Kafka broker unreachable");
            }
            return successFuture;
        });

        outboxRelay.relayMessages();

        verify(kafkaTemplate, times(1)).send(eq("topic-fail"), any());
        verify(kafkaTemplate, times(1)).send(eq("topic-ok"), any());

        assertEquals(1, poisonMessage.getRetryCount());
        assertFalse(poisonMessage.isFailed());
        assertFalse(poisonMessage.isProcessed());
        assertEquals("Kafka broker unreachable", poisonMessage.getLastError());

        assertTrue(healthyMessage.isProcessed());

        verify(outboxRepository, times(1)).save(poisonMessage);
        verify(outboxRepository, times(1)).save(healthyMessage);
    }

    @Test
    @DisplayName("A message should be marked as permanently failed (dead-letter) after reaching MAX_RETRIES")
    void shouldMarkAsFailedAfterMaxRetries() throws Exception {
        OutboxMessage message = OutboxMessage.builder()
                .id(UUID.randomUUID())
                .topic("document-uploaded")
                .payload("{\"documentId\":\"still-broken\"}")
                .processed(false)
                .retryCount(4)
                .failed(false)
                .build();

        when(outboxRepository.findTop100PendingMessages()).thenReturn(List.of(message));
        when(objectMapper.readValue(anyString(), eq(DocumentUploadedEvent.class)))
                .thenReturn(new DocumentUploadedEvent(UUID.randomUUID(), "path", "type"));
        when(kafkaTemplate.send(anyString(), any()))
                .thenThrow(new RuntimeException("Still failing"));

        outboxRelay.relayMessages();

        assertEquals(5, message.getRetryCount());
        assertTrue(message.isFailed(), "Message should be moved to dead-letter state after MAX_RETRIES");
        assertFalse(message.isProcessed());
        verify(outboxRepository, times(1)).save(message);
    }
}
