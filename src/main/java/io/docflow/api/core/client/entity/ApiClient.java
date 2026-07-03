package io.docflow.api.core.client.entity;

import jakarta.persistence.*;
import lombok.*;

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

    private String planTier;
    private String webhookSecret;
    private Integer monthlyQuota;
    private OffsetDateTime createdAt;
}
