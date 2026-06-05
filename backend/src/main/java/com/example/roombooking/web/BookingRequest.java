package com.example.roombooking.web;

import java.time.LocalDateTime;

public record BookingRequest(Long roomId, String borrower, LocalDateTime start, LocalDateTime end) {
}
