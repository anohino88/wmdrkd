package io.github.augmentwild.domain;

import java.util.EnumMap;

/** Higher-priority layers hide lower layers until cleared or expired. */
public final class HudMessages<T> {
    public enum Layer { SELECTION, SHIELD, SUPPLY, SCOUT, PROC, TRACK }
    private record Entry<T>(T message, long expiry) { }
    private final EnumMap<Layer, Entry<T>> entries = new EnumMap<>(Layer.class);

    public void set(Layer layer, T message) { entries.put(layer, new Entry<>(message, Long.MAX_VALUE)); }

    public boolean offerProc(T message, long now, long duration) {
        Entry<T> old = entries.get(Layer.PROC);
        if (old != null && now < old.expiry()) return false;
        entries.put(Layer.PROC, new Entry<>(message, now + duration));
        return true;
    }

    public void remove(Layer layer) { entries.remove(layer); }

    public T current(long now) {
        entries.values().removeIf(item -> now >= item.expiry());
        return entries.isEmpty() ? null : entries.values().iterator().next().message();
    }
}
