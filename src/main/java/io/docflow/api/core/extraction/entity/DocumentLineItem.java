package io.docflow.api.core.extraction.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "document_line_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DocumentLineItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "extracted_data_id")
    private ExtractedData extractedData;

    private String description;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;
}
