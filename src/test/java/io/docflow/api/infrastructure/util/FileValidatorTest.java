package io.docflow.api.infrastructure.util;

import io.docflow.api.infrastructure.exception.InvalidRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Security-focused tests for File Validation.
 * Validates file size limits and protects against MIME Spoofing by checking
 * actual file headers (magic bytes) instead of just extensions using Apache Tika.
 */
public class FileValidatorTest {
    private final FileValidator fileValidator = new FileValidator();
    @Test
    @DisplayName("Success: Valid PDF file should pass all validation checks")
    void shouldAcceptRealPdf() {
        byte[] pdfContent = "%PDF-1.5\n%test content".getBytes();
        MockMultipartFile realPdf = new MockMultipartFile(
                "file", "invoice.pdf", "application/pdf", pdfContent
        );
        assertDoesNotThrow(() -> fileValidator.validate(realPdf));
    }
    @Test
    @DisplayName("Security: Fake PDF (text content with .pdf extension) should be rejected (MIME Spoofing)")
    void shouldRejectFakePdf() {
        MockMultipartFile fakePdf = new MockMultipartFile(
                "file", "hacker.pdf", "application/pdf", "A normal sentence.".getBytes()
        );

        assertThrows(InvalidRequestException.class, () -> fileValidator.validate(fakePdf));
    }

    @Test
    @DisplayName("Limit: Files larger than 10MB maximum limit should be rejected")
    void shouldRejectOversizedFile() {
        byte[] largeContent = new byte[11 * 1024 * 1024];
        MockMultipartFile heavyFile = new MockMultipartFile(
                "file", "heavy.pdf", "application/pdf", largeContent
        );

        assertThrows(InvalidRequestException.class, () -> fileValidator.validate(heavyFile));
    }
}
