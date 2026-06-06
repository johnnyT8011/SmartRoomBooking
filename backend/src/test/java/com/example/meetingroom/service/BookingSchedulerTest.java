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

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 5, 9, 20);

    @Mock private BookingRepository bookingRepository;
    @Mock private NotificationService notificationService;

    private BookingScheduler scheduler;
    private User user;
    private MeetingRoom room;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        scheduler = new BookingScheduler(bookingRepository, notificationService, clock);
        user = new User("員工甲", "a@example.com");
        ReflectionTestUtils.setField(user, "id", 1L);
        room = new MeetingRoom("會議室 B");
    }

    @Test
    void releaseExpiredLocks_marksExpired_andNotifies() {
        Booking stale = new Booking(user, room,
                LocalDateTime.of(2026, 6, 5, 11, 0),
                LocalDateTime.of(2026, 6, 5, 11, 30), BookingStatus.LOCKING);
        stale.setLockExpiresAt(NOW.minusMinutes(1));
        given(bookingRepository.findExpiredLocks(NOW)).willReturn(List.of(stale));

        scheduler.releaseExpiredLocks();

        assertThat(stale.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        verify(notificationService).sendSseEvent(eq(1L), any());
    }

    @Test
    void releaseNoShows_usesNowMinus15Minutes_andReleases() {
        // Booking started at 09:00; now is 09:20 → past the 15-minute grace (Scenario 4).
        Booking noShow = new Booking(user, room,
                LocalDateTime.of(2026, 6, 5, 9, 0),
                LocalDateTime.of(2026, 6, 5, 9, 30), BookingStatus.BOOKED);
        given(bookingRepository.findNoShows(any())).willReturn(List.of(noShow));

        scheduler.releaseNoShows();

        ArgumentCaptor<LocalDateTime> thresholdCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(bookingRepository).findNoShows(thresholdCaptor.capture());
        assertThat(thresholdCaptor.getValue()).isEqualTo(NOW.minusMinutes(15));

        assertThat(noShow.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        verify(notificationService).sendSseEvent(eq(1L), any());
    }
}
