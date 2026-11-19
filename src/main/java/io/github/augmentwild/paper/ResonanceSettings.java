package io.github.augmentwild.paper;

import io.github.augmentwild.augment.AugmentBalance;
import io.github.augmentwild.augment.AugmentResonance;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

record ResonanceSettings(AugmentResonance families, AugmentBalance balance, boolean includeSpent,
                         boolean announce, boolean sound, boolean particles, double oneWeight, double twoWeight,
                         boolean enforceBudget, boolean measureBudget, double damageRatio, double minimumDamage, int followUpTicks,
                         boolean lateEnabled, boolean lateLog, Map<Integer, Double> lateFractions,
                         Map<String, Integer> referenceStacks, Map<String, Double> finalFractions) {

    static ResonanceSettings load(JavaPlugin plugin) {
        try {
            return parse(OfficialAugments.load(plugin, "resonance.yml"));
        } catch (IllegalArgumentException | ArithmeticException invalid) {
            throw new IllegalArgumentException("Invalid resonance.yml: " + invalid.getMessage(), invalid);
        }
    }

    private static ResonanceSettings parse(ConfigurationSection config) {
        var stacking = section(config, "stacking");
        var revival = section(config, "revival");
        if (!"ADDITIVE_BONUS".equals(stacking.getString("stack-dinosaur-mode"))
                || !"CARRY_FRACTION".equals(stacking.getString("rounding"))) {
            throw new IllegalArgumentException("stacking mode or rounding");
        }
        List<?> multipliers = stacking.getList("multiplier");
        if (multipliers == null) throw new IllegalArgumentException("stacking.multiplier");
        var families = new AugmentResonance(flag(config, "enabled"), new AugmentResonance.Stacking(
                flag(stacking, "enabled"), name(stacking), members(stacking),
                multipliers.stream().map(value -> AugmentResonance.multiplier(number(value, "stacking.multiplier"))).toList(),
                AugmentResonance.multiplier(number(stacking, "total-multiplier-cap"))),
                new AugmentResonance.Revival(flag(revival, "enabled"), name(revival), members(revival),
                        integer(revival, "duration-seconds"), 2 * number(revival, "two-augments-absorption-hearts"),
                        2 * number(revival, "three-augments-absorption-hearts"), 2 * number(revival, "max-absorption-hearts")));
        var steel = section(config, "balance.steel-heart");
        var balance = new AugmentBalance(integer(steel, "first-end"), integer(steel, "second-end"),
                number(steel, "second-points"), number(steel, "third-points"),
                number(config, "balance.heavy-fist.pvp-bonus-cap-points"));
        var announcement = section(config, "announcement");
        var budget = section(config, "balance.damage-budget");
        var select = section(config, "selection");
        double one = number(select, "one-member-weight"), two = number(select, "two-members-weight");
        double ratio = number(budget, "pvp-ratio"), minimum = number(budget, "minimum-points");
        if (one < 1 || two < one || two > 10 || ratio <= 0 || ratio > 10 || minimum < 0 || minimum > 100) {
            throw new IllegalArgumentException("selection weights or damage budget");
        }
        int followUp = integer(budget, "follow-up-max-ticks");
        if (followUp < 0 || followUp > 60) throw new IllegalArgumentException("follow-up-max-ticks");
        var late = section(config, "late-growth");
        var lateTimes = section(late, "fractions-by-minute");
        var fractions = new TreeMap<Integer, Double>();
        for (var key : lateTimes.getKeys(false)) {
            int minute = Integer.parseInt(key);
            double fraction = number(lateTimes, key);
            if (minute < 0 || minute >= 30 || fraction < 0 || fraction > 1) throw new IllegalArgumentException("late-growth fraction");
            fractions.put(minute, fraction);
        }
        var lateRefs = section(late, "reference-stacks");
        var refs = new TreeMap<String, Integer>();
        for (var id : lateRefs.getKeys(false)) {
            int value = integer(lateRefs, id);
            if (!AugmentResonance.PERMANENT.contains(id) || value < 1 || value > 100_000) {
                throw new IllegalArgumentException("late-growth reference " + id);
            }
            refs.put(id, value);
        }
        var finalSection = section(late, "final-fractions");
        var finalFractions = new TreeMap<String, Double>();
        for (var id : finalSection.getKeys(false)) {
            double fraction = number(finalSection, id);
            if (!refs.containsKey(id) || fraction < 0 || fraction > 1) {
                throw new IllegalArgumentException("late-growth final fraction " + id);
            }
            finalFractions.put(id, fraction);
        }
        return new ResonanceSettings(families, balance, flag(revival, "include-spent"), flag(announcement, "enabled"),
                flag(announcement, "sound"), flag(announcement, "particles"), one, two,
                flag(budget, "enforce"), flag(budget, "measure"), ratio, minimum, followUp,
                flag(late, "enabled"), flag(late, "log"), Map.copyOf(fractions), Map.copyOf(refs), Map.copyOf(finalFractions));
    }

    int lateGrant(String id, long second) {
        int minute = (int) (second / 60);
        if (minute >= 30) {
            return (int) Math.floor(referenceStacks.getOrDefault(id, 0) * finalFractions.getOrDefault(id, 0.0));
        }
        return lateFractions.entrySet().stream().filter(entry -> entry.getKey() <= minute)
                .max(Map.Entry.comparingByKey()).map(entry -> (int) Math.floor(entry.getValue()
                        * referenceStacks.getOrDefault(id, 0))).orElse(0);
    }

    private static ConfigurationSection section(ConfigurationSection parent, String path) {
        var section = parent.getConfigurationSection(path);
        if (section == null) throw new IllegalArgumentException(path);
        return section;
    }

    private static String name(ConfigurationSection section) {
        if (!(section.get("name") instanceof String value) || value.isBlank()) throw new IllegalArgumentException("name");
        return value;
    }

    private static Set<String> members(ConfigurationSection config) {
        var entries = config.getList("members");
        if (entries == null || entries.stream().anyMatch(value -> !(value instanceof String))) {
            throw new IllegalArgumentException("members");
        }
        return Set.copyOf(config.getStringList("members"));
    }

    private static boolean flag(ConfigurationSection config, String path) {
        if (config.get(path) instanceof Boolean value) return value;
        throw new IllegalArgumentException(path);
    }

    private static double number(ConfigurationSection config, String path) {
        return number(config.get(path), path);
    }

    private static double number(Object value, String path) {
        if (value instanceof Number numeric && Double.isFinite(numeric.doubleValue())) return numeric.doubleValue();
        throw new IllegalArgumentException(path);
    }

    private static int integer(ConfigurationSection config, String path) {
        double value = number(config, path);
        if (value != (int) value) throw new IllegalArgumentException(path);
        return (int) value;
    }
}
