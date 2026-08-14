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
        log.info("Starting scheduled storage cleanup task. Retention period: 30 days");

        try {
            int deletedFilesCount = storageService.cleanup(30);
            log.info("Scheduled cleanup completed. Removed {} files/objects.", deletedFilesCount);
        } catch (Exception e) {
            log.error("Critical error during scheduled cleanup!", e);
        }
    }
}
