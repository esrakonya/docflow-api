package io.docflow.api.web.admin;

import io.docflow.api.core.document.service.CorrectionService;
import io.docflow.api.core.extraction.dto.ExtractionCorrectionRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/extractions")
@RequiredArgsConstructor
public class ExtractionCorrectionController {

    private final CorrectionService correctionService;

    /**
     * AI tarafından çıkartılan verileri manuel olarak düzeltir ve onaylar.
     * Sadece ADMIN yetkisine sahip kullanıcılar erişebilir.
     */
    @PutMapping("/{id}/correct")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> correctExtraction(
            @PathVariable UUID id,
            @Valid @RequestBody ExtractionCorrectionRequest request
    ) {
        log.info("Admin correction request received for Extraction ID: {}", id);

        correctionService.correctData(id, request);

        log.info("Extraction ID: {} successfully corrected by admin.", id);

        return ResponseEntity.noContent().build();
    }
}
