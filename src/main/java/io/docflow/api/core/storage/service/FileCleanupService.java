package io.docflow.api.core.storage.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
@Slf4j
public class FileCleanupService {

    private final StorageService storageService;

    @Scheduled(cron = "0 0 3 * * ?")
    public void runScheduledCleanup() {
        log.info("Scheduled cleanup task started...");
        storageService.cleanup(30);
    }
}
