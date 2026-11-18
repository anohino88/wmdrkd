package io.github.augmentwild.augment;

import java.util.HashMap;
import java.util.Map;

/** Match-owned counters. All callers run on the server thread. */
public final class AugmentProgress {
    private final Map<String, Integer> values = new HashMap<>();
    private final Map<String, Long> readyAt = new HashMap<>();

    public int value(String key) {
        return values.getOrDefault(key, 0);
    }

    public int add(String key, int amount, int cap) {
        if (amount < 0 || cap < 0) throw new IllegalArgumentException("Negative augment progress");
        int result = (int) Math.min((long) value(key) + amount, cap);
        values.put(key, result);
        return result;
    }

    public void reset(String key) {
        values.remove(key);
    }

    public boolean ready(String key, long now) {
        return now >= readyAt.getOrDefault(key, Long.MIN_VALUE);
    }

    public boolean cooldown(String key, long now, long duration) {
        if (duration < 0) throw new IllegalArgumentException("Negative cooldown");
        if (!ready(key, now)) return false;
        readyAt.put(key, Math.addExact(now, duration));
        return true;
    }
}
