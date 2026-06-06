package com.example.meetingroom.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.meetingroom.domain.Booking;
import com.example.meetingroom.domain.BookingStatus;

/**
 * Data access for {@link Booking}, including the core anti-overlap query.
 */
public interface BookingRepository extends JpaRepository<Booking, Long> {

    /**
     * Core anti-overlap query. Returns active bookings for a room whose time range
     * intersects {@code [start, end)}.
     *
     * <p>Two half-open intervals {@code [aStart, aEnd)} and {@code [bStart, bEnd)} overlap
     * iff {@code aStart < bEnd AND aEnd > bStart}. Touching edges (e.g. 09:00-10:00 and
     * 10:00-11:00) do <b>not</b> count as an overlap, which is the desired behaviour for
     * back-to-back bookings.</p>
     */
    @Query("""
            SELECT b FROM Booking b
            WHERE b.room.id = :roomId
              AND b.status IN :activeStatuses
              AND b.startTime < :endTime
              AND b.endTime > :startTime
            """)
    List<Booking> findOverlapping(@Param("roomId") Long roomId,
                                  @Param("startTime") LocalDateTime startTime,
                                  @Param("endTime") LocalDateTime endTime,
                                  @Param("activeStatuses") Collection<BookingStatus> activeStatuses);

    /**
     * Same as {@link #findOverlapping} but ignores one booking (its own id). Used by
     * {@code confirmBooking} to re-validate the slot without colliding with its own lock.
     */
    @Query("""
            SELECT b FROM Booking b
            WHERE b.room.id = :roomId
              AND b.id <> :excludeId
              AND b.status IN :activeStatuses
              AND b.startTime < :endTime
              AND b.endTime > :startTime
            """)
    List<Booking> findOverlappingExcluding(@Param("roomId") Long roomId,
                                           @Param("startTime") LocalDateTime startTime,
                                           @Param("endTime") LocalDateTime endTime,
                                           @Param("activeStatuses") Collection<BookingStatus> activeStatuses,
                                           @Param("excludeId") Long excludeId);

    /**
     * Active bookings for a given user that intersect {@code [start, end)}. Used to stop the
     * same employee double-booking themselves across rooms.
     */
    @Query("""
            SELECT b FROM Booking b
            WHERE b.user.id = :userId
              AND b.status IN :activeStatuses
              AND b.startTime < :endTime
              AND b.endTime > :startTime
            """)
    List<Booking> findUserOverlapping(@Param("userId") Long userId,
                                      @Param("startTime") LocalDateTime startTime,
                                      @Param("endTime") LocalDateTime endTime,
                                      @Param("activeStatuses") Collection<BookingStatus> activeStatuses);

    /** LOCKING bookings whose 5-minute hold has expired ({@code lockExpiresAt < now}). */
    @Query("SELECT b FROM Booking b WHERE b.status = com.example.meetingroom.domain.BookingStatus.LOCKING AND b.lockExpiresAt < :now")
    List<Booking> findExpiredLocks(@Param("now") LocalDateTime now);

    /**
     * BOOKED reservations that started at or before {@code threshold} (= now - 15 min) and
     * were never checked in — i.e. no-shows ready to be released.
     */
    @Query("SELECT b FROM Booking b WHERE b.status = com.example.meetingroom.domain.BookingStatus.BOOKED AND b.startTime <= :threshold")
    List<Booking> findNoShows(@Param("threshold") LocalDateTime threshold);

    /**
     * Bookings intersecting a date range, regardless of status, with {@code user} and
     * {@code room} eagerly join-fetched so they can be mapped to DTOs after the transaction
     * closes (open-in-view is disabled). Used for the weekly schedule view.
     */
    @Query("""
            SELECT b FROM Booking b
            JOIN FETCH b.user
            JOIN FETCH b.room
            WHERE b.startTime < :rangeEnd
              AND b.endTime > :rangeStart
            ORDER BY b.startTime ASC
            """)
    List<Booking> findInRangeWithDetails(@Param("rangeStart") LocalDateTime rangeStart,
                                         @Param("rangeEnd") LocalDateTime rangeEnd);

    /**
     * Load a single booking with its {@code user} and {@code room} eagerly fetched, so the
     * result can be mapped to a DTO after the transaction closes (open-in-view is disabled).
     */
    @Query("SELECT b FROM Booking b JOIN FETCH b.user JOIN FETCH b.room WHERE b.id = :id")
    Optional<Booking> findDetailedById(@Param("id") Long id);
}
