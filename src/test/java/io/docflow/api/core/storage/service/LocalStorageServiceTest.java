package io.docflow.api.core.storage.service;

import io.docflow.api.infrastructure.exception.StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.springframework.mock.web.MockMultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

/**
 * Plain JUnit test against a real temp directory: no Spring context, no mocks needed,
 * because uploadDir is now constructor-injected instead of a @Value field.
 */
class LocalStorageServiceTest {

    @TempDir
    Path tempDir;

    private LocalStorageService service;

    @BeforeEach
    void setUp() {
        service = new LocalStorageService(tempDir.toString());
    }

    @Test
    void store_writesFileAndReturnsRetrievableKey() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "invoice.pdf", "application/pdf", "content".getBytes());

        String key = service.store(file);

        assertThat(key).endsWith("_invoice.pdf");
        assertThat(Files.exists(tempDir.resolve(key))).isTrue();
        assertThat(service.fetch(key)).isEqualTo("content".getBytes());
    }

    @Test
    void delete_removesStoredFile() {
        MockMultipartFile file = new MockMultipartFile("file", "invoice.pdf", "application/pdf", "content".getBytes());
        String key = service.store(file);

        service.delete(key);

        assertThat(Files.exists(tempDir.resolve(key))).isFalse();
    }

    @Test
    void fetch_missingKey_throwsStorageException() {
        assertThatThrownBy(() -> service.fetch("does-not-exist.pdf"))
                .isInstanceOf(StorageException.class);
    }

    @Test
    void cleanup_deletesOnlyFilesOlderThanRetention() throws IOException {
        Path oldFile = tempDir.resolve("old.pdf");
        Path recentFile = tempDir.resolve("recent.pdf");
        Files.writeString(oldFile, "old");
        Files.writeString(recentFile, "recent");

        Instant fortyDaysAgo = Instant.now().minus(40, ChronoUnit.DAYS);
        Files.setLastModifiedTime(oldFile, FileTime.from(fortyDaysAgo));

        int deletedCount = service.cleanup(30);

        assertThat(deletedCount).isEqualTo(1);
        assertThat(Files.exists(oldFile)).isFalse();
        assertThat(Files.exists(recentFile)).isTrue();
    }
}
