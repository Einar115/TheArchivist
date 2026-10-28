package com.einar115.thearchivist.exception;

import com.einar115.thearchivist.entity.DocumentEntity;

import java.util.UUID;

public class InvalidDocumentStateException extends RuntimeException {
    public InvalidDocumentStateException(UUID id, DocumentEntity.DocumentStatusEnum status) {
        super("Document " + id + " is " + status + " and cannot be reviewed");
    }
}
