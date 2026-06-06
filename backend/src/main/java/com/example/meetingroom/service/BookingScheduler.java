package com.example.meetingroom.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.meetingroom.domain.Booking;
import com.example.meetingroom.domain.BookingStatus;
import com.example.meetingroom.repository.BookingRepository;

/**
 * Periodic housekeeping. Uses Spring's built-in {@code @Scheduled} (no external scheduler,
 * no message queue) to release abandoned locks and no-shows.
 */
@Component
public class BookingScheduler {

    private static final Logger log = LoggerFactory.getLogger(BookingScheduler.class);

    /** A BOOKED reservation must be checked in within 15 minutes of its start time. */
    static final long NO_SHOW_GRACE_MINUTES = 15;

    private final BookingRepository bookingRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    public BookingScheduler(BookingRepository bookingRepository,
                            NotificationService notificationService,
                            Clock clock) {
        this.bookingRepository = bookingRepository;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    /**
     * Release LOCKING holds whose 5-minute window has elapsed without confirmation,
     * returning the slot to the available pool.
     */
    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void releaseExpiredLocks() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Booking> expired = bookingRepository.findExpiredLocks(now);
        for (Booking booking : expired) {
            booking.setStatus(BookingStatus.EXPIRED);
            notificationService.sendSseEvent(booking.getUser().getId(),
                    "預約鎖逾時未確認，" + booking.getRoom().getRoomName() + " 時段已自動釋放");
        }
        if (!expired.isEmpty()) {
            log.info("Released {} expired lock(s)", expired.size());
        }
    }

    /**
     * Force-cancel BOOKED reservations whose start time passed more than 15 minutes ago
     * with no check-in (CHECKED_IN reservations are a different status and are never
     * selected), and free the room.
     */
    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void releaseNoShows() {
        LocalDateTime threshold = LocalDateTime.now(clock).minusMinutes(NO_SHOW_GRACE_MINUTES);
        List<Booking> noShows = bookingRepository.findNoShows(threshold);
        for (Booking booking : noShows) {
            booking.setStatus(BookingStatus.EXPIRED);
            notificationService.sendSseEvent(booking.getUser().getId(),
                    "您預約的 " + booking.getRoom().getRoomName() + " 因超過 "
                            + NO_SHOW_GRACE_MINUTES + " 分鐘未報到，已被取消並釋放時段");
        }
        if (!noShows.isEmpty()) {
            log.info("Released {} no-show booking(s)", noShows.size());
        }
    }
}
