package io.docflow.api.core.extraction.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ExtractedInvoiceData(
        String vendorName,
        String invoiceNumber,
        LocalDate invoiceDate,
        LocalDate dueDate,
        String currency,
        BigDecimal totalAmount,
        BigDecimal taxAmount,
        List<LineItem> lineItems,
        BigDecimal confidence
) {
    public record LineItem(
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal
    ) {}
}
