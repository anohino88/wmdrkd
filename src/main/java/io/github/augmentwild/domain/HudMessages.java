package io.github.augmentwild.domain;

import java.util.EnumMap;
import java.util.ArrayList;
import java.util.function.IntFunction;

/** Higher-priority layers hide lower layers until cleared or expired. */
public final class HudMessages<T> {
    public enum Layer { RESPAWN, SELECTION, DANGER, RESCUE, SHIELD, CRISIS, SUPPLY, SCOUT, PROC, INCOMING, DAMAGE, TRACK, SKILL_INPUT, BORDER, STATUS, SKILL, AUTO }
    private static final Layer[] URGENT = {Layer.RESPAWN,Layer.SELECTION,Layer.DANGER,Layer.RESCUE,Layer.CRISIS};
    private record Entry<T>(T message, long expiry) { }
    private final EnumMap<Layer, Entry<T>> entries = new EnumMap<>(Layer.class);

    public enum Kind {
        INPUT(1800,3500,true), MILESTONE(3000,8500,true), GROWTH(2000,6500,true),
        SUPPLY(2700,7000,true), COMBAT(1000,3200,false), PROC(1400,6500,true);
        public final long visibleNanos, lifetimeNanos;
        final boolean merge;
        Kind(long visibleMillis,long lifetimeMillis,boolean merge) {
            visibleNanos = visibleMillis * 1_000_000L;
            lifetimeNanos = lifetimeMillis * 1_000_000L;
            this.merge = merge;
        }
    }
    public static final int CAPACITY = 6;
    public static final class Metrics {
        private long merged, expired, budget, displayed, peak;
        private final long[] shownNanos = new long[Kind.values().length];
        private final long[] completed = new long[shownNanos.length];
        public long merged() { return merged; }
        public long expired() { return expired; }
        public long budget() { return budget; }
        public long displayed() { return displayed; }
        public long peak() { return peak; }
        public long visibleNanos(Kind kind) { return shownNanos[kind.ordinal()]; }
        public long completed(Kind kind) { return completed[kind.ordinal()]; }
    }

    private static final class Alert<T> {
        final Kind kind;
        final String id;
        final long created, deadline;
        T message;
        int count = 1;
        long shown;
        boolean displayed;
        Alert(Kind kind,String id,long now,T message) {
            this.kind = kind;
            this.id = id;
            created = now;
            deadline = now + kind.lifetimeNanos;
            this.message = message;
        }
    }
    private final ArrayList<Alert<T>> queue = new ArrayList<>(CAPACITY);
    private final Metrics metrics;
    private Alert<T> visible;
    private long sampledAt;

    public HudMessages() { this(new Metrics()); }
    public HudMessages(Metrics metrics) { this.metrics = metrics; }

    public void offer(Kind kind,String id,long now,IntFunction<T> message) {
        if (queue.size() == CAPACITY) {
            for (var iterator = queue.iterator(); iterator.hasNext();) {
                var expired = iterator.next();
                if (expired != visible && now >= expired.deadline) { metrics.expired++; iterator.remove(); }
            }
        }
        if (kind.merge) for (var item : queue) {
            if (item.kind == kind && item.id.equals(id) && now < item.deadline) {
                if (kind != Kind.INPUT) item.message = message.apply(++item.count);
                metrics.merged++;
                return;
            }
        }
        if (queue.size() == CAPACITY) {
            Alert<T> lowest = null;
            for (var item : queue) {
                if (item == visible) continue;
                if (lowest == null || item.kind.ordinal() > lowest.kind.ordinal()
                        || item.kind == lowest.kind && item.created < lowest.created) lowest = item;
            }
            metrics.budget++;
            if (lowest == null || kind.ordinal() >= lowest.kind.ordinal()) return;
            queue.remove(lowest);
        }
        queue.add(new Alert<>(kind,id,now,message.apply(1)));
        metrics.peak = Math.max(metrics.peak,queue.size());
    }

    public void remove(Kind kind) {
        queue.removeIf(alert -> alert.kind == kind);
        if (visible != null && visible.kind == kind) visible = null;
    }
    public int queued() { return queue.size(); }

    private T nextAlert(long now,boolean interrupted) {
        var previous = visible;
        if (previous != null) {
            long elapsed = Math.max(0,Math.min(now,previous.deadline) - sampledAt);
            previous.shown += elapsed;
            metrics.shownNanos[previous.kind.ordinal()] += elapsed;
        }
        sampledAt = now;
        for (var iterator = queue.iterator(); iterator.hasNext();) {
            var alert = iterator.next();
            if (alert.shown >= alert.kind.visibleNanos) {
                metrics.completed[alert.kind.ordinal()]++;
                iterator.remove();
            } else if (now >= alert.deadline) {
                metrics.expired++;
                iterator.remove();
            }
        }
        if (interrupted) { visible = null; return null; }
        if (previous != null && queue.contains(previous)) {
            visible = previous;
            return visible.message;
        }
        visible = null;
        for (var candidate : queue) {
            if (visible == null || candidate.kind.ordinal() < visible.kind.ordinal()) visible = candidate;
        }
        if (visible == null) return null;
        if (!visible.displayed) { visible.displayed = true; metrics.displayed++; }
        return visible.message;
    }

    public void set(Layer layer, T message) { entries.put(layer, new Entry<>(message, Long.MAX_VALUE)); }

    public void until(Layer layer, T message, long expiry) { entries.put(layer, new Entry<>(message, expiry)); }

    public boolean offerProc(T message, long now, long duration) {
        Entry<T> old = entries.get(Layer.PROC);
        if (old != null && now < old.expiry()) return false;
        entries.put(Layer.PROC, new Entry<>(message, now + duration));
        return true;
    }

    public void remove(Layer layer) { entries.remove(layer); }

    public T layer(Layer layer,long now) {
        var entry = entries.get(layer);
        if (entry == null || now >= entry.expiry()) return null;
        return entry.message();
    }

    public T current(long now) {
        return current(now,null);
    }

    public T current(long now,T warning) {
        entries.values().removeIf(item -> now >= item.expiry());
        for (var layer : URGENT) {
            if (layer == Layer.RESCUE && warning != null) { nextAlert(now,true); return warning; }
            T urgent = layer(layer,now);
            if (urgent != null) { nextAlert(now,true); return urgent; }
        }
        T alert = nextAlert(now,false);
        if (alert != null) return alert;
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
