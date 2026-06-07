package com.example.meetingroom.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.meetingroom.domain.Booking;
import com.example.meetingroom.domain.BookingStatus;
import com.example.meetingroom.domain.MeetingRoom;
import com.example.meetingroom.domain.TimeRange;
import com.example.meetingroom.domain.User;
import com.example.meetingroom.exception.BookingConflictException;
import com.example.meetingroom.exception.BookingNotFoundException;
import com.example.meetingroom.exception.InvalidBookingException;
import com.example.meetingroom.exception.MeetingRoomNotFoundException;
import com.example.meetingroom.exception.UserNotFoundException;
import com.example.meetingroom.repository.BookingRepository;
import com.example.meetingroom.repository.MeetingRoomRepository;
import com.example.meetingroom.repository.UserRepository;

/**
 * Core booking service: the "brain" implementing the lock → confirm → check-in lifecycle
 * plus cancellation, all guarded for transactional consistency and overlap safety.
 */
@Service
public class BookingService {

    /** A booking lock is held for 5 minutes before the scheduler may release it. */
    static final long LOCK_MINUTES = 5;

    static final String MSG_OVERLAP = "時段重疊，預約失敗";
    static final String MSG_NOT_LOCKING = "僅有鎖定中 (LOCKING) 的預約可被確認";
    static final String MSG_CANNOT_CANCEL = "此預約狀態無法取消";

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final MeetingRoomRepository meetingRoomRepository;
    private final BookingRuleValidator validator;
    private final NotificationService notificationService;
    private final Clock clock;

    public BookingService(BookingRepository bookingRepository,
                          UserRepository userRepository,
                          MeetingRoomRepository meetingRoomRepository,
                          BookingRuleValidator validator,
                          NotificationService notificationService,
                          Clock clock) {
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.meetingRoomRepository = meetingRoomRepository;
        this.validator = validator;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    /**
     * Create a 5-minute LOCKING hold. Only invoked when the user explicitly submits a
     * booking request (never on page load), so an idle visitor never occupies a slot.
     *
     * <p>The room row is fetched with a pessimistic write lock, which serializes concurrent
     * requests for the same room and guarantees at most one lock survives — the second
     * caller blocks, then sees the overlap and is rejected.</p>
     */
    @Transactional
    public Booking lockRoom(Long userId, Long roomId, LocalDateTime start, LocalDateTime end) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        // Pessimistic lock on the room serializes concurrent booking attempts (Scenario 3).
        MeetingRoom room = meetingRoomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new MeetingRoomNotFoundException(roomId));

        // [Refactor #1/#2] Pack the loose start/end pair (arriving from the HTTP request) into a
        // single TimeRange so the validator and overlap check take one argument, not two.
        TimeRange range = new TimeRange(start, end);
        validator.validateAll(user, range);
        ensureNoRoomOverlap(roomId, range, null);

        LocalDateTime now = LocalDateTime.now(clock);
        Booking booking = new Booking(user, room, start, end, BookingStatus.LOCKING);
        booking.setLockedAt(now);
        booking.setLockExpiresAt(now.plusMinutes(LOCK_MINUTES));
        Booking saved = bookingRepository.save(booking);

        notificationService.sendSseEvent(userId,
                "已鎖定 " + room.getRoomName() + "，請於 " + LOCK_MINUTES + " 分鐘內確認預約");
        return saved;
    }

    /**
     * Promote a LOCKING hold to a confirmed BOOKED reservation, re-checking that no other
     * active booking grabbed the slot in the meantime.
     */
    @Transactional
    public Booking confirmBooking(Long bookingId) {
        Booking booking = bookingRepository.findDetailedById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        if (booking.getStatus() != BookingStatus.LOCKING) {
            throw new InvalidBookingException(MSG_NOT_LOCKING);
        }
        // [Refactor #1/#4] booking.getRoomId() (Hide Delegate) + TimeRange parameter object.
        ensureNoRoomOverlap(booking.getRoomId(),
                new TimeRange(booking.getStartTime(), booking.getEndTime()), booking.getId());

        booking.setStatus(BookingStatus.BOOKED);
        notificationService.sendSseEvent(booking.getUserId(),
                "預約成功：" + booking.getRoomName());
        return booking;
    }

    /**
     * User-initiated cancellation. The record is kept (status → CANCELLED) for auditing
     * rather than deleted, and the slot becomes free again.
     */
    @Transactional
    public Booking cancelBooking(Long bookingId) {
        Booking booking = bookingRepository.findDetailedById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        if (booking.getStatus() == BookingStatus.CANCELLED
                || booking.getStatus() == BookingStatus.EXPIRED) {
            throw new InvalidBookingException(MSG_CANNOT_CANCEL);
        }
        booking.setStatus(BookingStatus.CANCELLED);
        // [Refactor #4 Hide Delegate]
        notificationService.sendSseEvent(booking.getUserId(),
                "已取消預約：" + booking.getRoomName() + "，時段已釋放");
        return booking;
    }

    /**
     * Mark a reservation as checked in so the no-show scheduler ignores it afterwards.
     */
    @Transactional
    public Booking checkIn(Long bookingId) {
        Booking booking = bookingRepository.findDetailedById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        if (booking.getStatus() != BookingStatus.BOOKED) {
            throw new InvalidBookingException("僅有已預約 (BOOKED) 的紀錄可以報到");
        }
        booking.setStatus(BookingStatus.CHECKED_IN);
        booking.setCheckedInAt(LocalDateTime.now(clock));
        // [Refactor #4 Hide Delegate]
        notificationService.sendSseEvent(booking.getUserId(),
                "報到成功：" + booking.getRoomName());
        return booking;
    }

    /**
     * Bookings occupying any slot in the current Monday–Sunday week, with associations
     * initialized so the caller can safely map them to DTOs.
     */
    @Transactional(readOnly = true)
    public List<Booking> getWeeklySchedule() {
        LocalDateTime weekStart = LocalDateTime.now(clock)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .toLocalDate().atStartOfDay();
        return bookingRepository.findInRangeWithDetails(weekStart, weekStart.plusDays(7));
    }

    /** Bookings occupying any slot within an arbitrary {@code [from, to)} range. */
    @Transactional(readOnly = true)
    public List<Booking> getSchedule(LocalDateTime from, LocalDateTime to) {
        return bookingRepository.findInRangeWithDetails(from, to);
    }

    // [Refactor #1/#2] Takes a TimeRange instead of a separate (start, end) pair.
    private void ensureNoRoomOverlap(Long roomId, TimeRange range, Long excludeId) {
        List<Booking> overlaps = (excludeId == null)
                ? bookingRepository.findOverlapping(roomId, range.getStart(), range.getEnd(),
                        BookingStatus.activeStatuses())
                : bookingRepository.findOverlappingExcluding(roomId, range.getStart(), range.getEnd(),
                        BookingStatus.activeStatuses(), excludeId);
        if (!overlaps.isEmpty()) {
            throw new BookingConflictException(MSG_OVERLAP);
        }
    }
}
