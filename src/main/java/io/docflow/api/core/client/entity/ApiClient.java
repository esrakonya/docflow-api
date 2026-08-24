package io.docflow.api.core.client.entity;

import io.docflow.api.core.billing.entity.Plan;
import jakarta.persistence.*;
import lombok.*;
import org.checkerframework.checker.units.qual.C;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "api_clients")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ApiClient {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String apiKeyHash;

    @Column(nullable = false)
    private String companyName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(name = "stripe_customer_id", unique = true)
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id")
    private String stripeSubscriptionId;

    private String webhookSecret;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClientStatus status = ClientStatus.ACTIVE;

    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
