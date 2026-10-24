package org.example.aicvgenerator.exception;

public class UnreadableDocumentException extends RuntimeException {

    public UnreadableDocumentException(String message) {
        super(message);
    }

    public UnreadableDocumentException(String message, Throwable cause) {
        super(message, cause);
    }
}
