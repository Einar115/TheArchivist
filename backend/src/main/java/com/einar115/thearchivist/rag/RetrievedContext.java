package com.einar115.thearchivist.rag;

import com.einar115.thearchivist.dto.response.ChatSourceResponse;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The chunks retrieved for a question, numbered per document: chunks of the same document share one [N], so a
 * citation points at something the UI can show as a single source.
 */
public record RetrievedContext(List<ChatSourceResponse> sources, String text) {

    public static RetrievedContext from(List<Document> chunks) {
        Map<String, ChatSourceResponse> sources = new LinkedHashMap<>();
        List<String> blocks = new ArrayList<>();

        for (Document chunk : chunks) {
            Map<String, Object> metadata = chunk.getMetadata();
            String documentId = String.valueOf(metadata.getOrDefault("documentId", chunk.getId()));
            ChatSourceResponse source = sources.get(documentId);
            if (source == null) {
                source = toSource(sources.size() + 1, documentId, metadata);
                sources.put(documentId, source);
            }
            blocks.add(header(source) + "\n" + Objects.requireNonNullElse(chunk.getText(), "").strip());
        }

        String text = blocks.isEmpty() ? ChatPrompts.NO_SOURCES : String.join("\n\n", blocks);
        return new RetrievedContext(List.copyOf(sources.values()), text);
    }

    private static ChatSourceResponse toSource(int number, String documentId, Map<String, Object> metadata) {
        // The title is optional on submit, so the filename stands in for it.
        String title = firstPresent(metadata, "title", "filename");
        return new ChatSourceResponse(
                number,
                documentId,
                title != null ? title : "Documento sin título",
                firstPresent(metadata, "universe"),
                firstPresent(metadata, "work"),
                firstPresent(metadata, "canonicity"));
    }

    private static String header(ChatSourceResponse source) {
        String setting = Stream.of(source.universe(), source.work())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" · "));
        return Stream.of("Fuente " + source.number(), source.title(), setting, source.canonicity())
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(" | ", "[", "]"));
    }

    private static String firstPresent(Map<String, Object> metadata, String... keys) {
        for (String key : keys) {
            Object value = metadata.get(key);
            if (value != null && !value.toString().isBlank()) return value.toString();
        }
        return null;
    }
}
