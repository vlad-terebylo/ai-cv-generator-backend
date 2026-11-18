package org.example.aicvgenerator.exception;

public class NotACvException extends RuntimeException implements JobFailure {

    public NotACvException(String message) {
        super(message);
    }

    @Override
    public String errorCode() {
        return "NOT_A_CV";
    }

    @Override
    public String safeMessage() {
        return getMessage();
    }
}
