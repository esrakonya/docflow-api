package io.docflow.api.core.storage.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

@Service
@Primary
@RequiredArgsConstructor
public class S3StorageService implements StorageService {

    private final S3Client s3Client;

    @Value("${storage.minio.bucket-name}")
    private String bucketName;

    @Override
    public String store(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        if (originalName != null && originalName.contains("..")) {
            throw new RuntimeException("Geçersiz dosya ismi!");
        }

        String fileName = UUID.randomUUID() + "_" + originalName;

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
}
