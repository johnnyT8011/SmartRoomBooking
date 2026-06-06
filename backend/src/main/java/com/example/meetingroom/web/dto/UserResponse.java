package com.example.meetingroom.web.dto;

import com.example.meetingroom.domain.User;

/**
 * API view of a {@link User} (id + display name), used to populate the borrower selector.
 */
public class UserResponse {

    private Long id;
    private String empName;

    public static UserResponse from(User user) {
        UserResponse r = new UserResponse();
        r.id = user.getId();
        r.empName = user.getEmpName();
        return r;
    }

    public Long getId() {
        return id;
    }

    public String getEmpName() {
        return empName;
    }
}
