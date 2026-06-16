package io.docflow.api.core.document.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "documents")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String originalFilename;
    private String storagePath;

    @Enumerated(EnumType.STRING)
    private DocumentStatus status;

    private String callbackUrl;
    private OffsetDateTime uploadedAt;
    private OffsetDateTime processedAt;
}
