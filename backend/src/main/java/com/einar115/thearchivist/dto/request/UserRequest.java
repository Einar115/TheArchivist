package com.einar115.thearchivist.dto.request;

import com.einar115.thearchivist.entity.RoleEntity;

public record UserRequest(
        String username,
        String password,
        RoleEntity.RoleEnum role
) {
}
