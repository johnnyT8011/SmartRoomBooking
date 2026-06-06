package com.example.meetingroom.exception;

public class UserNotFoundException extends ResourceNotFoundException {

    public UserNotFoundException(Long userId) {
        super("查無此使用者 (id=" + userId + ")");
    }
}
