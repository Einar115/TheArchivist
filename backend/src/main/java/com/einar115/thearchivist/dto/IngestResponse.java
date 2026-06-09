package com.einar115.thearchivist.dto;

public record IngestResponse(
        String documentId,
        String source,
        String game
) {
}
