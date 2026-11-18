package org.example.aicvgenerator.exception;

/**
 * Implemented by exceptions that a background job reports to the client as FAILED. The job machinery uses these two
 * values and knows nothing about the exception type; any other exception becomes {@code INTERNAL_ERROR}.
 */
public interface JobFailure {

    /** Stable machine-readable code that clients can switch on. */
    String errorCode();

    /** Message that is safe to show to the client; never internal details. */
    String safeMessage();
}
