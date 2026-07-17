package com.einar115.thearchivist.dto.request;

public record AuthRequest(
        String username,
        String password,
        String deviceId
) {
}
