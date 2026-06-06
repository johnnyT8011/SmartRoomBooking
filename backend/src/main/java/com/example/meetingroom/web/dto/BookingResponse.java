package com.example.meetingroom.web.dto;

import java.time.LocalDateTime;

import com.example.meetingroom.domain.Booking;

/**
 * API view of a {@link Booking}. Flattens the {@code user}/{@code room} associations into
 * plain fields so no lazy JPA proxy is ever serialized.
 */
public class BookingResponse {

    private Long id;
    private Long roomId;
    private String roomName;
    private Long userId;
    private String borrower;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private LocalDateTime lockExpiresAt;
    private LocalDateTime checkedInAt;

    public static BookingResponse from(Booking b) {
        BookingResponse r = new BookingResponse();
        r.id = b.getId();
        r.roomId = b.getRoom().getId();
        r.roomName = b.getRoom().getRoomName();
        r.userId = b.getUser().getId();
        r.borrower = b.getUser().getEmpName();
        r.startTime = b.getStartTime();
        r.endTime = b.getEndTime();
        r.status = b.getStatus().name();
        r.lockExpiresAt = b.getLockExpiresAt();
        r.checkedInAt = b.getCheckedInAt();
        return r;
    }

    public Long getId() {
        return id;
    }

    public Long getRoomId() {
        return roomId;
    }

    public String getRoomName() {
        return roomName;
    }

    public Long getUserId() {
        return userId;
    }

    public String getBorrower() {
        return borrower;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getLockExpiresAt() {
        return lockExpiresAt;
    }

    public LocalDateTime getCheckedInAt() {
        return checkedInAt;
    }
}
