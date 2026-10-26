package com.einar115.thearchivist.dto.response;

import java.util.UUID;

public record SubmitResponse(
        UUID id,
        String filename,
        String status,
        String universe,
        String lang
) {
}
