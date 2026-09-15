package com.einar115.thearchivist.dto.request;

import com.einar115.thearchivist.entity.RoleEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserRequest(
        @NotBlank String username,
        @NotBlank @Size(min = 8) String password,
        @NotNull RoleEntity.RoleEnum role
) {
}
