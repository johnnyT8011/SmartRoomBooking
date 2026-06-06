package com.example.meetingroom.web.dto;

import com.example.meetingroom.domain.MeetingRoom;

/**
 * API view of a {@link MeetingRoom}.
 */
public class RoomResponse {

    private Long id;
    private String roomName;

    public static RoomResponse from(MeetingRoom room) {
        RoomResponse r = new RoomResponse();
        r.id = room.getId();
        r.roomName = room.getRoomName();
        return r;
    }

    public Long getId() {
        return id;
    }

    public String getRoomName() {
        return roomName;
    }
}
