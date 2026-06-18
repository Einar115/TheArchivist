package com.einar115.thearchivist.repository;

import com.einar115.thearchivist.entity.DocumentHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DocumentHistoryRepository extends JpaRepository<DocumentHistoryEntity, Integer> {
    Optional<DocumentHistoryEntity> findByDocumentId(UUID documentId);
}
