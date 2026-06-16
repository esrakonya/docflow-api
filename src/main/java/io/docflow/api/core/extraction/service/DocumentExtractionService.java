package io.docflow.api.core.extraction.service;

import io.docflow.api.core.document.entity.Document;
import io.docflow.api.core.document.entity.DocumentStatus;
import io.docflow.api.core.document.repository.DocumentRepository;
import io.docflow.api.core.extraction.dto.ExtractedInvoiceData;
import io.docflow.api.core.extraction.entity.DocumentLineItem;
import io.docflow.api.core.extraction.entity.ExtractedData;
import io.docflow.api.core.extraction.repository.ExtractedDataRepository;
import io.docflow.api.core.extraction.validator.ExtractionValidator;
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
    private final DocumentRepository documentRepository;
    private final ExtractedDataRepository extractedDataRepository;
    private final String promptTemplateText;
    private final BeanOutputConverter<ExtractedInvoiceData> outputConverter;
    private final ExtractionValidator extractionValidator;

    public DocumentExtractionService(ChatClient documentChatClient,
                                     DocumentRepository documentRepository,
                                     ExtractedDataRepository extractedDataRepository,
                                     ExtractionValidator extractionValidator) {
        this.chatClient = documentChatClient;
        this.documentRepository = documentRepository;
        this.extractedDataRepository = extractedDataRepository;
        this.promptTemplateText = loadPromptTemplate("classpath:prompts/invoice-extraction.st");
        this.outputConverter = new BeanOutputConverter<>(ExtractedInvoiceData.class);
        this.extractionValidator = extractionValidator;
    }

    @Transactional
    public ExtractedInvoiceData extractAndSave(UUID documentId, byte[] fileBytes, String mimeType) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        doc.setStatus(DocumentStatus.PROCESSING);
        documentRepository.save(doc);

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

        saveToDatabase(doc, dto, rawResponse);

        return dto;
    }

    private void saveToDatabase(Document doc, ExtractedInvoiceData dto, String rawJson) {
        ExtractionValidator.ValidationResult validation = extractionValidator.validate(dto);

        ExtractedData entity = ExtractedData.builder()
                .document(doc)
                .vendorName(dto.vendorName())
                .invoiceNumber(dto.invoiceNumber())
                .invoiceDate(dto.invoiceDate())
                .totalAmount(dto.totalAmount())
                .currency(dto.currency())
                .overallConfidence(dto.confidence())
                .rawLlmResponse(rawJson)
                .validationWarnings(validation.warnings())
                .build();

        List<DocumentLineItem> lineItems =dto.lineItems().stream()
                .map(item -> DocumentLineItem.builder()
                        .extractedData(entity)
                        .description(item.description())
                        .quantity(item.quantity())
                        .unitPrice(item.unitPrice())
                        .lineTotal(item.lineTotal())
                        .build())
                .toList();

        entity.setLineItems(lineItems);

        if (validation.isValid()) {
            doc.setStatus(DocumentStatus.PROCESSED);
            log.info("Belge başarıyla doğrulandı: {}", doc.getId());
        } else {
            doc.setStatus(DocumentStatus.NEEDS_REVIEW);
            log.warn("Belge doğrulamadan geçemedi! Uyarılar: {}", validation.warnings());
        }

        doc.setProcessedAt(OffsetDateTime.now());
        extractedDataRepository.save(entity);
        documentRepository.save(doc);
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

