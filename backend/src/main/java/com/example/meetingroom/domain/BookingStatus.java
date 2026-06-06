package com.example.meetingroom.domain;

import java.util.List;

/**
 * Lifecycle states of a {@link Booking}.
 *
 * <pre>
 *   lockRoom()            confirmBooking()        checkIn()
 *   ---------> LOCKING ----------------> BOOKED ----------> CHECKED_IN
 *                 |                         |
 *   lock expires  |  (5 min)                | no-show (15 min) / cancel
 *                 v                         v
 *              EXPIRED                  EXPIRED / CANCELLED
 * </pre>
 *
 * <p>{@code LOCKING}, {@code BOOKED} and {@code CHECKED_IN} are the "active" states that
 * occupy a time slot and therefore participate in overlap detection.</p>
 */
public enum BookingStatus {

    /** A short-lived (5 minute) hold created the moment a user submits a booking request. */
    LOCKING,

    /** A confirmed reservation. */
    BOOKED,

    /** The borrower has checked in; the reservation is safe from no-show release. */
    CHECKED_IN,

    /** The user actively cancelled. Record is kept for auditing. */
    CANCELLED,

    /** Released automatically: lock timed out, or BOOKED but no-show after 15 minutes. */
    EXPIRED;

    /**
     * The states that occupy a time slot and therefore participate in overlap detection.
     * {@code CANCELLED} and {@code EXPIRED} free the slot and are excluded.
     */
    public static List<BookingStatus> activeStatuses() {
        return List.of(LOCKING, BOOKED, CHECKED_IN);
    }
}
