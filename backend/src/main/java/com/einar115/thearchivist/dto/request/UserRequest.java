package com.einar115.thearchivist.dto.request;

import com.einar115.thearchivist.entity.RoleEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record UserRequest(
        @NotBlank String username,
        @NotBlank @Size(min = 8) String password,
        @NotEmpty RoleEntity.RoleEnum role
) {
}
