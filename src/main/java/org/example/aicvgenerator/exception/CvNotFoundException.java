package org.example.aicvgenerator.exception;

public class CvNotFoundException extends RuntimeException {

    public CvNotFoundException(long id) {
        super(String.format("CV with id %d not found", id));
    }
}
