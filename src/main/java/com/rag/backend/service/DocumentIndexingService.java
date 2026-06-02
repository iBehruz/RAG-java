package com.rag.backend.service;

import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class DocumentIndexingService {

    private final VectorStore vectorStore;

    public DocumentIndexingService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void saveDocument(String content) {
        Document document = new Document(content);
        vectorStore.add(List.of(document));
    }

    public void saveDocument(String content, Map<String, Object> metadata) {
        Document document = new Document(content, metadata);
        vectorStore.add(List.of(document));
    }

    public void saveDocuments(List<String> contents) {
        List<Document> documents = contents.stream()
                .map(Document::new)
                .toList();

        vectorStore.add(documents);
    }
}