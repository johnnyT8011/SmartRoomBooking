package com.example.meetingroom.exception;

/**
 * Base type for "entity does not exist" errors. Mapped to HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
