package org.example.aicvgenerator.exception;

public class CvNotFoundException extends RuntimeException {

    public CvNotFoundException(long id) {
        super("CV with id " + id + " not found");
    }
}
