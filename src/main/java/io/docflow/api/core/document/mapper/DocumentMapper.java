package io.docflow.api.core.document.mapper;

import io.docflow.api.core.client.dto.ClientRegistrationResponse;
import io.docflow.api.core.client.entity.ApiClient;
import io.docflow.api.core.document.controller.DocumentController;
import io.docflow.api.core.document.entity.Document;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DocumentMapper {

    @Mapping(target = "id", source = "id")
    @Mapping(target = "status", expression = "java(document.getStatus().name())")
    DocumentController.DocumentResponse toResponse(Document document);

    @Mapping(target = "clientId", source = "client.id")
    @Mapping(target = "companyName", source = "client.companyName")
    @Mapping(target = "rawApiKey", source = "rawKey")
    ClientRegistrationResponse toRegistrationResponse(ApiClient client, String rawKey);
}
