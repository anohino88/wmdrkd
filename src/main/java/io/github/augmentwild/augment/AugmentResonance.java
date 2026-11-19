package io.github.augmentwild.augment;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/** Immutable match settings and ownership thresholds. Multipliers use basis points. */
public record AugmentResonance(boolean enabled, Stacking stacking, Revival revival) {
    public static final int BASE = 10_000;
    public static final Set<String> PERMANENT = Set.of("blood_record", "mineral_addiction", "steel_heart",
            "tank_engine", "muscle_memory", "iron_stomach", "wound_record", "vein_overload",
            "heart_harvester", "mineral_devourer", "draw_the_sword", "endless_evolution", "blood_god");
    public static final Set<String> DEATH_PREVENTION = Set.of("immortal_berserker", "phoenix_heart", "russian_doll");

    public record Stacking(boolean enabled, String name, Set<String> members, List<Integer> multipliers,
                           int totalCap) {
        public Stacking {
            members = Set.copyOf(members);
            multipliers = List.copyOf(multipliers);
            if (name.isBlank() || !PERMANENT.containsAll(members) || multipliers.size() != 4
                    || multipliers.getFirst() != BASE || totalCap < BASE || totalCap > BASE * 10) {
                throw new IllegalArgumentException("Invalid resonance.stacking");
            }
            int previous = BASE;
            for (int multiplier : multipliers) {
                if (multiplier < previous || multiplier > 17_500 || multiplier > totalCap) {
                    throw new IllegalArgumentException("Invalid resonance.stacking.multiplier");
                }
                previous = multiplier;
            }
        }
    }

    public record Revival(boolean enabled, String name, Set<String> members, int durationSeconds,
                          double twoPoints, double threePoints, double maxPoints) {
        public Revival {
            members = Set.copyOf(members);
            if (name.isBlank() || !DEATH_PREVENTION.containsAll(members) || durationSeconds < 1
                    || durationSeconds > 300 || !(twoPoints >= 0 && twoPoints <= threePoints
                    && threePoints <= 100 && maxPoints >= 0 && maxPoints <= 100)) {
                throw new IllegalArgumentException("Invalid resonance.revival");
            }
        }
    }

    public record State(int stackingCount, int revivalCount, int familyMultiplier, int totalMultiplier, int dinosaurOnlyMultiplier,
                        double shieldPoints, long shieldNanos) { }

    public State state(Set<String> owned, int dinosaurMultiplier) {
        int growth = count(stacking.members(), owned);
        int revivalCount = count(revival.members(), owned);
        int family = enabled && stacking.enabled() ? stacking.multipliers().get(Math.clamp(growth - 1, 0, 3)) : BASE;
        int dinosaurBonus = owned.contains("stack_dinosaur") ? Math.max(0, dinosaurMultiplier - BASE) : 0;
        int total = Math.min(stacking.totalCap(), family + dinosaurBonus);
        double shield = enabled && revival.enabled() && revivalCount >= 2
                ? Math.min(revival.maxPoints(), revivalCount >= 3 ? revival.threePoints() : revival.twoPoints()) : 0;
        return new State(growth, revivalCount, family, total, Math.min(stacking.totalCap(), BASE + dinosaurBonus), shield,
                java.time.Duration.ofSeconds(revival.durationSeconds()).toNanos());
    }

    private static int count(Set<String> members, Set<String> owned) {
        return (int) members.stream().filter(owned::contains).count();
    }

    public static int multiplier(double value) {
        return BigDecimal.valueOf(value).movePointRight(4).intValueExact();
    }
}
