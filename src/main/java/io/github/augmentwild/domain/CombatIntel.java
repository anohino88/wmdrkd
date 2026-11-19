package io.github.augmentwild.domain;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class CombatIntel {
    private CombatIntel() { }

    public record Position(UUID id, double x, double z) { }

    public static Position nearest(Position origin, List<Position> eligible, double range) {
        return eligible.stream().filter(target -> !origin.id().equals(target.id()))
                .filter(target -> range < 0 || distance(origin, target) <= range * range)
                .min(Comparator.<Position>comparingDouble(target -> distance(origin, target)).thenComparing(Position::id))
                .orElse(null);
    }

    private static double distance(Position a, Position b) {
        double x = a.x() - b.x(), z = a.z() - b.z();
        return x * x + z * z;
    }

    public static String direction(Position origin, Position target) {
        double x = target.x() - origin.x(), z = target.z() - origin.z();
        if (x == 0 && z == 0) return "방향 미정";
        String[] directions = {"북", "북동", "동", "남동", "남", "남서", "서", "북서"};
        return directions[Math.floorMod(Math.round(Math.atan2(x, -z) / (Math.PI / 4)), directions.length)];
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
