package com.example.meetingroom.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MutableClockTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final ZoneId ASIA_TAIPEI = ZoneId.of("Asia/Taipei");

    @Test
    void testInitialState() {
        MutableClock clock = new MutableClock(UTC);
        assertThat(clock.getZone()).isEqualTo(UTC);
        assertThat(clock.isSimulated()).isFalse();
        assertThat(clock.getOffset()).isEqualTo(Duration.ZERO);
        assertThat(clock.instant()).isCloseTo(Instant.now(), within(100, ChronoUnit.MILLIS));
    }

    @Test
    void testWithZone() {
        MutableClock clock = new MutableClock(UTC);
        clock.advance(Duration.ofHours(1));

        MutableClock clockWithNewZone = (MutableClock) clock.withZone(ASIA_TAIPEI);
        
        assertThat(clockWithNewZone.getZone()).isEqualTo(ASIA_TAIPEI);
        assertThat(clockWithNewZone.getOffset()).isEqualTo(Duration.ofHours(1));
        // original clock should not be affected by zone change
        assertThat(clock.getZone()).isEqualTo(UTC);
    }

    @Test
    void testAdvance() {
        MutableClock clock = new MutableClock(UTC);
        Duration offset = Duration.ofDays(1).plusHours(2);
        
        clock.advance(offset);
        
        assertThat(clock.isSimulated()).isTrue();
        assertThat(clock.getOffset()).isEqualTo(offset);
        assertThat(clock.instant()).isCloseTo(Instant.now().plus(offset), within(100, ChronoUnit.MILLIS));
    }

    @Test
    void testJumpTo() {
        MutableClock clock = new MutableClock(UTC);
        Instant targetInstant = Instant.parse("2030-01-01T10:00:00Z");
        
        clock.jumpTo(targetInstant);
        
        assertThat(clock.isSimulated()).isTrue();
        assertThat(clock.instant()).isCloseTo(targetInstant, within(100, ChronoUnit.MILLIS));
    }

    @Test
    void testReset() {
        MutableClock clock = new MutableClock(UTC);
        clock.advance(Duration.ofDays(5));
        assertThat(clock.isSimulated()).isTrue();
        
        clock.reset();
        
        assertThat(clock.isSimulated()).isFalse();
        assertThat(clock.getOffset()).isEqualTo(Duration.ZERO);
        assertThat(clock.instant()).isCloseTo(Instant.now(), within(100, ChronoUnit.MILLIS));
    }

    @Test
    void testEqualsAndHashCode() {
        MutableClock clock1 = new MutableClock(UTC);
        MutableClock clock2 = new MutableClock(UTC);
        MutableClock clock3 = new MutableClock(ASIA_TAIPEI);

        // 1. Same instance (this == obj)
        assertThat(clock1).isEqualTo(clock1);

        // 2. Different type / null (!(obj instanceof MutableClock))
        assertThat(clock1).isNotEqualTo(null);
        assertThat(clock1).isNotEqualTo(new Object());

        // 3. Same zone, should be equal, even if offsets are different
        clock2.advance(Duration.ofHours(5));
        assertThat(clock1).isEqualTo(clock2);
        assertThat(clock1.hashCode()).isEqualTo(clock2.hashCode());
        
        // 4. Different zone, should not be equal
        assertThat(clock1).isNotEqualTo(clock3);
        assertThat(clock1.hashCode()).isNotEqualTo(clock3.hashCode());
    }
}
