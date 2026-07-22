package com.dungeonhero.framework;

import com.dungeonhero.framework.time.TimerService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimerServiceTest {

    @Test
    void tracksAndExpiresCooldownsFromInjectedClock() {
        MutableClock clock = new MutableClock();
        TimerService timers = new TimerService(clock);
        timers.start("boss", Duration.ofSeconds(30));

        assertTrue(timers.active("boss"));
        clock.advance(Duration.ofSeconds(31));
        assertFalse(timers.active("boss"));
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.EPOCH;

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        private void advance(Duration duration) { now = now.plus(duration); }
    }
}
