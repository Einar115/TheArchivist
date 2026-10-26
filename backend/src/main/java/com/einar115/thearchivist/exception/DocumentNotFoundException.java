package com.einar115.thearchivist.exception;

import java.util.UUID;

public class DocumentNotFoundException extends RuntimeException {
    public DocumentNotFoundException(UUID id) {
        super("Document " + id + " not found");
    }
}
