package io.docflow.api.core.extraction.validator;

import io.docflow.api.core.extraction.dto.ExtractedInvoiceData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ExtractionValidator {

    private static final BigDecimal CONFIDENCE_THRESHOLD = new BigDecimal("0.80");

    public ValidationResult validate(ExtractedInvoiceData data) {
        List<String> warnings = new ArrayList<>();

        if (data.confidence().compareTo(CONFIDENCE_THRESHOLD) < 0) {
            warnings.add("Düşük güven skoru: " + data.confidence());
        }

        BigDecimal sumOfItems = data.lineItems().stream()
                .map(ExtractedInvoiceData.LineItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal tax = data.taxAmount() != null ? data.taxAmount() : BigDecimal.ZERO;
        BigDecimal calculatedTotal = sumOfItems.add(tax);

        if (data.totalAmount().subtract(calculatedTotal).abs().compareTo(new BigDecimal("0.05")) > 0) {
            warnings.add(String.format("Matematiksel tutarsızlık! Okunan %s, Hesaplanan: %s", data.totalAmount(), calculatedTotal));
        }

        if (data.vendorName() == null || data.vendorName().isBlank()) {
            warnings.add("Satıcı adı bulunamadı.");
        }

        return new ValidationResult(warnings.isEmpty(), warnings);
    }

    public record ValidationResult(boolean isValid, List<String> warnings) {}
}
