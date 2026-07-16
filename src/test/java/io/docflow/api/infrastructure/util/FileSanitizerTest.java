package io.docflow.api.infrastructure.util;

import io.docflow.api.infrastructure.exception.InvalidRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FileSanitizerTest {

    @Test
    @DisplayName("Karmaşık dosya isimlerini başarıyla temizlemeli")
    void shouldSanitizeComplexFileName() {
        String input = "fatura_şubat_2026! @#$.png";
        String result = FileSanitizer.sanitize(input);
        assertEquals("fatura_subat_2026_____.png", result);
    }

    @Test
    @DisplayName("Path traversal girişimlerini engellemeli")
    void shouldThrowExceptionOnPathTraversal() {
        assertThrows(InvalidRequestException.class, () ->
                FileSanitizer.sanitize("../../../etc/passwd"));
    }

    @Test
    @DisplayName("Çok uzun dosya isimlerini sondan kırpmalı")
    void shouldTruncateLongFileNames() {
        String longName = "a".repeat(150) + ".pdf";
        String result = FileSanitizer.sanitize(longName);
        assertTrue(result.length() <= 100);
        assertTrue(result.endsWith(".pdf"));
    }
}
