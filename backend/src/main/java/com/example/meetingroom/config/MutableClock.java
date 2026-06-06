package com.example.meetingroom.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A {@link Clock} that keeps ticking in real time but can be shifted by an offset, so the
 * whole application (validator, service timestamps, scheduler) can be "time-travelled" for
 * demos. Offset 0 == real time. {@code advance}/{@code jumpTo} change the offset; the clock
 * still advances second-by-second from there.
 *
 * <p>Demo aid only — not intended for production use.</p>
 */
public class MutableClock extends Clock {

    private final ZoneId zone;
    private final AtomicReference<Duration> offset = new AtomicReference<>(Duration.ZERO);

    public MutableClock(ZoneId zone) {
        this.zone = zone;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        MutableClock copy = new MutableClock(newZone);
        copy.offset.set(this.offset.get());
        return copy;
    }

    @Override
    public Instant instant() {
        return Instant.now().plus(offset.get());
    }

    /** Shift the simulated clock by a (possibly negative) duration. */
    public void advance(Duration delta) {
        offset.updateAndGet(current -> current.plus(delta));
    }

    /** Set the simulated clock to an absolute instant. */
    public void jumpTo(Instant target) {
        offset.set(Duration.between(Instant.now(), target));
    }

    /** Return to real wall-clock time. */
    public void reset() {
        offset.set(Duration.ZERO);
    }

    public Duration getOffset() {
        return offset.get();
    }

    public boolean isSimulated() {
        return !offset.get().isZero();
    }
}
