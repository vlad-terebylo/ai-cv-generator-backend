package org.example.aicvgenerator.exception;

/** An id received from a client does not have the format of the resource's id. */
public class InvalidIdException extends RuntimeException {

    public InvalidIdException(String id) {
        super(String.format("'%s' is not a valid id", id));
    }
}
