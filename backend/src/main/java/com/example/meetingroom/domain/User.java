package com.example.meetingroom.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 企業員工 (system user / employee).
 *
 * <p>This is a "master data" entity. It intentionally holds <b>no</b> collection of
 * {@link Booking}s to avoid a bidirectional association that would cause infinite
 * recursion during JSON serialization.</p>
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String empName;

    @Column(unique = true)
    private String email;

    protected User() {
        // required by JPA
    }

    public User(String empName, String email) {
        this.empName = empName;
        this.email = email;
    }

    public Long getId() {
        return id;
    }

    public String getEmpName() {
        return empName;
    }

    public void setEmpName(String empName) {
        this.empName = empName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
