package io.docflow.api.core.document.controller;

import io.docflow.api.core.document.entity.Document;
import io.docflow.api.core.document.repository.DocumentRepository;
import io.docflow.api.core.extraction.repository.ExtractedDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@Controller
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class AdminViewController {

    private final DocumentRepository documentRepository;
    private final ExtractedDataRepository extractedDataRepository;

    @GetMapping
    public String showDashboard(
            Model model,
            @PageableDefault(size = 10, sort = "uploadedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<Document> documentPage = documentRepository.findAll(pageable);

        model.addAttribute("documents", documentPage.getContent());
        model.addAttribute("currentPage", documentPage.getNumber());
        model.addAttribute("totalPages", documentPage.getTotalPages());
        model.addAttribute("totalItems", documentPage.getTotalElements());

        return "dashboard";
    }

    @GetMapping("/document/{id}")
    public String showDocumentDetail(@PathVariable UUID id, Model model) {
        extractedDataRepository.findByDocumentIdWithItems(id).ifPresentOrElse(
                data -> model.addAttribute("data", data),
                () -> model.addAttribute("error", "AI data not available for this document.")
        );
        return "document-detail";
    }
}
