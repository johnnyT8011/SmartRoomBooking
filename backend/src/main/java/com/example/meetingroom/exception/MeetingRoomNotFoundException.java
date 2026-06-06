package com.example.meetingroom.exception;

public class MeetingRoomNotFoundException extends ResourceNotFoundException {

    public MeetingRoomNotFoundException(Long roomId) {
        super("查無此會議室 (id=" + roomId + ")");
    }
}
