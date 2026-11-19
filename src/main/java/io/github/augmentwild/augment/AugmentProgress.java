package io.github.augmentwild.augment;

import java.util.HashMap;
import java.util.Map;

/** Match-owned counters. All callers run on the server thread. */
public final class AugmentProgress {
    private final Map<String, Integer> values = new HashMap<>();
    private final Map<String, Integer> remainder = new HashMap<>();
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

    public int addPermanent(String key, int rawGain, int multiplier, int cap) {
        if (rawGain < 0 || multiplier < 0 || cap < 0) throw new IllegalArgumentException("Invalid permanent gain");
        int before = value(key);
        if (before >= cap || rawGain == 0) return before;
        long credit = (long) rawGain * multiplier + remainder.getOrDefault(key, 0);
        int awarded = (int) Math.min(cap - before, credit / AugmentResonance.BASE);
        int after = add(key, awarded, cap);
        if (after == cap) remainder.remove(key);
        else remainder.put(key, (int) (credit % AugmentResonance.BASE));
        return after;
    }

    public void reset(String key) {
        values.remove(key);
        remainder.remove(key);
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
