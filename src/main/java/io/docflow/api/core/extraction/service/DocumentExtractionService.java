package io.docflow.api.core.extraction.service;

import io.docflow.api.core.document.entity.Document;
import io.docflow.api.core.document.entity.DocumentStatus;
import io.docflow.api.core.document.service.DocumentService;
import io.docflow.api.core.extraction.dto.ExtractedInvoiceData;
import io.docflow.api.core.extraction.entity.ExtractedData;
import io.docflow.api.core.extraction.mapper.ExtractionMapper;
import io.docflow.api.core.extraction.repository.ExtractedDataRepository;
import io.docflow.api.core.extraction.validator.ExtractionValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MimeType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentExtractionService {

    private final ChatClient chatClient;
    private final DocumentService documentService;
    private final ExtractedDataRepository extractedDataRepository;
    private final ExtractionValidator extractionValidator;
    private final ExtractionMapper extractionMapper;

    @Value("classpath:prompts/invoice-extraction.st")
    private Resource promptTemplateResource;

    private final BeanOutputConverter<ExtractedInvoiceData> outputConverter =
            new BeanOutputConverter<>(ExtractedInvoiceData.class);

    @Transactional
    public ExtractedInvoiceData extractAndSave(UUID documentId, byte[] fileBytes, String mimeType) {
        documentService.updateStatus(documentId, DocumentStatus.PROCESSING);

        try {
            Media media = new Media(MimeType.valueOf(mimeType), new ByteArrayResource(fileBytes));

            String promptText = promptTemplateResource.getContentAsString(StandardCharsets.UTF_8);

            PromptTemplate promptTemplate = PromptTemplate.builder()
                    .template(promptText)
                    .variables(Map.of("format", outputConverter.getFormat()))
                    .build();

            String rawResponse = chatClient.prompt()
                    .user(spec -> spec.text(promptTemplate.render()).media(media))
                    .call()
                    .content();

            ExtractedInvoiceData dto = outputConverter.convert(rawResponse);
            saveToDatabase(documentId, dto, rawResponse);
            return dto;
        } catch (IOException e) {
            throw new RuntimeException("Could not read prompt template file!", e);
        }
    }

    private void saveToDatabase(UUID documentId, ExtractedInvoiceData dto, String rawJson) {

        extractedDataRepository.findByDocumentId(documentId)
                .ifPresent(oldData -> {
                    log.info("Clearing old extraction data for document: {}", documentId);
                    extractedDataRepository.delete(oldData);
                    extractedDataRepository.flush();
                });

        ExtractionValidator.ValidationResult validation = extractionValidator.validate(dto);

        ExtractedData entity = extractionMapper.toEntity(dto);

        Document doc = documentService.getById(documentId);
        entity.setDocument(doc);
        entity.setRawLlmResponse(rawJson);
        entity.setValidationWarnings(validation.warnings());

        if (entity.getLineItems() != null) {
            entity.getLineItems().forEach(item -> item.setExtractedData(entity));
        }

        extractedDataRepository.save(entity);

        if (validation.isValid()) {
            documentService.markAsProcessed(documentId, OffsetDateTime.now());
            log.info("Document processed successfully: {}", documentId);
        } else {
            documentService.markAsNeedReview(documentId);
            log.warn("Document requires manual review (NEEDS_REVIEW): {}", documentId);
        }

    }
}

