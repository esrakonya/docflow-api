package io.docflow.api.core.document.controller;

import io.docflow.api.core.document.repository.DocumentRepository;
import io.docflow.api.core.extraction.repository.ExtractedDataRepository;
import lombok.RequiredArgsConstructor;
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
    public String showDashboard(Model model) {
        model.addAttribute("documents", documentRepository.findAll());
        return "dashboard";
    }

    @GetMapping("/document/{id}")
    public String showDocumentDetail(@PathVariable UUID id, Model model) {
        extractedDataRepository.findByDocumentId(id).ifPresentOrElse(
                data -> model.addAttribute("data", data),
                () -> model.addAttribute("error", "Bu belge için henüz AI verisi oluşmamış.")
        );
        return "document-detail";
    }
}
