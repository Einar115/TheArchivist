package com.einar115.thearchivist.dto;

public record AuthRequest(
        String username,
        String password,
        String deviceId
) {
}
