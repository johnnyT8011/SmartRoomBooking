package com.example.meetingroom.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.example.meetingroom.domain.Booking;
import com.example.meetingroom.domain.BookingStatus;
import com.example.meetingroom.domain.MeetingRoom;
import com.example.meetingroom.domain.User;

/**
 * Exercises the custom anti-overlap and housekeeping queries against an H2 database.
 */
@DataJpaTest
class BookingRepositoryTest {

    @Autowired private BookingRepository bookingRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MeetingRoomRepository meetingRoomRepository;

    private User user;
    private MeetingRoom roomA;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("員工甲", "a@example.com"));
        roomA = meetingRoomRepository.save(new MeetingRoom("會議室 A"));
    }

    private Booking persistBooking(LocalDateTime start, LocalDateTime end, BookingStatus status) {
        return bookingRepository.save(new Booking(user, roomA, start, end, status));
    }

    @Test
    void findOverlapping_detectsIntersectingActiveBooking() {
        // Existing BOOKED 14:00–15:00.
        persistBooking(LocalDateTime.of(2026, 6, 5, 14, 0),
                LocalDateTime.of(2026, 6, 5, 15, 0), BookingStatus.BOOKED);

        List<Booking> overlap = bookingRepository.findOverlapping(roomA.getId(),
                LocalDateTime.of(2026, 6, 5, 14, 30),
                LocalDateTime.of(2026, 6, 5, 15, 30),
                BookingStatus.activeStatuses());

        assertThat(overlap).hasSize(1);
    }

    @Test
    void findOverlapping_treatsTouchingEdgesAsFree() {
        persistBooking(LocalDateTime.of(2026, 6, 5, 14, 0),
                LocalDateTime.of(2026, 6, 5, 15, 0), BookingStatus.BOOKED);

        // Back-to-back 15:00–16:00 must NOT count as overlap.
        assertThat(bookingRepository.findOverlapping(roomA.getId(),
                LocalDateTime.of(2026, 6, 5, 15, 0),
                LocalDateTime.of(2026, 6, 5, 16, 0),
                BookingStatus.activeStatuses())).isEmpty();

        // 13:00–14:00 just before also free.
        assertThat(bookingRepository.findOverlapping(roomA.getId(),
                LocalDateTime.of(2026, 6, 5, 13, 0),
                LocalDateTime.of(2026, 6, 5, 14, 0),
                BookingStatus.activeStatuses())).isEmpty();
    }

    @Test
    void findOverlapping_ignoresCancelledAndExpired() {
        persistBooking(LocalDateTime.of(2026, 6, 5, 14, 0),
                LocalDateTime.of(2026, 6, 5, 15, 0), BookingStatus.CANCELLED);
        persistBooking(LocalDateTime.of(2026, 6, 5, 14, 0),
                LocalDateTime.of(2026, 6, 5, 15, 0), BookingStatus.EXPIRED);

        assertThat(bookingRepository.findOverlapping(roomA.getId(),
                LocalDateTime.of(2026, 6, 5, 14, 0),
                LocalDateTime.of(2026, 6, 5, 15, 0),
                BookingStatus.activeStatuses())).isEmpty();
    }

    @Test
    void findOverlappingExcluding_skipsTheBookingItself() {
        Booking self = persistBooking(LocalDateTime.of(2026, 6, 5, 14, 0),
                LocalDateTime.of(2026, 6, 5, 15, 0), BookingStatus.LOCKING);

        assertThat(bookingRepository.findOverlappingExcluding(roomA.getId(),
                self.getStartTime(), self.getEndTime(),
                BookingStatus.activeStatuses(), self.getId())).isEmpty();
    }

    @Test
    void findExpiredLocks_returnsOnlyTimedOutLocks() {
        Booking stale = persistBooking(LocalDateTime.of(2026, 6, 5, 16, 0),
                LocalDateTime.of(2026, 6, 5, 16, 30), BookingStatus.LOCKING);
        stale.setLockExpiresAt(LocalDateTime.of(2026, 6, 5, 9, 0));
        bookingRepository.save(stale);

        List<Booking> expired = bookingRepository.findExpiredLocks(LocalDateTime.of(2026, 6, 5, 9, 1));

        assertThat(expired).extracting(Booking::getId).containsExactly(stale.getId());
    }

    @Test
    void findNoShows_returnsBookedStartedBeyondThreshold() {
        Booking noShow = persistBooking(LocalDateTime.of(2026, 6, 5, 9, 0),
                LocalDateTime.of(2026, 6, 5, 9, 30), BookingStatus.BOOKED);
        // A CHECKED_IN booking with the same time must NOT be treated as a no-show.
        persistBooking(LocalDateTime.of(2026, 6, 5, 9, 0),
                LocalDateTime.of(2026, 6, 5, 9, 30), BookingStatus.CHECKED_IN);

        List<Booking> result = bookingRepository.findNoShows(LocalDateTime.of(2026, 6, 5, 9, 16));

        assertThat(result).extracting(Booking::getId).containsExactly(noShow.getId());
    }
}
