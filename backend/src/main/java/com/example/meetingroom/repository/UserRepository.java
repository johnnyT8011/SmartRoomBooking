package com.example.meetingroom.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.meetingroom.domain.User;

/**
 * Basic CRUD access for {@link User} master data.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmpName(String empName);
}
