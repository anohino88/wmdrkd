package io.github.augmentwild.match;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MatchClockTest {
    @Test void pauseExcludesTimeAndRepeatedCallsAreIdempotent() {
        var time = new AtomicLong();
        var clock = new MatchClock(time::get);
        time.set(10); clock.pause();
        time.set(20); clock.pause();
        time.set(30); clock.resume();
        time.set(40); clock.resume();
        assertEquals(Duration.ofNanos(20), clock.elapsed());
    }
    @Test void rejectsBackwardTime() {
        var time = new AtomicLong(10);
        var clock = new MatchClock(time::get);
        time.set(9);
        assertThrows(IllegalStateException.class, clock::elapsed);
    }
}
