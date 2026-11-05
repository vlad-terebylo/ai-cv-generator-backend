package org.example.aicvgenerator.exception;

import java.util.UUID;

public class CvImportNotFoundException extends RuntimeException {

    public CvImportNotFoundException(UUID id) {
        super("CV import with id " + id + " not found");
    }
}
