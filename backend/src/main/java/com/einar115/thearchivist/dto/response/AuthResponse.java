package com.einar115.thearchivist.dto.response;

import java.util.List;

public record AuthResponse(
        String username,
        List<String> roles,
        String accessToken,
        String refreshToken
) {
}
