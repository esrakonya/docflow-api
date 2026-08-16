package io.docflow.api.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.docflow.api.core.common.entity.OutboxMessage;
import io.docflow.api.core.common.repository.OutboxRepository;
import io.docflow.api.core.document.dto.DocumentUploadedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final int MAX_RETRIES = 5;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void relayMessages() {
        List<OutboxMessage> pendingMessages = outboxRepository.findTop100PendingMessages();

        if (pendingMessages.isEmpty()) {
            return;
        }

        log.debug("Processing {} outbox messages...", pendingMessages.size());

        for (OutboxMessage message : pendingMessages) {
            try {
                DocumentUploadedEvent event = objectMapper.readValue(message.getPayload(), DocumentUploadedEvent.class);
                kafkaTemplate.send(message.getTopic(), event).get(5, TimeUnit.SECONDS);

                message.setProcessed(true);
                message.setLastError(null);
                log.info("Outbox message successfully relayed to Kafka. ID: {}, Topic: {}", message.getId(), message.getTopic());

            } catch (Exception e) {
                int currentRetries = message.getRetryCount() + 1;
                message.setRetryCount(currentRetries);
                message.setLastError(e.getMessage());

                if (currentRetries >= MAX_RETRIES) {
                    message.setFailed(true);
                    log.error("Message PERMANENTLY FAILED: ID={}. Moved to dead-letter state.", message.getId());
                } else {
                    log.warn("Relay attempt {} failed for ID={}: {}", currentRetries, message.getId(), e.getMessage());
                }
            }
            outboxRepository.save(message);
        }
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanUp() {
        outboxRepository.purgeOldMessages(LocalDateTime.now().minusDays(1));
        log.info("Outbox cleanup: Old processed and failed messages purged.");
    }
}
