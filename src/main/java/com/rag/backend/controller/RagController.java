package com.rag.backend.controller;

import com.rag.backend.service.DocumentIndexingService;
import com.rag.backend.service.RagService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagService ragService;
    private final DocumentIndexingService documentIndexingService;

    public RagController(RagService ragService,
                         DocumentIndexingService documentService) {
        this.ragService = ragService;
        this.documentIndexingService = documentService;
    }

    @GetMapping("/ask")
    public String ask(@RequestParam String question) {
        return ragService.ask(question);
    }

    @PostMapping("/documents")
    public String saveDocument(@RequestBody String content) {
        documentIndexingService.saveDocument(content);
        return "Документ успешно сохранён в Qdrant";
    }
}
