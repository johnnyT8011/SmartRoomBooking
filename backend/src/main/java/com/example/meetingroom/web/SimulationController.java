package com.example.meetingroom.web;

import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.meetingroom.config.MutableClock;
import com.example.meetingroom.service.BookingScheduler;

import java.time.Duration;

/**
 * 時空模擬控制台 (demo-only) — lets the UI fast-forward / rewind / jump the system clock so
 * the 5-minute lock release and 15-minute no-show release can be demonstrated instantly
 * instead of waiting in real time.
 *
 * <p>After every time change the scheduler scans are run immediately, so any locks/no-shows
 * that just became due are released and pushed over SSE right away. This endpoint group is
 * an aid for demos and is not part of the production booking contract.</p>
 */
@RestController
@RequestMapping("/api/sim")
public class SimulationController {

    private final MutableClock clock;
    private final BookingScheduler scheduler;

    public SimulationController(MutableClock clock, BookingScheduler scheduler) {
        this.clock = clock;
        this.scheduler = scheduler;
    }

    public record TimeResponse(String now, boolean simulated, long offsetSeconds) { }

    @GetMapping("/time")
    public TimeResponse time() {
        return currentTime();
    }

    /** Shift the clock by {@code minutes} (negative = rewind), then release due bookings. */
    @PostMapping("/advance")
    public TimeResponse advance(@RequestParam long minutes) {
        clock.advance(Duration.ofMinutes(minutes));
        runScans();
        return currentTime();
    }

    /** Jump to an absolute date-time, then release due bookings. */
    @PostMapping("/jump")
    public TimeResponse jump(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        clock.jumpTo(to.atZone(clock.getZone()).toInstant());
        runScans();
        return currentTime();
    }

    /** Return the clock to real wall-clock time. */
    @PostMapping("/reset")
    public TimeResponse reset() {
        clock.reset();
        runScans();
        return currentTime();
    }

    private void runScans() {
        scheduler.releaseExpiredLocks();
        scheduler.releaseNoShows();
    }

    private TimeResponse currentTime() {
        return new TimeResponse(
                LocalDateTime.now(clock).toString(),
                clock.isSimulated(),
                clock.getOffset().getSeconds());
    }
}
