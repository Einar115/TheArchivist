package com.einar115.thearchivist.dto.response;

import java.util.UUID;

public record DocumentContentResponse(
        UUID id,
        String content
) {
}
