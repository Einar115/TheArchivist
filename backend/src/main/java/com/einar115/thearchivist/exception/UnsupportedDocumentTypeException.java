package com.einar115.thearchivist.exception;

public class UnsupportedDocumentTypeException extends RuntimeException {
    public UnsupportedDocumentTypeException(String filename, String mimeType) {
        super("File '" + filename + "' has an unsupported type: " + mimeType);
    }
}
