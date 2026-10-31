package org.example.aicvgenerator.exception;

public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(String email) {
        super("CV with email " + email + " already exists");
    }
}
