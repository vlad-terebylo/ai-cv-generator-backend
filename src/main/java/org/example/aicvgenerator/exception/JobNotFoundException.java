package org.example.aicvgenerator.exception;

import java.util.UUID;

public class JobNotFoundException extends RuntimeException {

    public JobNotFoundException(UUID id) {
        super(String.format("Job with id %s not found", id));
    }
}
