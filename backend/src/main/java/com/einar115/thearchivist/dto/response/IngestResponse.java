package com.einar115.thearchivist.dto.response;

public record IngestResponse(
        String documentId,
        String source,
        String game
) {
}
