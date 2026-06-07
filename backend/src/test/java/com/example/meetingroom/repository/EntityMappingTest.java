package com.example.meetingroom.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.example.meetingroom.domain.Booking;
import com.example.meetingroom.domain.BookingStatus;
import com.example.meetingroom.domain.MeetingRoom;
import com.example.meetingroom.domain.User;

/**
 * Verifies ORM mapping: the {@code @ManyToOne} relations on {@link Booking} persist and
 * reload correctly, and the basic {@link User}/{@link MeetingRoom} repositories work.
 */
@DataJpaTest
class EntityMappingTest {

    @Autowired private TestEntityManager em;
    @Autowired private UserRepository userRepository;
    @Autowired private MeetingRoomRepository meetingRoomRepository;
    @Autowired private BookingRepository bookingRepository;

    @Test
    void booking_persistsManyToOneRelations() {
        User user = em.persist(new User("牛", "niu@example.com"));
        MeetingRoom room = em.persist(new MeetingRoom("會議室 A"));
        Booking booking = em.persist(new Booking(user, room,
                new com.example.meetingroom.domain.TimeRange(LocalDateTime.of(2026, 6, 5, 9, 0),
                LocalDateTime.of(2026, 6, 5, 10, 30)),
                BookingStatus.BOOKED));
        em.flush();
        em.clear();

        Booking reloaded = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(reloaded.getUser().getEmpName()).isEqualTo("牛");
        assertThat(reloaded.getRoom().getRoomName()).isEqualTo("會議室 A");
        assertThat(reloaded.getStatus()).isEqualTo(BookingStatus.BOOKED);
        assertThat(reloaded.getStartTime()).isEqualTo(LocalDateTime.of(2026, 6, 5, 9, 0));
    }

    @Test
    void userRepository_savesAndQueries() {
        userRepository.save(new User("羊", "yang@example.com"));
        assertThat(userRepository.findByEmpName("羊")).isPresent();
    }

    @Test
    void meetingRoomRepository_savesAndQueries() {
        meetingRoomRepository.save(new MeetingRoom("會議室 C"));
        assertThat(meetingRoomRepository.findByRoomName("會議室 C")).isPresent();
    }
}
