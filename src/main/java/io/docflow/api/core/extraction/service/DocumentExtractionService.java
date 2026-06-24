package io.docflow.api.core.extraction.service;

import io.docflow.api.core.document.entity.Document;
import io.docflow.api.core.document.entity.DocumentStatus;
import io.docflow.api.core.document.repository.DocumentRepository;
import io.docflow.api.core.document.service.DocumentInternalService;
import io.docflow.api.core.extraction.dto.ExtractedInvoiceData;
import io.docflow.api.core.extraction.entity.DocumentLineItem;
import io.docflow.api.core.extraction.entity.ExtractedData;
import io.docflow.api.core.extraction.repository.ExtractedDataRepository;
import io.docflow.api.core.extraction.validator.ExtractionValidator;
import io.docflow.api.core.storage.service.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MimeType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class DocumentExtractionService {

    private final ChatClient chatClient;
    private final DocumentInternalService documentInternalService;
    private final ExtractedDataRepository extractedDataRepository;
    private final String promptTemplateText;
    private final BeanOutputConverter<ExtractedInvoiceData> outputConverter;
    private final ExtractionValidator extractionValidator;
    private final StorageService storageService;

    public DocumentExtractionService(ChatClient documentChatClient,
                                     DocumentInternalService documentInternalService,
                                     ExtractedDataRepository extractedDataRepository,
                                     ExtractionValidator extractionValidator,
                                     StorageService storageService) {
        this.chatClient = documentChatClient;
        this.documentInternalService = documentInternalService;
        this.extractedDataRepository = extractedDataRepository;
        this.promptTemplateText = loadPromptTemplate("classpath:prompts/invoice-extraction.st");
        this.outputConverter = new BeanOutputConverter<>(ExtractedInvoiceData.class);
        this.extractionValidator = extractionValidator;
        this.storageService = storageService;
    }

    @Transactional
    public ExtractedInvoiceData extractAndSave(UUID documentId, byte[] fileBytes, String mimeType) {
        documentInternalService.updateStatus(documentId, DocumentStatus.PROCESSING);

        Media media = new Media(MimeType.valueOf(mimeType), new ByteArrayResource(fileBytes));
        String formatInstructions = outputConverter.getFormat();

        PromptTemplate promptTemplate = PromptTemplate.builder()
                .template(promptTemplateText)
                .variables(Map.of("format", formatInstructions))
                .build();


        String rawResponse = chatClient.prompt()
                .user(spec -> spec.text(promptTemplate.render()).media(media))
                .call()
                .content();

        ExtractedInvoiceData dto = outputConverter.convert(rawResponse);

        saveToDatabase(documentId, dto, rawResponse);

        return dto;
    }

    private void saveToDatabase(UUID documentId, ExtractedInvoiceData dto, String rawJson) {
        Document doc = documentInternalService.getById(documentId);

        ExtractionValidator.ValidationResult validation = extractionValidator.validate(dto);

        extractedDataRepository.findByDocumentId(documentId)
                .ifPresent(oldData -> {
                    log.info("Eski çıkarım verisi temizleniyor: {}", documentId);
                    extractedDataRepository.delete(oldData);
                });

        ExtractedData entity = ExtractedData.builder()
                .document(doc)
                .vendorName(dto.vendorName())
                .invoiceNumber(dto.invoiceNumber())
                .invoiceDate(dto.invoiceDate())
                .dueDate(dto.dueDate())
                .totalAmount(dto.totalAmount())
                .currency(dto.currency())
                .overallConfidence(dto.confidence())
                .rawLlmResponse(rawJson)
                .validationWarnings(validation.warnings())
                .build();

        List<DocumentLineItem> lineItems = dto.lineItems().stream()
                .map(item -> DocumentLineItem.builder()
                        .extractedData(entity)
                        .description(item.description())
                        .quantity(item.quantity())
                        .unitPrice(item.unitPrice())
                        .lineTotal(item.lineTotal())
                        .build())
                .toList();

        entity.setLineItems(lineItems);
        extractedDataRepository.save(entity);

        if (validation.isValid()) {
            documentInternalService.markAsProcessed(documentId, OffsetDateTime.now());
            log.info("Belge başarıyla işlendi: {}", documentId);
        } else {
            documentInternalService.markAsNeedReview(documentId);
            log.warn("Belge inceleme gerektiriyor (NEEDS_REVIEW): {}", documentId);
        }

    }


    private String loadPromptTemplate(String location) {
        Resource resource = new PathMatchingResourcePatternResolver().getResource(location);
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Prompt template okunamadı: " + location, e);
        }
    }
}

