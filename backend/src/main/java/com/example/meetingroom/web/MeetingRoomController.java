package com.example.meetingroom.web;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.meetingroom.domain.Booking;
import com.example.meetingroom.repository.MeetingRoomRepository;
import com.example.meetingroom.service.BookingService;
import com.example.meetingroom.web.dto.BookingResponse;
import com.example.meetingroom.web.dto.RoomResponse;

/**
 * Read-only resource queries: room master data and the current week's occupied slots.
 *
 * <ul>
 *   <li>{@code GET /api/rooms} — list meeting rooms.</li>
 *   <li>{@code GET /api/rooms/schedule} — this week's bookings (Mon–Sun).</li>
 * </ul>
 */
@RestController
public class MeetingRoomController {

    private final MeetingRoomRepository meetingRoomRepository;
    private final BookingService bookingService;

    public MeetingRoomController(MeetingRoomRepository meetingRoomRepository,
                                 BookingService bookingService) {
        this.meetingRoomRepository = meetingRoomRepository;
        this.bookingService = bookingService;
    }

    @GetMapping("/api/rooms")
    public List<RoomResponse> rooms() {
        return meetingRoomRepository.findAll().stream()
                .map(RoomResponse::from)
                .toList();
    }

    /**
     * This week's occupied slots by default. If both {@code from} and {@code to} (ISO
     * date-time) are supplied, returns bookings in that arbitrary range instead — used by
     * the front-end day/month views.
     */
    @GetMapping("/api/rooms/schedule")
    public List<BookingResponse> schedule(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        List<Booking> bookings = (from != null && to != null)
                ? bookingService.getSchedule(from, to)
                : bookingService.getWeeklySchedule();
        return bookings.stream().map(BookingResponse::from).toList();
    }
}
