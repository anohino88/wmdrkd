package io.github.augmentwild.domain;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;

public final class CombatIntel {
    private CombatIntel() { }

    public static final class Tracking {
        public enum Start { STARTED, COOLDOWN, NO_TARGET, NO_FUNDS }

        private UUID target;
        private long expires;
        private long cooldown;

        public Start start(UUID eligible, long now, long duration, long reuse, BooleanSupplier pay) {
            if (busy(now)) return Start.COOLDOWN;
            if (eligible == null) return Start.NO_TARGET;
            if (!pay.getAsBoolean()) return Start.NO_FUNDS;
            target = eligible;
            expires = now + duration;
            cooldown = now + reuse;
            return Start.STARTED;
        }

        public boolean busy(long now) { return target(now) != null || now < cooldown; }
        public UUID target(long now) { return now < expires ? target : null; }
        public UUID assigned() { return target; }
        public void stop() { target = null; }
    }

    public record TimedReport<T>(List<T> values, long deadline) {
        public TimedReport { values = List.copyOf(values); }
        public boolean valid(long now) { return deadline - now > 0; }
    }

    public static final class Dwell {
        private UUID target;
        private long since;

        public boolean observe(UUID visible, long now, long required) {
            if (visible == null || !Objects.equals(visible, target)) {
                target = visible;
                since = now;
            }
            return visible != null && now - since >= required;
        }
    }

    public enum Kind { MELEE, PROJECTILE, AUGMENT, PERIODIC, AREA, ENVIRONMENT }

    public record Damage(long time, UUID attacker, String attackerName, Kind kind, String cause,
                         String augment, UUID origin, double health, double shield) { }

    public static final class History {
        private final ArrayDeque<Damage> records = new ArrayDeque<>();
        private final int maxRecords;
        private final long window;

        public History(int maxRecords, long window) {
            if (maxRecords <= 0 || window <= 0) throw new IllegalArgumentException("Invalid combat review bounds");
            this.maxRecords = maxRecords;
            this.window = window;
        }

        public void add(Damage damage) {
            expire(damage.time());
            if (records.size() == maxRecords) records.removeFirst();
            records.addLast(damage);
        }

        public List<Damage> snapshot(long now) {
            expire(now);
            return List.copyOf(records);
        }

        private void expire(long now) {
            while (!records.isEmpty() && now - records.getFirst().time() > window) records.removeFirst();
        }
    }
}
