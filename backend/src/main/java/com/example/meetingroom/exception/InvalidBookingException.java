package com.example.meetingroom.exception;

/**
 * Raised when a booking request violates a business rule (30-minute blocks, duration
 * limits, booking window, illegal state transition). Mapped to HTTP 400.
 */
public class InvalidBookingException extends RuntimeException {

    public InvalidBookingException(String message) {
        super(message);
    }
}
