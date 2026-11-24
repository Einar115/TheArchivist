package com.einar115.thearchivist.service.impl;

import com.einar115.thearchivist.dto.request.ChatRequest;
import com.einar115.thearchivist.dto.response.ChatSourceResponse;
import com.einar115.thearchivist.rag.ChatPrompts;
import com.einar115.thearchivist.rag.RagConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ChatModel chatModel;

    @Captor
    private ArgumentCaptor<SearchRequest> searchCaptor;

    @Captor
    private ArgumentCaptor<Prompt> promptCaptor;

    private ChatServiceImpl chatService;

    @BeforeEach
    void setUp() {
        // ChatClient merges its request options into the model defaults, so the mock has to provide some.
        lenient().when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        // A real ChatClient over a mocked model and the advisor exactly as RagConfig builds it, over a mocked store:
        // the test exercises the real retrieval and augmentation and sees the prompt as it would be sent.
        var ragAdvisor = new RagConfig().ragAdvisor(vectorStore, 5, 0.40);
        chatService = new ChatServiceImpl(ChatClient.builder(chatModel).build(), ragAdvisor);
    }

    @Test
    void sendsTheSourcesFirstAndThenTheAnswerTokens() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                chunk("doc-a", "Hyrule fell.", "Crónicas de Hyrule", "CANON")));
        streamAnswer("Hyrule", " cayó", " [1].");

        List<ServerSentEvent<Object>> events = ask("¿Qué le pasó a Hyrule?");

        assertThat(events.getFirst().event()).isEqualTo("sources");
        assertThat(events.getFirst().data()).asInstanceOf(LIST)
                .containsExactly(new ChatSourceResponse(1, "doc-a", "Crónicas de Hyrule", "The Legend of Zelda", null, "CANON"));
        List<ServerSentEvent<Object>> tokens = events.subList(1, events.size());
        assertThat(tokens).extracting(ServerSentEvent::event).containsOnlyNulls();
        assertThat(tokens).extracting(ServerSentEvent::data).containsExactly("Hyrule", " cayó", " [1].");
    }

    @Test
    void searchesWithTheQuestionAndTheConfiguredLimits() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        streamAnswer("No lo sé.");

        ask("¿Quién es Ciri?");

        verify(vectorStore).similaritySearch(searchCaptor.capture());
        SearchRequest search = searchCaptor.getValue();
        assertThat(search.getQuery()).isEqualTo("¿Quién es Ciri?");
        // The values RagConfig gives VectorStoreDocumentRetriever.
        assertThat(search.getTopK()).isEqualTo(5);
        assertThat(search.getSimilarityThreshold()).isEqualTo(0.40);
    }

    @Test
    void putsTheRulesInTheSystemMessageAndTheLabelledSourcesInTheUserMessage() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                chunk("doc-a", "Hyrule fell.", "Crónicas de Hyrule", "CANON")));
        streamAnswer("ok");

        ask("¿Qué le pasó a Hyrule?");

        List<Message> messages = sentMessages();
        assertThat(messages).extracting(Message::getMessageType).containsExactly(MessageType.SYSTEM, MessageType.USER);
        assertThat(messages.get(0).getText()).contains(ChatPrompts.PERSONA).contains("Cita las fuentes");
        // The user message is USER_TEMPLATE rendered by ContextualQueryAugmenter with our documentFormatter.
        // StringTemplate writes the platform line separator (\r\n on Windows), so line endings are normalised.
        assertThat(messages.get(1).getText().replace("\r\n", "\n"))
                .contains("<context>\n[Fuente 1 | Crónicas de Hyrule | The Legend of Zelda | CANON]\nHyrule fell.\n</context>")
                .contains("Pregunta: ¿Qué le pasó a Hyrule?")
                .contains("cita las fuentes como [N]");
    }

    @Test
    void keepsBracesInDocumentsAndQuestionsAsPlainText() {
        // The augmenter renders USER_TEMPLATE, but documents and question go in as values, which are not parsed.
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                chunk("doc-a", "The seal reads {ancient} and {\"key\": 1}.", "Sellos", "CANON")));
        streamAnswer("ok");

        ask("¿Qué significa {ancient}?");

        assertThat(sentMessages().get(1).getText())
                .contains("The seal reads {ancient} and {\"key\": 1}.")
                .contains("Pregunta: ¿Qué significa {ancient}?");
    }

    @Test
    void switchesToTheEmptyContextPromptWhenNothingWasRetrieved() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        streamAnswer("No lo encuentro en el archivo.");

        List<ServerSentEvent<Object>> events = ask("¿Cuál es la capital de Francia?");

        assertThat(events.getFirst().event()).isEqualTo("sources");
        assertThat(events.getFirst().data()).asInstanceOf(LIST).isEmpty();
        // EMPTY_CONTEXT_TEMPLATE replaces the user message, so the model is told to decline instead of improvising.
        assertThat(sentMessages().get(1).getText())
                .contains("que el archivo no contiene información sobre eso")
                .doesNotContain("<context>");
    }

    @Test
    void passesTheChosenFiltersToTheSearch() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        streamAnswer("ok");

        ask(new ChatRequest("¿Quién es Ciri?", "The Witcher", true));

        verify(vectorStore).similaritySearch(searchCaptor.capture());
        var b = new FilterExpressionBuilder();
        assertThat(searchCaptor.getValue().getFilterExpression())
                .isEqualTo(b.and(b.eq("universe", "The Witcher"), b.eq("canonicity", "CANON")).build());
    }

    @Test
    void searchesTheWholeArchiveWithoutFilters() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        streamAnswer("ok");

        ask("¿Quién es Ciri?");

        verify(vectorStore).similaritySearch(searchCaptor.capture());
        assertThat(searchCaptor.getValue().getFilterExpression()).isNull();
    }

    private List<ServerSentEvent<Object>> ask(String question) {
        return ask(new ChatRequest(question, null, false));
    }

    private List<ServerSentEvent<Object>> ask(ChatRequest request) {
        return chatService.ask(request).collectList().block(Duration.ofSeconds(5));
    }

    private List<Message> sentMessages() {
        verify(chatModel).stream(promptCaptor.capture());
        return promptCaptor.getValue().getInstructions();
    }

    private void streamAnswer(String... tokens) {
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.fromArray(tokens)
                .map(token -> new ChatResponse(List.of(new Generation(new AssistantMessage(token))))));
    }

    private static Document chunk(String documentId, String text, String title, String canonicity) {
        return new Document(text, Map.of(
                "documentId", documentId,
                "title", title,
                "universe", "The Legend of Zelda",
                "canonicity", canonicity));
    }
}
