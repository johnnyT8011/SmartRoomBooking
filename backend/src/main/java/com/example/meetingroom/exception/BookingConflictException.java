package com.example.meetingroom.exception;

/**
 * Raised when a requested slot overlaps an existing active booking. Mapped to HTTP 409.
 */
public class BookingConflictException extends RuntimeException {

    public BookingConflictException(String message) {
        super(message);
    }
}
