package io.docflow.api.core.storage.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileCleanupService {

    private final StorageService storageService;
    private final S3Client s3Client;

    @Value("${storage.minio.bucket-name}")
    private String bucketName;

    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupOlFiles() {
        log.info("MinIO temizlik işlemi başlatıldı...");

        ListObjectsV2Response result = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(bucketName)
                .build());

        Instant threshold = Instant.now().minus(30, ChronoUnit.DAYS);

        result.contents().stream()
                .filter(s3Object -> s3Object.lastModified().isBefore(threshold))
                .forEach(s3Object -> storageService.delete(s3Object.key()));
    }
}
