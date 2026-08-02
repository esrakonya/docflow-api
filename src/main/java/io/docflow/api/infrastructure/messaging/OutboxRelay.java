package io.docflow.api.infrastructure.messaging;

import io.docflow.api.core.common.entity.OutboxMessage;
import io.docflow.api.core.common.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void relayMessages() {
        List<OutboxMessage> pendingMessages = outboxRepository.findByProcessedFalseOrderByCreatedAtAsc();

        if (pendingMessages.isEmpty()) {  return; }

        log.debug("OutboxRelay: {} adet bekleyen mesaj gönderiliyor...", pendingMessages.size());

        for (OutboxMessage message : pendingMessages) {
            try {
                kafkaTemplate.send(message.getTopic(), message.getPayload()).get(5, TimeUnit.SECONDS);

                message.setProcessed(true);
                outboxRepository.save(message);

                log.info("Outbox mesajı başarıyla Kafka'ya iletildi. ID: {}, Topic: {}", message.getId(), message.getTopic());
            } catch (Exception e) {
                log.error("Outbox mesajı iletilemedi! ID: {}. Hata: {}", message.getId(), e.getMessage());
                break;
            }
        }

    }
}
