package com.einar115.thearchivist.controller;

import com.einar115.thearchivist.dto.request.ChatRequest;
import com.einar115.thearchivist.service.ChatService;
import com.einar115.thearchivist.service.DocumentService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;
    private final DocumentService documentService;

    public ChatController(ChatService chatService, DocumentService documentService) {
        this.chatService = chatService;
        this.documentService = documentService;
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> chat(@Valid @RequestBody ChatRequest request) {
        return chatService.ask(request);
    }

    @GetMapping("/universes")
    public List<String> universes() {
        return documentService.findApprovedUniverses();
    }

}
