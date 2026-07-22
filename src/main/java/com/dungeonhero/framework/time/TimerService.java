package com.dungeonhero.framework.time;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Clock-based timers and cooldowns; scheduling remains an integration concern. */
public final class TimerService {
  private final Clock clock;
  private final Map<String, Instant> deadlines = new LinkedHashMap<>();

  public TimerService() {
    this(Clock.systemUTC());
  }

  public TimerService(Clock clock) {
    this.clock = clock == null ? Clock.systemUTC() : clock;
  }

  public synchronized void start(String key, Duration duration) {
    if (key == null
        || key.isBlank()
        || duration == null
        || duration.isNegative()
        || duration.isZero()) {
      throw new IllegalArgumentException("Timer key and positive duration are required.");
    }
    deadlines.put(key, clock.instant().plus(duration));
  }

  public synchronized boolean active(String key) {
    Instant deadline = deadlines.get(key);
    if (deadline == null) {
      return false;
    }
    if (!deadline.isAfter(clock.instant())) {
      deadlines.remove(key);
      return false;
    }
    return true;
  }

  public synchronized Duration remaining(String key) {
    Instant deadline = deadlines.get(key);
    if (deadline == null) {
      return Duration.ZERO;
    }
    Duration remaining = Duration.between(clock.instant(), deadline);
    return remaining.isNegative() ? Duration.ZERO : remaining;
  }

  public synchronized void cancel(String key) {
    deadlines.remove(key);
  }

  public synchronized void clear() {
    deadlines.clear();
  }
}
