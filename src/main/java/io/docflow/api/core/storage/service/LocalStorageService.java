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
        String originalName =file.getOriginalFilename();
        if (originalName != null && originalName.contains("..")) {
            log.error("Güvenlik Riski: Dosya isminde '..' tespit edildi! -> {}", originalName);
            throw new RuntimeException("Geçersiz dosya ismi! Path traversal girişimi engellendi.");
        }

        try {
            Path root = Paths.get(uploadDir);
            if (!Files.exists(root)) {
                Files.createDirectories(root);
                log.info("Yükleme klasörü oluşturuldu: {}", root.toAbsolutePath());
            }

            String fileName = UUID.randomUUID() + "_" + originalName;
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
}
