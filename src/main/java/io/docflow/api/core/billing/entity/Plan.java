package io.docflow.api.core.billing.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "plans")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class Plan {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer monthlyQuota;

    @Column(nullable = false)
    private Integer rateLimitPerMin;

    @Column(unique = true)
    private String stripePriceId;

    @Builder.Default
    private boolean active = true;
}
