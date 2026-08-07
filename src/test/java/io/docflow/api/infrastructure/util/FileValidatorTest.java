package io.docflow.api.infrastructure.util;

import io.docflow.api.infrastructure.exception.InvalidRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FileValidatorTest {
    private final FileValidator fileValidator = new FileValidator();

    @Test
    @DisplayName("Başarı: Gerçek bir PDF dosyası doğrulama testinden geçmeli")
    void shouldAcceptRealPdf() {
        byte[] pdfContent = "%PDF-1.5\n%test content".getBytes();
        MockMultipartFile realPdf = new MockMultipartFile(
                "file", "invoice.pdf", "application/pdf", pdfContent
        );
        assertDoesNotThrow(() -> fileValidator.validate(realPdf));
    }
    @Test
    @DisplayName("Güvenlik: Uzantısı PDF olup içi TEXT olan dosya reddedilmeli (MIME Spoofing)")
    void shouldRejectFakePdf() {
        MockMultipartFile fakePdf = new MockMultipartFile(
                "file", "hacker.pdf", "application/pdf", "Normal bir yazı.".getBytes()
        );

        assertThrows(InvalidRequestException.class, () -> fileValidator.validate(fakePdf));
    }

    @Test
    @DisplayName("Limit: 10MB'dan büyük dosyalar reddedilmeli")
    void shouldRejectOversizedFile() {
        byte[] largeContent = new byte[11 * 1024 * 1024];
        MockMultipartFile heavyFile = new MockMultipartFile(
                "file", "heavy.pdf", "application/pdf", largeContent
        );

        assertThrows(InvalidRequestException.class, () -> fileValidator.validate(heavyFile));
    }
}
