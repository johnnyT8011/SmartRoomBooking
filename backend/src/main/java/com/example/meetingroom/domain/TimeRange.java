package com.example.meetingroom.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * [Refactor #1 Data clumps / #2 Long parameter list — Introduce Parameter Object]
 *
 * <p>The {@code (startTime, endTime)} pair previously travelled together as a data clump
 * through the validator, service and repository layers, and the time-interval rules
 * (duration, 30-minute alignment) were computed inline by each caller. This value object
 * carries the pair as a single argument and owns those rules, so callers ask the range
 * questions instead of pulling its two fields apart.</p>
 *
 * <p>Note: the actual overlap detection stays in {@code BookingRepository}'s JPQL, because
 * it is a set query evaluated by the database, not in-memory Java.</p>
 */
public class TimeRange {

    private final LocalDateTime start;
    private final LocalDateTime end;

    public TimeRange(LocalDateTime start, LocalDateTime end) {
        this.start = start;
        this.end = end;
    }

    public LocalDateTime getStart() {
        return start;
    }

    public LocalDateTime getEnd() {
        return end;
    }

    /** Whole minutes between start and end using the specified timezone. */
    public long durationMinutes(ZoneId zoneId) {
        return Duration.between(
                start.atZone(zoneId),
                end.atZone(zoneId)
        ).toMinutes();
    }


    /** True when end is strictly after start. */
    public boolean endsAfterStart() {
        return end.isAfter(start);
    }

    /** True when both endpoints fall exactly on a {@code slotMinutes} boundary (no stray seconds/nanos). */
    public boolean isAlignedTo(int slotMinutes) {
        return aligned(start, slotMinutes) && aligned(end, slotMinutes);
    }

    private static boolean aligned(LocalDateTime t, int slotMinutes) {
        return t.getMinute() % slotMinutes == 0 && t.getSecond() == 0 && t.getNano() == 0;
    }
}
