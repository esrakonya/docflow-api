package io.docflow.api.core.storage.service;

import io.docflow.api.infrastructure.util.FileSanitizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.support.SimpleTriggerContext;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@Slf4j
@ConditionalOnProperty(name = "storage.type", havingValue = "local")
public class LocalStorageService implements StorageService {

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    @Override
    public String store(MultipartFile file) {
        String sanitizedOriginalName = FileSanitizer.sanitize(file.getOriginalFilename());
        String fileName = UUID.randomUUID() + "_" + sanitizedOriginalName;

        if (fileName!= null && fileName.contains("..")) {
            log.error("Güvenlik Riski: Dosya isminde '..' tespit edildi! -> {}", fileName);
            throw new RuntimeException("Geçersiz dosya ismi! Path traversal girişimi engellendi.");
        }

        try {
            Path root = Paths.get(uploadDir);
            if (!Files.exists(root)) {
                Files.createDirectories(root);
                log.info("Yükleme klasörü oluşturuldu: {}", root.toAbsolutePath());
            }

            Path destination = root.resolve(fileName);

            Files.copy(file.getInputStream(), destination);

            log.info("Dosya başarıyla kaydedildi: {}", destination);
            return destination.toString();

        } catch (IOException e) {
            log.error("Dosya depolama hatası!", e);
            throw new RuntimeException("Dosya kaydedilemedi: " + e.getMessage());
        }
    }

    @Override
    public byte[] fetch(String key) {
        try {
            return Files.readAllBytes(Paths.get(uploadDir).resolve(key));
        } catch (IOException e) {
            throw new RuntimeException("Dosya yerel diskten okunamadı!");
        }
    }

    @Override
    public void delete(String key) {
        try {
            Path filePath = Paths.get(uploadDir).resolve(key);

            boolean deleted = Files.deleteIfExists(filePath);

            if (deleted) {
                log.info("Dosya yerel diskten başarıyla silindi: {}", key);
            } else {
                log.warn("Silinmek istenen dosya yerel diskte bulunamadı: {}", key);
            }
        } catch (IOException e) {
            log.error("Dosya yerel diskten silinirken hata oluştu: {}", key, e);
        }
    }

    @Override
    public void cleanup(int days) {
       Path root = Paths.get(uploadDir);
       if (!Files.exists(root)) return;

       Instant threshold = Instant.now().minus(days, ChronoUnit.DAYS);
       log.info("Starting local storage cleanup for files older than {} days (Threshold: {})", days, threshold);

       try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
           int deletedCount = 0;
           for (Path file : stream) {
               Instant lastModified = Files.getLastModifiedTime(file).toInstant();

               if (lastModified.isBefore(threshold)) {
                   Files.delete(file);
                   deletedCount++;
                   log.debug("Deleted old local file: {}", file.getFileName());
               }
           }
           log.info("Local cleanup finished. Total deleted files: {}", deletedCount);
       } catch (IOException e) {
           log.error("Error occurred during local file cleanup", e);
       }
    }

}
