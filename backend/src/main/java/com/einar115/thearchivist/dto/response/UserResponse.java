package com.einar115.thearchivist.dto.response;

import com.einar115.thearchivist.entity.RoleEntity;

public record UserResponse(
        String username,
        RoleEntity.RoleEnum role
) {
}
