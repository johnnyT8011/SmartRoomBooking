package com.example.meetingroom.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.meetingroom.repository.UserRepository;
import com.example.meetingroom.web.dto.UserResponse;

/**
 * Read-only lookup of employees, used by the front-end borrower selector. (Auxiliary to the
 * spec's required controllers — the system has no authentication, so the client needs the
 * user id to attach to a booking request.)
 */
@RestController
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/api/users")
    public List<UserResponse> users() {
        return userRepository.findAll().stream()
                .map(UserResponse::from)
                .toList();
    }
}
