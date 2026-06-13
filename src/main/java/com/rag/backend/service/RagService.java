package com.rag.backend.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.backend.dto.AskResponse;
import com.rag.backend.dto.AudioContent;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.imageio.ImageIO;

@Service
public class RagService {

    @Autowired
    private ObjectMapper objectMapper;

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

    public Flux<String> streamWithText(String question) {
        return chatClient.prompt()
                .user(question)
                .stream()
                .content();
    }

    public Flux<String> stream(String requestId) {

        SearchRequest request = SearchRequest.builder()
                .query("")
                .filterExpression(
                        "requestId == '" + requestId + "'"
                )
                .topK(1)
                .build();

        List<Document> docs =
                vectorStore.similaritySearch(request);
        return chatClient.prompt()
                .user(docs.getFirst().getText())
                .stream()
                .content();
    }

    public AudioContent transcribeAudioAndSaveToRag(MultipartFile file){
        String audioContent = transcribeAudio(file);

        String requestId = UUID.randomUUID().toString();

        vectorStore.add(List.of(
                new Document(
                        audioContent,
                        Map.of(
                                "requestId", requestId,
                                "type", "question"
                        )
                )
        ));
        return new AudioContent(requestId, audioContent);
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


    public String analyzeVideo(MultipartFile videoFile) throws Exception {

        List<ByteArrayResource> frames = new ArrayList<>();

        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoFile.getInputStream())) {

            grabber.start();

            Java2DFrameConverter converter = new Java2DFrameConverter();

            double fps = grabber.getFrameRate();

            Frame frame;

            while ((frame = grabber.grabImage()) != null) {

                int frameNumber = grabber.getFrameNumber();

                // 1 кадр каждые 2 секунды
                if (frameNumber % ((int) fps * 2) != 0) {
                    continue;
                }

                BufferedImage image = converter.convert(frame);

                if (image == null) {
                    continue;
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();

                ImageIO.write(image, "jpg", baos);

                frames.add(
                        new ByteArrayResource(baos.toByteArray()) {
                            @Override
                            public String getFilename() {
                                return "frame.jpg";
                            }
                        }
                );
            }

            grabber.stop();
        }

        String content = chatClient.prompt()
                .user(user -> {

                    user.text("""
                    Analyze these video frames.
                    Only possible languages are Uzbek, Russian and English.
    
                    Return:
                    - Events occurring in chronological order.
                    - Ignore UI elements and watermarks.
                    - Return only the events.
                    """);

                    for (ByteArrayResource frame : frames) {
                        System.out.println(frame);
                        user.media(MediaType.IMAGE_JPEG, frame);
                    }

                })
                .call()
                .content();
        System.out.println(content);
        return content;
    }

    public String transcribeMedia(MultipartFile file) throws FFmpegFrameGrabber.Exception {
        System.out.println(file.getOriginalFilename());
        System.out.println(file.getContentType());
        System.out.println(MediaType.parseMediaType(Objects.requireNonNull(file.getContentType())));
        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber((File) file);
        grabber.start();

        Frame frame = grabber.grabImage();

        BufferedImage image =
                new Java2DFrameConverter().convert(frame);
        String content = chatClient.prompt()
                .user(u -> u
                        .text("""
            Analyze this Media. Only possible languages are Uzbek, Russian and English.
            Return only the events depicted in the media.
            """)
                        .media(
                                MediaType.parseMediaType(Objects.requireNonNull(file.getContentType())),
                                file.getResource()
                        )
                )
                .call()
                .content();
        System.out.println(content);
        return content;
    }

    public AskResponse askGlobally(String question){
        String content = chatClient.prompt().system("""
                        Ты помощник, который отвечает на вопросы.
                        Ответ верни строго в json формате (question, answer, rating).Return only valid JSON.
                                                                             Escape all new lines using \\n.
                                                                             Do not use markdown.
                        """).user(question).call().content();
        return parseAskResponseJson(content);

    }

    public Mono<AskResponse> askGloballyStreamed(String question){
        StringBuilder buffer = new StringBuilder();

        return chatClient.prompt()
                .user(question)
                .stream()
                .content()
                .doOnNext(buffer::append)
                .then(Mono.fromSupplier(() -> {
                    AskResponse res = new AskResponse(
                            question, buffer.toString(), "test"
                    );
                    return res;
                }));
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

    private AskResponse parseAskResponseJson(String json) {
        // Удаляем возможные markdown-обёртки
        if (json == null) {
            throw new RuntimeException("Empty response from Gemini");
        }

        String clean = json
                .replaceAll("(?s)```json", "")
                .replaceAll("```", "")
                .trim();

        int start = clean.indexOf("{");
        int end = clean.lastIndexOf("}");

        if (start == -1 || end == -1) {
            throw new RuntimeException("Invalid JSON from model: " + clean);
        }

        String jsonOnly = clean.substring(start, end + 1);

        try {
            return objectMapper.readValue(jsonOnly, AskResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("JSON parse error: " + jsonOnly, e);
        }
    }

}
