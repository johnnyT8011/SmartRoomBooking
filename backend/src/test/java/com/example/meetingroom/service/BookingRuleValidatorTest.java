package com.example.meetingroom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.meetingroom.domain.Booking;
import com.example.meetingroom.domain.BookingStatus;
import com.example.meetingroom.domain.MeetingRoom;
import com.example.meetingroom.domain.User;
import com.example.meetingroom.exception.BookingConflictException;
import com.example.meetingroom.exception.InvalidBookingException;
import com.example.meetingroom.repository.BookingRepository;

/**
 * Boundary tests for {@link BookingRuleValidator}, driven by a fixed clock at
 * 2026-06-03 09:00 (mirroring Scenario 8's system date).
 */
@ExtendWith(MockitoExtension.class)
class BookingRuleValidatorTest {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 3, 9, 0);

    @Mock
    private BookingRepository bookingRepository;

    private BookingRuleValidator validator;
    private final User user = new User("員工甲", "a@example.com");

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        validator = new BookingRuleValidator(bookingRepository, clock);
        ReflectionTestUtils.setField(user, "id", 1L);
    }

    // ---- validateTimeBlock (Scenario 6) ----

    @Test
    void timeBlock_rejectsNon30MinuteBoundary() {
        assertThatThrownBy(() -> validator.validateTimeBlock(
                LocalDateTime.of(2026, 6, 3, 10, 15),
                LocalDateTime.of(2026, 6, 3, 10, 45)))
                .isInstanceOf(InvalidBookingException.class)
                .hasMessageContaining("30 分鐘");
    }

    @Test
    void timeBlock_acceptsAlignedTimes() {
        assertThatCode(() -> validator.validateTimeBlock(
                LocalDateTime.of(2026, 6, 3, 10, 0),
                LocalDateTime.of(2026, 6, 3, 10, 30)))
                .doesNotThrowAnyException();
    }

    // ---- validateDuration (Scenario 7) ----

    @Test
    void duration_rejectsOverFourHours() {
        assertThatThrownBy(() -> validator.validateDuration(
                LocalDateTime.of(2026, 6, 3, 9, 0),
                LocalDateTime.of(2026, 6, 3, 14, 0)))
                .isInstanceOf(InvalidBookingException.class)
                .hasMessageContaining("4 小時");
    }

    @Test
    void duration_rejectsUnder30Minutes() {
        assertThatThrownBy(() -> validator.validateDuration(
                LocalDateTime.of(2026, 6, 3, 9, 0),
                LocalDateTime.of(2026, 6, 3, 9, 15)))
                .isInstanceOf(InvalidBookingException.class);
    }

    @Test
    void duration_rejectsEndBeforeStart() {
        assertThatThrownBy(() -> validator.validateDuration(
                LocalDateTime.of(2026, 6, 3, 11, 0),
                LocalDateTime.of(2026, 6, 3, 10, 0)))
                .isInstanceOf(InvalidBookingException.class);
    }

    @Test
    void duration_acceptsExactlyFourHours() {
        assertThatCode(() -> validator.validateDuration(
                LocalDateTime.of(2026, 6, 3, 9, 0),
                LocalDateTime.of(2026, 6, 3, 13, 0)))
                .doesNotThrowAnyException();
    }

    // ---- validateBookingWindow (Scenario 8) ----

    @Test
    void window_rejectsBeyond7Days() {
        assertThatThrownBy(() -> validator.validateBookingWindow(
                LocalDateTime.of(2026, 6, 15, 10, 0),
                LocalDateTime.of(2026, 6, 15, 11, 0)))
                .isInstanceOf(InvalidBookingException.class)
                .hasMessageContaining("7 天");
    }

    @Test
    void window_rejectsPast() {
        assertThatThrownBy(() -> validator.validateBookingWindow(
                LocalDateTime.of(2026, 6, 1, 10, 0),
                LocalDateTime.of(2026, 6, 1, 11, 0)))
                .isInstanceOf(InvalidBookingException.class)
                .hasMessageContaining("過去");
    }

    @Test
    void window_acceptsWithin7Days() {
        assertThatCode(() -> validator.validateBookingWindow(
                LocalDateTime.of(2026, 6, 5, 10, 0),
                LocalDateTime.of(2026, 6, 5, 11, 0)))
                .doesNotThrowAnyException();
    }

    // ---- checkUserConflict ----

    @Test
    void userConflict_throwsWhenUserHasOverlap() {
        Booking existing = new Booking(user, new MeetingRoom("會議室 A"),
                LocalDateTime.of(2026, 6, 5, 10, 0),
                LocalDateTime.of(2026, 6, 5, 11, 0), BookingStatus.BOOKED);
        given(bookingRepository.findUserOverlapping(anyLong(), any(), any(), any()))
                .willReturn(List.of(existing));

        assertThatThrownBy(() -> validator.checkUserConflict(user,
                LocalDateTime.of(2026, 6, 5, 10, 30),
                LocalDateTime.of(2026, 6, 5, 11, 30)))
                .isInstanceOf(BookingConflictException.class);
    }

    @Test
    void userConflict_passesWhenNoOverlap() {
        given(bookingRepository.findUserOverlapping(anyLong(), any(), any(), any()))
                .willReturn(List.of());

        assertThatCode(() -> validator.checkUserConflict(user,
                LocalDateTime.of(2026, 6, 5, 10, 0),
                LocalDateTime.of(2026, 6, 5, 11, 0)))
                .doesNotThrowAnyException();
    }

    @Test
    void slotConstants_areAsSpecified() {
        assertThat(BookingRuleValidator.MIN_DURATION_MINUTES).isEqualTo(30);
        assertThat(BookingRuleValidator.MAX_DURATION_MINUTES).isEqualTo(240);
        assertThat(BookingRuleValidator.BOOKING_WINDOW_DAYS).isEqualTo(7);
    }
}
