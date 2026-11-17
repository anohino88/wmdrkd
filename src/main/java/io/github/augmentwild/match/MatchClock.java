package io.github.augmentwild.match;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Monotonic clock; caller controls lifecycle and supplies nanoseconds. */
public final class MatchClock {
    private final LongSupplier time;
    private long last;
    private long elapsed;
    private boolean paused;

    public MatchClock(LongSupplier time) {
        this.time = Objects.requireNonNull(time);
        this.last = time.getAsLong();
    }

    private void update() {
        long now = time.getAsLong();
        long delta = now - last;
        if (delta < 0) throw new IllegalStateException("Clock moved backwards");
        if (!paused) elapsed = Math.addExact(elapsed, delta);
        last = now;
    }

    public Duration elapsed() { update(); return Duration.ofNanos(elapsed); }
    public void pause() { update(); paused = true; }
    public void resume() { update(); paused = false; }
    public boolean paused() { return paused; }
}
