package com.einar115.thearchivist.controller;

import com.einar115.thearchivist.config.SecurityConfig;
import com.einar115.thearchivist.dto.request.ChatRequest;
import com.einar115.thearchivist.dto.response.ChatSourceResponse;
import com.einar115.thearchivist.service.ChatService;
import com.einar115.thearchivist.service.DocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasKey;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ChatController.class, properties = "app.cors.allowed-origins=http://localhost:4200")
@Import(SecurityConfig.class)
class ChatControllerTest {

    private static final String URL = "/api/v1/chat";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private DocumentService documentService;

    @Test
    void streamsTheSourcesEventFollowedByTheTokensAsTheFrontendParsesThem() throws Exception {
        when(chatService.ask(any(ChatRequest.class))).thenReturn(Flux.just(
                ServerSentEvent.<Object>builder(List.of(new ChatSourceResponse(1, "doc-a", "Crónicas de Hyrule", "The Legend of Zelda", null, "CANON")))
                        .event("sources").build(),
                ServerSentEvent.<Object>builder("Hyrule").build(),
                ServerSentEvent.<Object>builder(" cayó\n- en ruinas").build()));

        // Anonymous on purpose: the chat is open to everyone in SecurityConfig.
        MvcResult started = mockMvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"¿Qué le pasó a Hyrule?\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).isEqualTo(
                "event:sources\n"
                        + "data: [{\"number\":1,\"documentId\":\"doc-a\",\"title\":\"Crónicas de Hyrule\",\"universe\":\"The Legend of Zelda\",\"work\":null,\"canonicity\":\"CANON\"}]\n\n"
                        + "data: Hyrule\n\n"
                        // Spring 7.1 puts one space after "data:", then the token's own leading space follows; a line
                        // break becomes a second data line of the same event. The frontend parser drops only the first.
                        + "data:  cayó\ndata: - en ruinas\n\n");
    }

    @Test
    void passesTheFiltersInTheBodyToTheService() throws Exception {
        when(chatService.ask(any(ChatRequest.class))).thenReturn(Flux.empty());

        mockMvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"¿Quién es Ciri?\", \"universe\": \"The Witcher\", \"canonOnly\": true}"))
                .andExpect(request().asyncStarted());

        verify(chatService).ask(new ChatRequest("¿Quién es Ciri?", "The Witcher", true));
    }

    @Test
    void rejectsAUniverseLongerThan100Characters() throws Exception {
        mockMvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"¿?\", \"universe\": \"" + "a".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors", hasKey("universe")));

        verifyNoInteractions(chatService);
    }

    @Test
    void rejectsABlankQuestion() throws Exception {
        mockMvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors", hasKey("question")));

        verifyNoInteractions(chatService);
    }

    @Test
    void rejectsAQuestionLongerThan2000Characters() throws Exception {
        mockMvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"" + "a".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chatService);
    }

    @Test
    void listsTheApprovedUniversesWithoutLoggingIn() throws Exception {
        when(documentService.findApprovedUniverses()).thenReturn(List.of("Sabotage Studio Universe", "The Witcher"));

        mockMvc.perform(get(URL + "/universes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", contains("Sabotage Studio Universe", "The Witcher")));
    }
}
