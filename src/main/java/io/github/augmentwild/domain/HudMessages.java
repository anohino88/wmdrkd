package io.github.augmentwild.domain;

import java.util.EnumMap;

/** Higher-priority layers hide lower layers until cleared or expired. */
public final class HudMessages<T> {
    public enum Layer { SELECTION, RESCUE, SHIELD, CRISIS, SUPPLY, SCOUT, PROC, INCOMING, DAMAGE, TRACK, SKILL_INPUT, BORDER, STATUS, SKILL, AUTO }
    private record Entry<T>(T message, long expiry) { }
    private final EnumMap<Layer, Entry<T>> entries = new EnumMap<>(Layer.class);

    public void set(Layer layer, T message) { entries.put(layer, new Entry<>(message, Long.MAX_VALUE)); }

    public void until(Layer layer, T message, long expiry) { entries.put(layer, new Entry<>(message, expiry)); }

    public boolean offerProc(T message, long now, long duration) {
        Entry<T> old = entries.get(Layer.PROC);
        if (old != null && now < old.expiry()) return false;
        entries.put(Layer.PROC, new Entry<>(message, now + duration));
        return true;
    }

    public void remove(Layer layer) { entries.remove(layer); }

    public T current(long now) {
        entries.values().removeIf(item -> now >= item.expiry());
        if (entries.isEmpty()) return null;
        var first = entries.entrySet().iterator().next();
        if (first.getKey().ordinal() < Layer.STATUS.ordinal() || entries.size() == 1) return first.getValue().message();
        // Cycle only the passive pages. Every alert still preempts them immediately.
        int page = Math.floorMod(Math.floorDiv(now, 2_000_000_000L), entries.size());
        var pages = entries.values().iterator();
        for (int i = 0; i < page; i++) pages.next();
        return pages.next().message();
    }
}
