package org.example.aicvgenerator.exception;

public class UnsupportedDocumentException extends RuntimeException implements JobFailure {

    public UnsupportedDocumentException(String message) {
        super(message);
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
