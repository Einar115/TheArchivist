package com.einar115.thearchivist.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotBlank @Size(max = 2000) String question,
        @Size(max = 100) String universe,
        Boolean canonOnly   // optional in the JSON: missing or null means false
) {
    public ChatRequest {
        if (canonOnly == null) canonOnly = false;
    }
}
