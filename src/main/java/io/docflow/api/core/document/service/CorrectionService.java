package io.docflow.api.core.document.service;

import io.docflow.api.core.extraction.dto.ExtractionCorrectionRequest;
import io.docflow.api.core.extraction.entity.ExtractedData;
import io.docflow.api.core.extraction.repository.ExtractedDataRepository;
import io.docflow.api.infrastructure.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CorrectionService {

    private final ExtractedDataRepository repository;

    /**
     * AI tarafından çıkartılan veriyi admin onayı/düzeltmesiyle günceller.
     * Optimistic Locking (@Version) sayesinde veri tutarlılığı garanti edilir.
     */
    @Transactional
    public void correctData(UUID dataId, ExtractionCorrectionRequest request) {
        log.info("Applying manual correction for ExtractedData ID: {}", dataId);

        ExtractedData data = repository.findById(dataId)
                .orElseThrow(() -> new ResourceNotFoundException("ExtractedData", "id", dataId));

        data.applyCorrection(request);

        repository.save(data);

        log.info("Successfully updated ExtractedData ID: {}. Version is now: {}", dataId, data.getVersion());
    }
}
