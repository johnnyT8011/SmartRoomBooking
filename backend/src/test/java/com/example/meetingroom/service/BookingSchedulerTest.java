package com.example.meetingroom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.meetingroom.domain.Booking;
import com.example.meetingroom.domain.BookingStatus;
import com.example.meetingroom.domain.MeetingRoom;
import com.example.meetingroom.domain.User;
import com.example.meetingroom.repository.BookingRepository;

/**
 * Verifies the two scheduled jobs: 5-minute lock release and 15-minute no-show release.
 */
@ExtendWith(MockitoExtension.class)
class BookingSchedulerTest {
    // 時間殘根，預設是2026/6/5 9:20
    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 5, 9, 20);

    @Mock private BookingRepository bookingRepository;
    @Mock private NotificationService notificationService;

    private BookingScheduler scheduler;
    private User user;
    private MeetingRoom room;

    @BeforeEach
    void setUp() {
        // 假時鐘 2026/6/5 9:20
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        // new 一個自動排程器
        scheduler = new BookingScheduler(bookingRepository, notificationService, clock);
        // new 一個 User 叫做員工甲
        user = new User("員工甲", "a@example.com");
        // 強制設他的 id 是 1L
        ReflectionTestUtils.setField(user, "id", 1L);
        // new 一個 MeetingRoom 叫會議室 B
        room = new MeetingRoom("會議室 B");
    }

    // 情境10：超過確認預約時間(5分鐘)
    @Test
    void releaseExpiredLocks_marksExpired_andNotifies() {
        // 建立一筆 LOCKING 的預約，2026/6/5 11:00~11:30
        Booking stale = new Booking(user, room,
                new com.example.meetingroom.domain.TimeRange(LocalDateTime.of(2026, 6, 5, 11, 0),
                LocalDateTime.of(2026, 6, 5, 11, 30)), BookingStatus.LOCKING);
        // 確定截止時間是 09:20 - 1分鐘，現在是 09:20
        stale.setLockExpiresAt(NOW.minusMinutes(1));
        // 用 findExpiredLocks 找已經超過時間的預約，回傳 stale
        given(bookingRepository.findExpiredLocks(NOW)).willReturn(List.of(stale));
        
        // 觸發排程器清理
        scheduler.releaseExpiredLocks();

        // 這比預約需從 LOCKING 變成 EXPIRED
        assertThat(stale.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        // 驗證排程器有傳送網頁 SSE 訊息通知該使用者
        verify(notificationService).sendSseEvent(eq(1L), any());
    }

    // 開會時間開始後超過 20 分鐘未報到
    @Test
    void releaseNoShows_usesNowMinus15Minutes_andReleases() {
        // 建立一筆 BOOKED 確定預約，2026/6/5 09:00~09:30，現在是 09:20
        Booking noShow = new Booking(user, room,
                new com.example.meetingroom.domain.TimeRange(LocalDateTime.of(2026, 6, 5, 9, 0),
                LocalDateTime.of(2026, 6, 5, 9, 30)), BookingStatus.BOOKED);
        // 用 findNoShows 找已經超過報到時間( 15 分鐘)回傳 noShow
        given(bookingRepository.findNoShows(any())).willReturn(List.of(noShow));
        
        // 觸發排程器清理
        scheduler.releaseNoShows();

        // 宣告一個 LocalDateTime 的專用捕獲器 thresholdCaptor
        ArgumentCaptor<LocalDateTime> thresholdCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        // 驗證有呼叫 findNoShows
        verify(bookingRepository).findNoShows(thresholdCaptor.capture());
        // 驗證現在時間 -15 是不是跟我們所想的是一樣的(9:05)
        assertThat(thresholdCaptor.getValue()).isEqualTo(NOW.minusMinutes(15));
        
        // 這比確定預約需從 BOOKED 變成 EXPIRED
        assertThat(noShow.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        // 驗證排程器有傳送網頁 SSE 訊息通知該使用者
        verify(notificationService).sendSseEvent(eq(1L), any());
    }
}
