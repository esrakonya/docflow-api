package io.docflow.api.core.extraction.mapper;

import io.docflow.api.core.extraction.dto.ExtractedInvoiceData;
import io.docflow.api.core.extraction.entity.DocumentLineItem;
import io.docflow.api.core.extraction.entity.ExtractedData;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ExtractionMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "document", ignore = true)
    @Mapping(target = "overallConfidence", source = "confidence")
    @Mapping(target = "rawLlmResponse", ignore = true)
    @Mapping(target = "validationWarnings", ignore = true)
    ExtractedData toEntity(ExtractedInvoiceData dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "extractedData", ignore = true)
    DocumentLineItem toEntity(ExtractedInvoiceData.LineItem dto);
}
