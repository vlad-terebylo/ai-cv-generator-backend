package org.example.aicvgenerator.exception;

public class UnreadableDocumentException extends RuntimeException implements JobFailure {

    public UnreadableDocumentException(String message) {
        super(message);
    }

    public UnreadableDocumentException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String errorCode() {
        return "UNREADABLE_DOCUMENT";
    }

    @Override
    public String safeMessage() {
        return getMessage();
    }
}
