package io.docflow.api.core.billing.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "processed_stripe_events")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class ProcessedStripeEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String eventId;

    private String eventType;

    private LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        processedAt = LocalDateTime.now();
    }
}
