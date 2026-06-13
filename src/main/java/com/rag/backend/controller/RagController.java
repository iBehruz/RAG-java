package com.rag.backend.controller;
import com.rag.backend.dto.AskResponse;
import com.rag.backend.dto.AudioContent;
import com.rag.backend.service.DocumentIndexingService;
import com.rag.backend.service.RagService;
import org.springframework.ai.document.Document;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService,
                         DocumentIndexingService documentService) {
        this.ragService = ragService;
    }

    @GetMapping(value = "/chat/stream/{requestId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamQ(
            @PathVariable String requestId) {
        return ragService.stream(requestId)
                .map(chunk ->
                        ServerSentEvent.<String>builder()
                                .data(chunk)
                                .build()
                );
    }

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamWithText(
            @RequestParam String question) {
        return ragService.streamWithText(question)
                .map(chunk ->
                        ServerSentEvent.<String>builder()
                                .data(chunk)
                                .build()
                );
    }

    @PostMapping("/stt")
    public AudioContent stt(@RequestParam MultipartFile file) {
        return ragService.transcribeAudioAndSaveToRag(file);
    }

}
