package com.einar115.thearchivist.service;

import com.einar115.thearchivist.dto.request.ChatRequest;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

public interface ChatService {
    /** A "sources" event with the retrieved documents, then one unnamed event per answer token. */
    Flux<ServerSentEvent<Object>> ask(ChatRequest request);
}
