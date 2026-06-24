package io.docflow.api.core.storage.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    /**
     * Gelen dosyayı sisteme kaydeder.
     * @param file Yüklenen dosya
     * @return Kaydedilen dosyanın fiziksel yolu (path)
    **/
    String store(MultipartFile file);
    byte[] fetch(String key);
}
