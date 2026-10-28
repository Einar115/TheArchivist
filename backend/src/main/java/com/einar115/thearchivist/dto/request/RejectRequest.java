package com.einar115.thearchivist.dto.request;

import jakarta.validation.constraints.Size;

public record RejectRequest(
        @Size(max = 500) String reason
) {
}
