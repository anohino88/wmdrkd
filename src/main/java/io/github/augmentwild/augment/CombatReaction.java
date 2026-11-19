package io.github.augmentwild.augment;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Temporary state of one reaction belonging to one fighter. Times are match milliseconds. */
public final class CombatReaction {
    public enum Action { MELEE, PROJECTILE, REACTION }
    private final Map<UUID, ReactionWindow> windows = new HashMap<>();
    private final long[] actions = {-1, -1, -1};
    private long chargedUntil, readyAt;
    private int charges;

    public boolean charged(long time) { return time < chargedUntil; }
    public boolean ready(long time) { return time >= readyAt && !charged(time); }
    public long remaining(long time) { return Math.max(0, readyAt - time); }
    public int charges() { return charges; }

    public void charge(long now, long duration, long cooldown) {
        chargedUntil = now + duration;
        readyAt = now + cooldown;
    }

    public boolean spend(long now) {
        if (!charged(now)) return false;
        chargedUntil = 0;
        return true;
    }

    public boolean store(int cap) {
        if (charges >= cap) return false;
        charges++;
        return true;
    }

    public boolean spendWard(long now, long cooldown) {
        if (!ready(now) || charges == 0) return false;
        charges--;
        readyAt = now + cooldown;
        return true;
    }

    public boolean action(Action type, long now, long interval) {
        if (!ready(now)) return false;
        actions[type.ordinal()] = now;
        for (long at : actions) if (at < 0 || now - at > interval) return false;
        Arrays.fill(actions, -1);
        return true;
    }

    public double progress(UUID key, long now, long interval) {
        var window = windows.get(key);
        return window == null ? 0 : window.total(now, interval);
    }

    public int actionCount(long now, long interval) {
        return (int) Arrays.stream(actions).filter(at -> at >= 0 && now - at <= interval).count();
    }

    public boolean accumulate(UUID key, long now, long interval, double amount, double threshold) {
        if (!ready(now)) return false;
        windows.values().removeIf(window -> window.expired(now, interval));
        var window = windows.computeIfAbsent(key, ignored -> new ReactionWindow());
        if (window.add(now, interval, amount) < threshold) return false;
        windows.remove(key);
        return true;
    }

    public void clearTransient() {
        chargedUntil = 0;
        charges = 0;
        Arrays.fill(actions, -1);
        windows.clear();
    }
}
