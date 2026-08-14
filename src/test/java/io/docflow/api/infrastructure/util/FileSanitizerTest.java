package io.docflow.api.infrastructure.util;

import io.docflow.api.infrastructure.exception.InvalidRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Security and utility tests for Filename Sanitization.
 * Checks for Path Traversal attack prevention, character normalization,
 * and length enforcement for different operating systems.
 */
public class FileSanitizerTest {

    @Test
    @DisplayName("Should successfully sanitize complex and non-ASCII filenames")
    void shouldSanitizeComplexFileName() {
        String input = "invoice_february_2026! @#$.png";
        String result = FileSanitizer.sanitize(input);
        assertEquals("invoice_february_2026_____.png", result);
    }

    @Test
    @DisplayName("Should prevent and throw exception on Path Traversal filenames")
    void shouldThrowExceptionOnPathTraversal() {
        assertThrows(InvalidRequestException.class, () ->
                FileSanitizer.sanitize("../../../etc/passwd"));
    }

    @Test
    @DisplayName("Should truncate extremely long filenames while preserving extension")
    void shouldTruncateLongFileNames() {
        String longName = "a".repeat(150) + ".pdf";
        String result = FileSanitizer.sanitize(longName);
        assertTrue(result.length() <= 100);
        assertTrue(result.endsWith(".pdf"));
    }
}
