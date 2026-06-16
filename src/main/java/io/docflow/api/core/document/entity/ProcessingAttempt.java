package io.docflow.api.core.document.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "processing_attempts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProcessingAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "document_id")
    private Document document;

    private Integer attemptNumber;
    private String status;
    private String errorMessage;
    private OffsetDateTime attemptedAt;
}
