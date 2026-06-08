package com.example.meetingroom.service;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

import com.example.meetingroom.domain.BookingStatus;
import com.example.meetingroom.domain.TimeRange;
import com.example.meetingroom.domain.User;
import com.example.meetingroom.exception.BookingConflictException;
import com.example.meetingroom.exception.InvalidBookingException;
import com.example.meetingroom.repository.BookingRepository;

import java.time.Clock;

/**
 * Stateless validator for the "防呆" (idiot-proofing) booking rules.
 *
 * <p>[Refactor #1 Data clumps / #2 Long parameter list] All rule methods now take a single
 * {@link TimeRange} instead of a loose {@code (startTime, endTime)} pair, and the
 * duration / 30-minute-alignment arithmetic is delegated to {@code TimeRange}.</p>
 *
 * <ul>
 *   <li>{@link #validateTimeBlock} — start/end must align to 30-minute boundaries.</li>
 *   <li>{@link #validateDuration} — at least 30 minutes, at most 4 hours.</li>
 *   <li>{@link #validateBookingWindow} — not in the past, within the next 7 days.</li>
 *   <li>{@link #checkUserConflict} — the same user may not double-book themselves.</li>
 * </ul>
 */
@Component
public class BookingRuleValidator {

    static final int SLOT_MINUTES = 30;
    static final int MIN_DURATION_MINUTES = 30;
    static final int MAX_DURATION_MINUTES = 4 * 60;
    static final int BOOKING_WINDOW_DAYS = 7;

    static final String MSG_NOT_30_BLOCK = "預約時間必須為 30 分鐘的倍數";
    static final String MSG_END_BEFORE_START = "結束時間必須晚於開始時間";
    static final String MSG_MIN_DURATION = "單次預約最短需 30 分鐘";
    static final String MSG_MAX_DURATION = "單次預約不可超過 4 小時";
    static final String MSG_PAST = "無法預約過去的時間";
    static final String MSG_WINDOW = "僅開放預約未來 7 天內的會議室";
    static final String MSG_USER_CONFLICT = "您在該時段已有其他預約，無法重複預約";

    private final BookingRepository bookingRepository;
    private final Clock clock;

    public BookingRuleValidator(BookingRepository bookingRepository, Clock clock) {
        this.bookingRepository = bookingRepository;
        this.clock = clock;
    }

    /** Run every rule. Convenience entry point used by {@link BookingService#lockRoom}. */
    public void validateAll(User user, TimeRange range) {
        validateTimeBlock(range);
        validateDuration(range);
        validateBookingWindow(range);
        checkUserConflict(user, range);
    }

    /** Both endpoints must fall exactly on a 00 or 30 minute mark (no stray seconds/nanos). */
    public void validateTimeBlock(TimeRange range) {
        if (!range.isAlignedTo(SLOT_MINUTES)) {
            throw new InvalidBookingException(MSG_NOT_30_BLOCK);
        }
    }

    /** 30 minutes &le; duration &le; 4 hours, and end strictly after start. */
    public void validateDuration(TimeRange range) {
        if (!range.endsAfterStart()) {
            throw new InvalidBookingException(MSG_END_BEFORE_START);
        }
        long minutes = range.durationMinutes(clock.getZone());
        if (minutes < MIN_DURATION_MINUTES) {
            throw new InvalidBookingException(MSG_MIN_DURATION);
        }
        if (minutes > MAX_DURATION_MINUTES) {
            throw new InvalidBookingException(MSG_MAX_DURATION);
        }
    }

    /** Start must not be in the past and must be within {@value #BOOKING_WINDOW_DAYS} days. */
    public void validateBookingWindow(TimeRange range) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (range.getStart().isBefore(now)) {
            throw new InvalidBookingException(MSG_PAST);
        }
        if (range.getStart().isAfter(now.plusDays(BOOKING_WINDOW_DAYS))) {
            throw new InvalidBookingException(MSG_WINDOW);
        }
    }

    /** Reject if this user already holds an active booking overlapping the requested slot. */
    public void checkUserConflict(User user, TimeRange range) {
        boolean clash = !bookingRepository
                .findUserOverlapping(user.getId(), range, BookingStatus.activeStatuses())
                .isEmpty();
        if (clash) {
            throw new BookingConflictException(MSG_USER_CONFLICT);
        }
    }
}
