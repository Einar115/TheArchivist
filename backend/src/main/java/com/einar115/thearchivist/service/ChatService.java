package com.einar115.thearchivist.service;

import com.einar115.thearchivist.dto.ChatRequest;
import reactor.core.publisher.Flux;

public interface ChatService {

    Flux<String> ask(ChatRequest request);
}
