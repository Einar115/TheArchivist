package com.einar115.thearchivist.dto.response;

import com.einar115.thearchivist.entity.DocumentEntity;

import java.time.Instant;
import java.util.UUID;

public record DocumentSummaryResponse(
        UUID id,
        String filename,
        Long fileSize,
        Instant uploadedAt,
        String uploadedBy,
        DocumentEntity.DocumentStatusEnum status,
        Integer chunkCount,
        String rejectionReason,
        String universe,
        String work,
        String lang,
        String title,
        DocumentEntity.CanonicityEnum canonicity,
        DocumentEntity.DocumentTypeEnum documentType
) {
}
