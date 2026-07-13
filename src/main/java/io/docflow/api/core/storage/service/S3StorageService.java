package io.docflow.api.core.storage.service;

import io.docflow.api.infrastructure.util.FileSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "storage.type", havingValue = "minio")
public class S3StorageService implements StorageService {

    private final S3Client s3Client;

    @Value("${storage.minio.bucket-name}")
    private String bucketName;

    @Override
    public String store(MultipartFile file) {
        String sanitizedOriginalName = FileSanitizer.sanitize(file.getOriginalFilename());

        String fileName = UUID.randomUUID() + "_" + sanitizedOriginalName;
        if (fileName != null && fileName.contains("..")) {
            throw new RuntimeException("Geçersiz dosya ismi!");
        }

        try {
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .contentType(file.getContentType())
                    .build(),
                    RequestBody.fromBytes(file.getBytes()));

            return fileName;
        } catch (Exception e) {
            throw new RuntimeException("MinIO/S3 yükleme hatası!", e);
        }
    }

    @Override
    public byte[] fetch(String key) {
        try {
            return s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build()).asByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Dosya MinIO'dan çekilemedi!", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build());
            log.info("Dosya MinIO'dan başarıyla silindi: {}", key);
        } catch (Exception e) {
            log.error("MinIO dosya silme hatası: {}", key, e);
        }
    }

    @Override
    public void cleanup(int days) {
        Instant threshold = Instant.now().minus(days, ChronoUnit.DAYS);
        log.info("Starting MinIO storage cleanup for files order than {} days (Threshold: {})", days, threshold);

        try {
            ListObjectsV2Response listResponse = s3Client.listObjectsV2(r -> r.bucket(bucketName));

            int deletedCount = 0;
            for (S3Object s3Object : listResponse.contents()) {
                if (s3Object.lastModified().isBefore(threshold)) {
                    s3Client.deleteObject(r -> r.bucket(bucketName).key(s3Object.key()));
                    deletedCount++;
                    log.debug("Deleted old MinIO object: {}", s3Object.key());
                }
            }
            log.info("MinIO cleanup finished. Total deleted objects: {}", deletedCount);
        } catch (Exception e) {
            log.error("Error occurred during MinIO cleanup", e);
        }
    }
}
