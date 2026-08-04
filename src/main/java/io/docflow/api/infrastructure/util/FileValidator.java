package io.docflow.api.infrastructure.util;

import io.docflow.api.infrastructure.exception.InvalidRequestException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

@Component
@Slf4j
public class FileValidator {
    private final Tika tika = new Tika();

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf",
            "image/png",
            "image/jpeg"
    );

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            log.warn("Validation failed: Uploaded file is null or empty");
            throw new InvalidRequestException("File cannot be empty.");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            log.warn("Validation failed: File size {} exceeds limit", file.getSize());
            throw new InvalidRequestException("File size exceeds 10MB limit");
        }

        try {
            String detectedType = tika.detect(file.getInputStream());

            log.debug("Apache Tika detected file type: {}", detectedType);

            if (!ALLOWED_TYPES.contains(detectedType)) {
                log.warn("UNSUPPORTED FILE TYPE ATTEMPT: Detected as {}", detectedType);
                throw new InvalidRequestException("Unsupported file format. Actual type is: " + detectedType);
            }
        } catch (IOException e) {
            log.error("File detection error: ", e);
            throw new InvalidRequestException("Could not verify file integrity");
        }
    }
}
