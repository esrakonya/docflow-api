package io.docflow.api.core.storage.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    String store(MultipartFile file);
    byte[] fetch(String key);

    void delete(String key);

    int cleanup(int days);
}
