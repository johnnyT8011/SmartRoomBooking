package com.example.meetingroom.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.meetingroom.domain.Booking;
import com.example.meetingroom.service.BookingService;
import com.example.meetingroom.service.NotificationService;
import com.example.meetingroom.web.dto.BookingResponse;
import com.example.meetingroom.web.dto.LockRequest;

import jakarta.validation.Valid;

/**
 * Presentation layer for the booking lifecycle and the SSE notification channel.
 *
 * <p>Endpoints (per spec):</p>
 * <ul>
 *   <li>{@code POST /api/bookings/lock} — create a LOCKING hold (only on explicit submit).</li>
 *   <li>{@code POST /api/bookings/{id}/confirm} — LOCKING → BOOKED.</li>
 *   <li>{@code POST /api/bookings/{id}/cancel} — → CANCELLED.</li>
 *   <li>{@code POST /api/bookings/{id}/checkin} — BOOKED → CHECKED_IN.</li>
 *   <li>{@code GET  /api/notifications/sse} — open the SSE push channel.</li>
 * </ul>
 */
@RestController
public class BookingController {

    private final BookingService bookingService;
    private final NotificationService notificationService;

    public BookingController(BookingService bookingService, NotificationService notificationService) {
        this.bookingService = bookingService;
        this.notificationService = notificationService;
    }

    @PostMapping("/api/bookings/lock")
    public ResponseEntity<BookingResponse> lock(@Valid @RequestBody LockRequest request) {
        Booking booking = bookingService.lockRoom(request.getUserId(), request.getRoomId(),
                request.getStartTime(), request.getEndTime());
        return ResponseEntity.status(HttpStatus.CREATED).body(BookingResponse.from(booking));
    }

    @PostMapping("/api/bookings/{id}/confirm")
    public ResponseEntity<BookingResponse> confirm(@PathVariable Long id) {
        return ResponseEntity.ok(BookingResponse.from(bookingService.confirmBooking(id)));
    }

    @PostMapping("/api/bookings/{id}/cancel")
    public ResponseEntity<BookingResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(BookingResponse.from(bookingService.cancelBooking(id)));
    }

    @PostMapping("/api/bookings/{id}/checkin")
    public ResponseEntity<BookingResponse> checkIn(@PathVariable Long id) {
        return ResponseEntity.ok(BookingResponse.from(bookingService.checkIn(id)));
    }

    /**
     * Open a Server-Sent Events stream for a user. The browser keeps this connection open
     * and receives booking/notification events pushed by {@link NotificationService}.
     */
    @GetMapping(value = "/api/notifications/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sse(@RequestParam Long userId) {
        return notificationService.subscribe(userId);
    }
}
