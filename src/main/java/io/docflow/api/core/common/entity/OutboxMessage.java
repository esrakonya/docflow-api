package io.docflow.api.core.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox_messages")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OutboxMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String topic;

    @Column(columnDefinition = "TEXT")
    private String payload;

    private LocalDateTime createdAt;

    @Builder.Default
    private boolean processed = false;

    @Builder.Default
    private int retryCount = 0;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    @Builder.Default
    private boolean failed = false;
}
