package com.rag.backend.service;

import java.util.List;
import java.util.Objects;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class RagService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public RagService(VectorStore vectorStore, ChatClient.Builder chatClientBuilder) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.build();
    }

    public String transcribeAudioFromUrl(String audioUrl) {
        byte[] bytes = new RestTemplate()
                .getForObject(audioUrl, byte[].class);

        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return "audio.wav";
            }
        };

        return chatClient.prompt()
                .user(u -> u
                        .text("""
            Transcribe this audio. Only possible languages are Uzbek, Russian and English.
            Return only the transcript.
            """)
                        .media(
                                MediaType.parseMediaType("audio/wav"),
                                resource
                        )
                )
                .call()
                .content();
    }

    public String transcribeAudio(MultipartFile file) {

        return chatClient.prompt()
              .user(u -> u
                      .text("""
            Transcribe this audio. Only possible languages are Uzbek, Russian and English.
            Return only the transcript.
            """)
                      .media(
                              MediaType.parseMediaType(Objects.requireNonNull(file.getContentType())),
                              file.getResource()
                      )
              )
              .call()
              .content();
    }

    public String askGlobally(String question){
        return chatClient.prompt().system("""
                        Ты помощник, который отвечает на вопросы.
                        Ответ верни строго в json формате (question, answer, rating).
                        """).user(question).call().content();
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
