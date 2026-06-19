package io.docflow.api.core.storage.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@Slf4j
public class FileCleanupService {

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupOlFiles() {
        log.info("Eski dosyalar temizleniyor...");
        Path root = Paths.get(uploadDir);

        if (!Files.exists(root)) return;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            Instant threshold = Instant.now().minus(30, ChronoUnit.DAYS);

            for (Path file : stream) {
                if (Files.getLastModifiedTime(file).toInstant().isBefore(threshold)) {
                    Files.delete(file);
                    log.info("Silinen eski dosya: {}", file.getFileName());
                }
            }
        } catch (IOException e) {
            log.error("Dosya temizleme sırasında hata!", e);
        }
    }
}
