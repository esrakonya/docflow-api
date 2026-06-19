package io.docflow.api.core.extraction.service;

import io.docflow.api.core.extraction.dto.ExtractedInvoiceData;
import io.docflow.api.core.extraction.validator.ExtractionValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ExtractionValidatorTest {

    private final ExtractionValidator validator = new ExtractionValidator();

    @Test
    @DisplayName("Tüm veriler tutarlı olduğunda geçerli sayılmalı")
    void shouldReturnValidWhenEverythingMatches() {
        var data = new ExtractedInvoiceData(
                "Aydın Ofis",
                "INV-001",
                LocalDate.now(),
                LocalDate.now(),
                "TRY",
                new BigDecimal("100.00"),
                new BigDecimal("18.00"),
                List.of(new ExtractedInvoiceData.LineItem("Kalem", BigDecimal.ONE, new BigDecimal("82.00"), new BigDecimal("82.00"))),
                new BigDecimal("0.95")
        );

        var result = validator.validate(data);
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("Güven skoru eşik değerinin altındaysa uyarı vermeli")
    void shouldReturnWarningWhenConfidenceIsLow() {
        var data = new ExtractedInvoiceData(
                "Aydın Ofis",
                "INV-001",
                LocalDate.now(),
                LocalDate.now(),
                "TRY",
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                List.of(),
                new BigDecimal("0.50")
        );

        var result = validator.validate(data);
        assertFalse(result.isValid());
        assertTrue(result.warnings().get(0).contains("Düşük güven skoru"));
    }

    @Test
    @DisplayName("Matematiksel tutarsızlık olduğunda uyarı vermeli")
    void shouldReturnWarningWhenMathMismatch() {
        var data = new ExtractedInvoiceData(
                "Market",
                "123",
                LocalDate.now(),
                LocalDate.now(),
                "TRY",
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
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("Matematiksel tutarsızlık!")));
    }
}
