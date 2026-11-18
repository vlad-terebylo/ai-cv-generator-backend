package org.example.aicvgenerator.exception;

public class AiServiceException extends RuntimeException implements JobFailure {

    public AiServiceException(String message) {
        super(message);
    }

    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String errorCode() {
        return "AI_SERVICE_ERROR";
    }

    /** The message of this exception can carry the remote service's details, so clients get a fixed text instead. */
    @Override
    public String safeMessage() {
        return "The AI service could not process the document";
    }
}
