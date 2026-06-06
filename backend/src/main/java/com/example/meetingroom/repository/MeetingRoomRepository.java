package com.example.meetingroom.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.meetingroom.domain.MeetingRoom;

import jakarta.persistence.LockModeType;

/**
 * Basic CRUD access for {@link MeetingRoom} master data.
 */
public interface MeetingRoomRepository extends JpaRepository<MeetingRoom, Long> {

    Optional<MeetingRoom> findByRoomName(String roomName);

    /**
     * Pessimistic-write lookup used when creating a booking lock.
     *
     * <p>Acquiring a {@code PESSIMISTIC_WRITE} lock on the room row serializes concurrent
     * lock attempts for the <em>same</em> room: the second transaction blocks until the
     * first commits, then sees the freshly inserted overlapping booking and is rejected.
     * This is how "僅允許一筆成功" is guaranteed without Redis or a message queue.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM MeetingRoom r WHERE r.id = :id")
    Optional<MeetingRoom> findByIdForUpdate(@Param("id") Long id);
}
