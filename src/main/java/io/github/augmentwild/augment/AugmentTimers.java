package io.github.augmentwild.augment;

import java.util.HashMap;
import java.util.Map;

public final class AugmentTimers {
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Map<String, Long> expires = new HashMap<>();
    private final Map<String, Long> lastPeriodic = new HashMap<>();

    public boolean ready(String id, long now) { return cooldowns.getOrDefault(id, 0L) <= now; }

    public void cooldown(String id, long now, int seconds) {
        cooldowns.put(id, Math.addExact(now, seconds));
    }

    public void activate(String id, long now, int seconds) {
        expires.put(id, Math.addExact(now, seconds));
        lastPeriodic.put(id, now);
    }

    public boolean active(String id, long now) { return expires.getOrDefault(id, -1L) > now; }

    public boolean periodic(String id, long now) {
        if (expires.getOrDefault(id, -1L) < now || lastPeriodic.getOrDefault(id, now) >= now) return false;
        lastPeriodic.put(id, now);
        return true;
    }

    public void expire(String id, long now) {
        if (!active(id, now)) {
            expires.remove(id);
            lastPeriodic.remove(id);
        }
    }

    public void clearActive() {
        expires.clear();
        lastPeriodic.clear();
    }
}
