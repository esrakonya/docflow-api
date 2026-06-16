package io.docflow.api.core.document.repository;

import io.docflow.api.core.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID>{
}
