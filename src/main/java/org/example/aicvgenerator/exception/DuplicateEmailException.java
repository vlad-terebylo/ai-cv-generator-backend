package org.example.aicvgenerator.exception;

public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(String email) {
        super(String.format("CV with email %s already exists", email));
    }
}
