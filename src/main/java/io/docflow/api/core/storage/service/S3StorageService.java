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

        if (fileName.contains("..")) {
            throw new RuntimeException("Invalid file name detected!");
        }

        try {
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .contentType(file.getContentType())
                    .build(),
                    RequestBody.fromBytes(file.getBytes()));

            log.info("File uploaded to S3/MinIO: {}", fileName);
            return fileName;
        } catch (Exception e) {
            log.error("S3/MinIO upload error for file: {}", fileName, e);
            throw new RuntimeException("Could not upload file to S3/MinIO!", e);
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
            log.error("Could not fetch file from S3/MinIO: {}", key);
            throw new RuntimeException("File could not be fetched from storage!", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build());
            log.info("File deleted successfully from S3/MinIO: {}", key);
        } catch (Exception e) {
            log.error("Error deleting file from S3/MinIO: {}", key, e);
        }
    }

    @Override
    public int cleanup(int days) {
        Instant threshold = Instant.now().minus(days, ChronoUnit.DAYS);
        int deletedCount = 0;

        try {
            ListObjectsV2Response listResponse = s3Client.listObjectsV2(r -> r.bucket(bucketName));

            for (S3Object s3Object : listResponse.contents()) {
                if (s3Object.lastModified().isBefore(threshold)) {
                    s3Client.deleteObject(r -> r.bucket(bucketName).key(s3Object.key()));
                    deletedCount++;
                    log.debug("Deleted old S3 object: {}", s3Object.key());
                }
            }
            log.info("S3/MinIO cleanup finished. Total deleted objects: {}", deletedCount);
        } catch (Exception e) {
            log.error("Error occurred during MinIO cleanup", e);
        }
        return deletedCount;
    }
}
