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

    // 建立一個使用者員工甲，和一個會議室 A
    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("員工甲", "a@example.com"));
        roomA = meetingRoomRepository.save(new MeetingRoom("會議室 A"));
    }

    // 預約單
    private Booking persistBooking(LocalDateTime start, LocalDateTime end, BookingStatus status) {
        return bookingRepository.save(new Booking(user, roomA, start, end, status));
    }

    // 到資料庫查詢是否有重疊資料
    @Test
    void findOverlapping_detectsIntersectingActiveBooking() {
        // 存在一個預約成功單 2026/6/5 14:00–15:00.
        persistBooking(LocalDateTime.of(2026, 6, 5, 14, 0),
                LocalDateTime.of(2026, 6, 5, 15, 0), BookingStatus.BOOKED);

        // 新增一個預約單，2026/6/5 14:30~15:30，呼叫 findOverlapping 去看 roomA 這個時段是否有人預約
        List<Booking> overlap = bookingRepository.findOverlapping(roomA.getId(),
                LocalDateTime.of(2026, 6, 5, 14, 30),
                LocalDateTime.of(2026, 6, 5, 15, 30),
                BookingStatus.activeStatuses());
        // 抓出結果需為 1，因為上面建立一筆重疊的
        assertThat(overlap).hasSize(1);
    }

    // 預約不重疊時段
    @Test
    void findOverlapping_treatsTouchingEdgesAsFree() {
        // 存在一個預約成功單 2026/6/5 14:00–15:00.
        persistBooking(LocalDateTime.of(2026, 6, 5, 14, 0),
                LocalDateTime.of(2026, 6, 5, 15, 0), BookingStatus.BOOKED);

        // 新增一個預約單預約 2026/6/5 15:00~16:00，預期是空的可以預約
        assertThat(bookingRepository.findOverlapping(roomA.getId(),
                LocalDateTime.of(2026, 6, 5, 15, 0),
                LocalDateTime.of(2026, 6, 5, 16, 0),
                BookingStatus.activeStatuses())).isEmpty();

        // 新增一個預約單預約 2026/6/5 13:00~14:00，預期是空的可以預約
        assertThat(bookingRepository.findOverlapping(roomA.getId(),
                LocalDateTime.of(2026, 6, 5, 13, 0),
                LocalDateTime.of(2026, 6, 5, 14, 0),
                BookingStatus.activeStatuses())).isEmpty();
    }

    // 測試取消、沒報到的時段可以預約
    @Test
    void findOverlapping_ignoresCancelledAndExpired() {
        // 存在一個預約成功單 2026/6/5 14:00–15:00. 狀態是 CANCELLED(取消)
        persistBooking(LocalDateTime.of(2026, 6, 5, 14, 0),
                LocalDateTime.of(2026, 6, 5, 15, 0), BookingStatus.CANCELLED);
        // 存在一個預約成功單 2026/6/5 14:00–15:00. 狀態是 EXPIRED(沒報到)
        persistBooking(LocalDateTime.of(2026, 6, 5, 14, 0),
                LocalDateTime.of(2026, 6, 5, 15, 0), BookingStatus.EXPIRED);

        // 新增一個預約單預約 2026/6/5 14:00–15:00，預期是空的可以預約
        assertThat(bookingRepository.findOverlapping(roomA.getId(),
                LocalDateTime.of(2026, 6, 5, 14, 0),
                LocalDateTime.of(2026, 6, 5, 15, 0),
                BookingStatus.activeStatuses())).isEmpty();
    }

    // 確定沒有跟自己以外的人撞時間
    @Test
    void findOverlappingExcluding_skipsTheBookingItself() {
        // 一筆預約單，2026/6/5 14:00–15:00，狀態是 LOCKING(預約還沒確定預約)
        Booking self = persistBooking(LocalDateTime.of(2026, 6, 5, 14, 0),
                LocalDateTime.of(2026, 6, 5, 15, 0), BookingStatus.LOCKING);

        // 確定 14:00~15:00 會議室 A 沒有人預約
        assertThat(bookingRepository.findOverlappingExcluding(roomA.getId(),
                self.getStartTime(), self.getEndTime(),
                BookingStatus.activeStatuses(), self.getId())).isEmpty();
    }

    // 把資料庫中過期的預約單撈出
    @Test
    void findExpiredLocks_returnsOnlyTimedOutLocks() {
        // 一筆預約單，2026/6/5 16:00–16:30，狀態是 LOCKING(預約還沒確定預約)
        Booking stale = persistBooking(LocalDateTime.of(2026, 6, 5, 16, 0),
                LocalDateTime.of(2026, 6, 5, 16, 30), BookingStatus.LOCKING);
        // 設定截止時間是 2026/6/5 09:00
        stale.setLockExpiresAt(LocalDateTime.of(2026, 6, 5, 9, 0));
        // 呼叫 save 更新資料庫
        bookingRepository.save(stale);

        // 09:01 findExpiredLocks 尋找截止的預約
        List<Booking> expired = bookingRepository.findExpiredLocks(LocalDateTime.of(2026, 6, 5, 9, 1));

        // 是否有抓到 stale 這個預約單
        assertThat(expired).extracting(Booking::getId).containsExactly(stale.getId());
    }

    // 把資料庫中沒報到的預約單撈出，沒有抓到已報到的
    @Test
    void findNoShows_returnsBookedStartedBeyondThreshold() {
        // 一筆預約單，2026/6/5 09:00–09:30，狀態是 BOOKED(預約還沒確定預約)
        Booking noShow = persistBooking(LocalDateTime.of(2026, 6, 5, 9, 0),
                LocalDateTime.of(2026, 6, 5, 9, 30), BookingStatus.BOOKED);
        // 一筆預約單，2026/6/5 09:00–09:30，狀態是 CHECKED_IN
        persistBooking(LocalDateTime.of(2026, 6, 5, 9, 0),
                LocalDateTime.of(2026, 6, 5, 9, 30), BookingStatus.CHECKED_IN);
        // 排程器 9:16 看是否有未報到的
        List<Booking> result = bookingRepository.findNoShows(LocalDateTime.of(2026, 6, 5, 9, 16));
        
        // 是否有抓到 noShow 這個預約單
        assertThat(result).extracting(Booking::getId).containsExactly(noShow.getId());
    }
}
