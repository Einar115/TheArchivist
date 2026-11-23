package com.einar115.thearchivist.service.impl;

import com.einar115.thearchivist.dto.request.ChatRequest;
import com.einar115.thearchivist.rag.ChatPrompts;
import com.einar115.thearchivist.rag.RetrievedContext;
import com.einar115.thearchivist.service.ChatService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class ChatServiceImpl implements ChatService {

    private final ChatClient chatClient;
    private final RetrievalAugmentationAdvisor ragAdvisor;

    public ChatServiceImpl(ChatClient chatClient, RetrievalAugmentationAdvisor ragAdvisor) {
        this.chatClient = chatClient;
        this.ragAdvisor = ragAdvisor;
    }

    @Override
    public Flux<ServerSentEvent<Object>> ask(ChatRequest request) {
        return chatClient.prompt()
                .system(ChatPrompts.system())
                .user(request.question())
                .advisors(ragAdvisor)
                .stream()
                .chatClientResponse()   // not content(): we need the context, not only the text
                .index()
                .concatMap(indexed -> {
                    Flux<ServerSentEvent<Object>> token = Mono.justOrEmpty(textOf(indexed.getT2()))
                            .map(text -> ServerSentEvent.<Object>builder(text).build())
                            .flux();
                    // Every streamed response carries the request context; the first one is enough for the sources.
                    return indexed.getT1() == 0
                            ? Flux.concat(Mono.just(sourcesEvent(indexed.getT2())), token)
                            : token;
                });
    }

    private static ServerSentEvent<Object> sourcesEvent(ChatClientResponse response) {
        @SuppressWarnings("unchecked")
        List<Document> documents = (List<Document>) response.context()
                .getOrDefault(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT, List.of());
        return ServerSentEvent.<Object>builder(RetrievedContext.from(documents).sources()).event("sources").build();
    }

    private static String textOf(ChatClientResponse response) {
        ChatResponse chatResponse = response.chatResponse();
        if (chatResponse == null || chatResponse.getResult() == null) return null;
        return chatResponse.getResult().getOutput().getText();
    }
}
