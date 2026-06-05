package com.rag.backend.controller;

import com.rag.backend.service.DocumentIndexingService;
import com.rag.backend.service.RagService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping("/ask")
    public String ask(@RequestParam MultipartFile file) {
        String audioContent = ragService.transcribeAudio(file);
        return ragService.askGlobally(audioContent);
    }

    @PostMapping("/transcribe")
    public String transcribe(@RequestParam MultipartFile file) {
        return ragService.transcribeAudio(file);
    }

    @GetMapping("/transcribe")
    public String transcribe(@RequestParam String fileUrl) {
        return ragService.transcribeAudioFromUrl(fileUrl);
    }

    @PostMapping("/documents")
    public String saveDocument(@RequestBody String content) {
        documentIndexingService.saveDocument(content);
        return "Документ успешно сохранён в Qdrant";
    }
}
