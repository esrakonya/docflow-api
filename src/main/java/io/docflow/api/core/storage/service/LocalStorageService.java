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
            log.error("Security Risk: '..' detected in filename! -> {}", fileName);
            throw new RuntimeException("Invalid file name! Path traversal attempt blocked.");
        }

        try {
            Path root = Paths.get(uploadDir);
            if (!Files.exists(root)) {
                Files.createDirectories(root);
                log.info("Upload directory created at: {}", root.toAbsolutePath());
            }

            Path destination = root.resolve(fileName);
            Files.copy(file.getInputStream(), destination);

            log.info("File stored successfully at: {}", destination);
            return fileName;

        } catch (IOException e) {
            log.error("File storage error!", e);
            throw new RuntimeException("Could not store file: " + e.getMessage());
        }
    }

    @Override
    public byte[] fetch(String key) {
        try {
            return Files.readAllBytes(Paths.get(uploadDir).resolve(key));
        } catch (IOException e) {
            log.error("Could not read file from local storage: {}", key);
            throw new RuntimeException("File could not be read from local storage!");
        }
    }

    @Override
    public void delete(String key) {
        try {
            Path filePath = Paths.get(uploadDir).resolve(key);

            boolean deleted = Files.deleteIfExists(filePath);

            if (deleted) {
                log.info("File deleted successfully from local storage: {}", key);
            } else {
                log.warn("File to delete not found in local storage: {}", key);
            }
        } catch (IOException e) {
            log.error("Error occurred while deleting file from local storage: {}", key, e);
        }
    }

    @Override
    public int cleanup(int days) {
       Path root = Paths.get(uploadDir);
       if (!Files.exists(root)) return 0;

       Instant threshold = Instant.now().minus(days, ChronoUnit.DAYS);
       int deletedCount = 0;

       try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
           for (Path file : stream) {
               Instant lastModified = Files.getLastModifiedTime(file).toInstant();
               if (lastModified.isBefore(threshold)) {
                   Files.delete(file);
                   deletedCount++;
                   log.debug("Deleted old local file: {}", file.getFileName());
               }
           }
           log.info("Local storage cleanup finished. Deleted {} files.", deletedCount);
       } catch (IOException e) {
           log.error("Error occurred during local file cleanup", e);
       }
       return deletedCount;
    }

}
