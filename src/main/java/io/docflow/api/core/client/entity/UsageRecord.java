package io.docflow.api.core.client.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "usage_records")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UsageRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "client_id")
    private ApiClient client;

    @Column(name = "usage_month")
    private String usageMonth;

    @Column(name = "request_count")
    private Integer request_count;
}
