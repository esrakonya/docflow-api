package io.docflow.api.core.extraction.service;

import io.docflow.api.core.extraction.dto.ExtractedInvoiceData;
import io.docflow.api.core.extraction.validator.ExtractionValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the AI Data Validation logic.
 * Ensures the consistency of LLM-extracted invoice data, checking for
 * mathematical accuracy (sum of items vs total) and confidence score thresholds.
 */
public class ExtractionValidatorTest {
    private final ExtractionValidator validator = new ExtractionValidator();

    @Test
    @DisplayName("Should return valid when all extracted data is consistent")
    void shouldReturnValidWhenEverythingMatches() {
        var data = new ExtractedInvoiceData(
                "Global Office Supplies",
                "INV-001",
                LocalDate.now(),
                LocalDate.now(),
                "USD",
                new BigDecimal("100.00"),
                new BigDecimal("18.00"),
                List.of(new ExtractedInvoiceData.LineItem("Office Pen", BigDecimal.ONE, new BigDecimal("82.00"), new BigDecimal("82.00"))),
                new BigDecimal("0.95")
        );

        var result = validator.validate(data);
        assertTrue(result.isValid(), "Validation should pass for consistent invoice data.");
    }

    @Test
    @DisplayName("Should return warning when confidence score is below threshold")
    void shouldReturnWarningWhenConfidenceIsLow() {
        var data = new ExtractedInvoiceData(
                "Global Office Supplies",
                "INV-001",
                LocalDate.now(),
                LocalDate.now(),
                "USD",
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                List.of(),
                new BigDecimal("0.50")
        );

        var result = validator.validate(data);
        assertFalse(result.isValid());
        assertTrue(result.warnings().get(0).contains("Low confidence score"));
    }

    @Test
    @DisplayName("Should return warning on mathematical inconsistency between total and items")
    void shouldReturnWarningWhenMathMismatch() {
        var data = new ExtractedInvoiceData(
                "Market",
                "123",
                LocalDate.now(),
                LocalDate.now(),
                "USD",
                new BigDecimal("150.00"),
                BigDecimal.ZERO,
                List.of(new ExtractedInvoiceData.LineItem(
                        "Süt",
                        BigDecimal.ONE,
                        new BigDecimal("50.00"),
                        new BigDecimal("50.00")
                )),
                new BigDecimal("0.99")
        );

        var result = validator.validate(data);
        assertFalse(result.isValid());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("Mathematical inconsistency!")));
    }
}
