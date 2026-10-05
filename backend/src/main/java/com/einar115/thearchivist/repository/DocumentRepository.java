package com.einar115.thearchivist.repository;

import com.einar115.thearchivist.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {

}
