package com.einar115.thearchivist.exception;

public class DuplicateDocumentException extends RuntimeException {
    public DuplicateDocumentException(String checksum) {
        super("Document with checksum " + checksum + " already exists");
    }
}
