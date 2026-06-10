package com.einar115.thearchivist.service.impl;

import com.einar115.thearchivist.dto.ChatRequest;
import com.einar115.thearchivist.service.ChatService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class ChatServiceImpl implements ChatService {

    private static final SearchRequest SEARCH_REQUEST = SearchRequest.builder()
            .topK(5)
            .similarityThreshold(0.55)
            .build();

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public ChatServiceImpl(ChatClient chatClient, VectorStore vectorStore) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
    }

    @Override
    public Flux<String> ask(ChatRequest request) {
        var advisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SEARCH_REQUEST)
                .build();

        return chatClient.prompt()
                .user(request.question())
                .advisors(advisor)
                .stream()
                .content();
    }
}
