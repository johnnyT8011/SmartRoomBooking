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

    // 測試確認有從資料庫撈出資料
    @Test
    void booking_persistsManyToOneRelations() {
        // 設定預約資料，user、room、時間、狀態
        User user = em.persist(new User("牛", "niu@example.com"));
        MeetingRoom room = em.persist(new MeetingRoom("會議室 A"));
        Booking booking = em.persist(new Booking(user, room,
                LocalDateTime.of(2026, 6, 5, 9, 0),
                LocalDateTime.of(2026, 6, 5, 10, 30),
                BookingStatus.BOOKED));
        // 寫入資料庫
        em.flush();
        // 清除快取
        em.clear();

        // 從資料庫撈資料，orElseThrow 有東西拿出，沒東西丟錯誤
        Booking reloaded = bookingRepository.findById(booking.getId()).orElseThrow();

        // 比對撈出來的資料是否正確
        assertThat(reloaded.getUser().getEmpName()).isEqualTo("牛");
        assertThat(reloaded.getRoom().getRoomName()).isEqualTo("會議室 A");
        assertThat(reloaded.getStatus()).isEqualTo(BookingStatus.BOOKED);
        assertThat(reloaded.getStartTime()).isEqualTo(LocalDateTime.of(2026, 6, 5, 9, 0));
    }

    // 測試資料有無寫入資料庫
    @Test
    void userRepository_savesAndQueries() {
        // new 一個使用者羊，寫入資料庫
        userRepository.save(new User("羊", "yang@example.com"));
        // 確定裡面有這個使用者(isPresent)
        assertThat(userRepository.findByEmpName("羊")).isPresent();
    }

    @Test
    void meetingRoomRepository_savesAndQueries() {
        // new 一個會議室 C，寫入資料庫
        meetingRoomRepository.save(new MeetingRoom("會議室 C"));
        // 確定裡面有這個會議室(isPresent)
        assertThat(meetingRoomRepository.findByRoomName("會議室 C")).isPresent();
    }
}
