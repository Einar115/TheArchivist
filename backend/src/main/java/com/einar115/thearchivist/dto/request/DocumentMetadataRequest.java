package com.einar115.thearchivist.dto.request;

import com.einar115.thearchivist.entity.DocumentEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record DocumentMetadataRequest(
        @NotBlank @Size(max = 100) String universe,
        @Size(max = 150) String work,
        @NotBlank @Pattern(regexp = "[a-z]{2}") String lang,
        @Size(max = 200) String title,
        @NotNull DocumentEntity.CanonicityEnum canonicity,
        DocumentEntity.DocumentTypeEnum documentType,
        @Size(max = 150) String author,
        @URL @Size(max = 500) String originUrl
) {
}
