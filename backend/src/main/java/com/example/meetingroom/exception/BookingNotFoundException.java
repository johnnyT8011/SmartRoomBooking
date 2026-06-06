package com.example.meetingroom.exception;

public class BookingNotFoundException extends ResourceNotFoundException {

    public BookingNotFoundException(Long bookingId) {
        super("查無此預約紀錄 (id=" + bookingId + ")");
    }
}
