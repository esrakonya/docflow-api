package io.docflow.api.core.extraction.entity;

import io.docflow.api.core.document.entity.Document;
import io.docflow.api.core.extraction.dto.ExtractionCorrectionRequest;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "extracted_data")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExtractedData {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", unique = true)
    private Document document;

    private String vendorName;
    private String invoiceNumber;
    private LocalDate invoiceDate;
    private LocalDate dueDate;
    private BigDecimal totalAmount;

    @Column(name = "tax_amount")
    private BigDecimal taxAmount;

    private String currency;
    private BigDecimal overallConfidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String rawLlmResponse;

    @OneToMany(mappedBy = "extractedData", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DocumentLineItem> lineItems;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> validationWarnings;

    @Version
    private Long version;

    private LocalDateTime updatedAt;


    public void applyCorrection(ExtractionCorrectionRequest request) {
        this.vendorName = request.vendorName();
        this.invoiceNumber = request.invoiceNumber();
        this.invoiceDate = request.invoiceDate();
        this.totalAmount = request.totalAmount();
        this.taxAmount = request.taxAmount();
        this.currency = request.currency();
        this.updatedAt = LocalDateTime.now();
        this.overallConfidence = BigDecimal.valueOf(1.0);
    }
}
