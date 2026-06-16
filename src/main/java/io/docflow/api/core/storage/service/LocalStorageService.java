package io.docflow.api.core.storage.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.support.SimpleTriggerContext;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@Slf4j
public class LocalStorageService implements StorageService {

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    @Override
    public String store(MultipartFile file) {
        try {
            Path root = Paths.get(uploadDir);
            if (!Files.exists(root)) {
                Files.createDirectories(root);
                log.info("Yükleme klasörü oluşturuldu: {}", root.toAbsolutePath());
            }

            String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
            Path destination = root.resolve(fileName);

            Files.copy(file.getInputStream(), destination);

            log.info("Dosya başarıyla kaydedildi: {}", destination);

            return destination.toString();
        } catch (IOException e) {
            log.error("Dosya depolama hatası!", e);
            throw new RuntimeException("Dosya kaydedilemedi: " + e.getMessage());
        }
    }
}
