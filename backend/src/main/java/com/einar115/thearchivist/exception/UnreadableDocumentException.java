package com.einar115.thearchivist.exception;

public class UnreadableDocumentException extends RuntimeException {
    public UnreadableDocumentException(String filename) {
        super("No text could be extracted from '" + filename + "'");
    }

    public UnreadableDocumentException(String filename, Throwable cause) {
        super("No text could be extracted from '" + filename + "'", cause);
    }
}
