package io.docflow.api.core.extraction.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExtractionCorrectionRequest(
        @NotNull String vendorName,
        @NotNull String invoiceNumber,
        @NotNull LocalDate invoiceDate,
        @NotNull BigDecimal totalAmount,
        BigDecimal taxAmount,
        String currency
) { }
