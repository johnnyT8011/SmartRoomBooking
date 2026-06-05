package com.example.roombooking.web;

import com.example.roombooking.model.Booking;
import com.example.roombooking.service.BookingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService service;

    public BookingController(BookingService service) {
        this.service = service;
    }

    @GetMapping
    public List<Booking> list(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate day = (date == null) ? LocalDate.now() : date;
        return service.getBookingsForDay(day);
    }

    @PostMapping
    public ResponseEntity<Booking> create(@RequestBody BookingRequest req) {
        Booking b = service.createBooking(req.roomId(), req.borrower(), req.start(), req.end());
        return ResponseEntity.status(HttpStatus.CREATED).body(b);
    }

    @PostMapping("/{id}/checkin")
    public Booking checkin(@PathVariable Long id) {
        return service.checkIn(id);
    }
}
