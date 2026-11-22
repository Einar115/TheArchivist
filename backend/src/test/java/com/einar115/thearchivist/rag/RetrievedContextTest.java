package com.einar115.thearchivist.rag;

import com.einar115.thearchivist.dto.response.ChatSourceResponse;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class RetrievedContextTest {

    @Test
    void numbersSourcesPerDocumentInRetrievalOrder() {
        var context = RetrievedContext.from(List.of(
                chunk("doc-a", "Hyrule fell.", Map.of("title", "Crónicas de Hyrule", "universe", "The Legend of Zelda", "canonicity", "CANON")),
                chunk("doc-b", "Ganon rose.", Map.of("filename", "teorias.md", "universe", "The Legend of Zelda", "canonicity", "SPECULATION")),
                chunk("doc-a", "The Sheikah hid.", Map.of("title", "Crónicas de Hyrule", "universe", "The Legend of Zelda", "canonicity", "CANON"))));

        // The two chunks of doc-a share [1].
        assertThat(context.sources()).extracting(ChatSourceResponse::number, ChatSourceResponse::documentId)
                .containsExactly(tuple(1, "doc-a"), tuple(2, "doc-b"));
        assertThat(context.text()).isEqualTo("""
                [Fuente 1 | Crónicas de Hyrule | The Legend of Zelda | CANON]
                Hyrule fell.

                [Fuente 2 | teorias.md | The Legend of Zelda | SPECULATION]
                Ganon rose.

                [Fuente 1 | Crónicas de Hyrule | The Legend of Zelda | CANON]
                The Sheikah hid.""");
    }

    @Test
    void includesTheWorkNextToTheUniverse() {
        var context = RetrievedContext.from(List.of(
                chunk("doc-a", "Text.", Map.of("title", "Bestiario", "universe", "The Witcher", "work", "The Witcher 3", "canonicity", "CANON"))));

        assertThat(context.text()).startsWith("[Fuente 1 | Bestiario | The Witcher · The Witcher 3 | CANON]");
        assertThat(context.sources().getFirst().work()).isEqualTo("The Witcher 3");
    }

    @Test
    void prefersTheTitleAndFallsBackToTheFilename() {
        var context = RetrievedContext.from(List.of(
                chunk("doc-a", "A.", Map.of("title", "Título", "filename", "archivo.pdf")),
                chunk("doc-b", "B.", Map.of("title", " ", "filename", "otro.pdf")),
                chunk("doc-c", "C.", Map.of())));

        assertThat(context.sources()).extracting(ChatSourceResponse::title)
                .containsExactly("Título", "otro.pdf", "Documento sin título");
    }

    @Test
    void omitsMissingMetadataFromTheHeader() {
        var context = RetrievedContext.from(List.of(chunk("doc-a", "Text.", Map.of("filename", "notas.txt"))));

        assertThat(context.text()).startsWith("[Fuente 1 | notas.txt]\n");
        assertThat(context.sources().getFirst().universe()).isNull();
    }

    @Test
    void hasNoSourcesWhenNothingWasRetrieved() {
        var context = RetrievedContext.from(List.of());

        assertThat(context.sources()).isEmpty();
        assertThat(context.text()).isEqualTo(ChatPrompts.NO_SOURCES);
    }

    private static Document chunk(String documentId, String text, Map<String, Object> metadata) {
        Map<String, Object> all = new HashMap<>(metadata);
        all.put("documentId", documentId);
        return new Document(text, all);
    }
}
