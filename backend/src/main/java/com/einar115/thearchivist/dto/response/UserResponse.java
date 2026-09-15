package com.einar115.thearchivist.dto.response;

import com.einar115.thearchivist.entity.RoleEntity;

public record UserResponse(
        Integer id,
        String username,
        RoleEntity.RoleEnum role
) {
}
