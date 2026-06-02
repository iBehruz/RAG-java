package com.rag.backend.service;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class RagService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public RagService(VectorStore vectorStore, ChatClient.Builder chatClientBuilder) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.build();
    }

    public String ask(String question) {
        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(4)
                        .build()
        );

        String context = documents.stream()
                .map(Document::getText)
                .reduce("", (a, b) -> a + "\n\n" + b);
        System.out.println(context);
        return chatClient.prompt()
                .system("""
                        Ты помощник, который отвечает только на основе переданного контекста.
                        Если в контексте нет ответа, честно скажи об этом.
                        """)
                .user("""
                        Контекст:
                        %s

                        Вопрос:
                        %s
                        """.formatted(context, question))
                .call()
                .content();
    }
}
