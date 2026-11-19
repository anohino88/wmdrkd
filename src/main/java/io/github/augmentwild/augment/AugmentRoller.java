package io.github.augmentwild.augment;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.function.Predicate;
import java.util.function.ToDoubleBiFunction;

public final class AugmentRoller {
    private final AugmentRegistry registry;
    private final RandomGenerator random;
    private final ToDoubleBiFunction<String, Set<String>> weight;

    public AugmentRoller(AugmentRegistry registry, RandomGenerator random) {
        this(registry, random, (id, owned) -> 1);
    }

    public AugmentRoller(AugmentRegistry registry, RandomGenerator random, ToDoubleBiFunction<String, Set<String>> weight) {
        this.registry = registry;
        this.weight = weight;
        this.random = random;
    }

    public List<AugmentDefinition> roll(int level, boolean finalRound, Set<String> owned, Set<String> previous) {
        return roll(level, finalRound, owned, previous, augment -> true);
    }

    public List<AugmentDefinition> roll(int level, boolean finalRound, Set<String> owned, Set<String> previous,
                                       Predicate<AugmentDefinition> eligible) {
        var pool = registry.selectable().stream()
                .filter(a -> !finalRound || a.rarity() == AugmentRarity.PRISMATIC).filter(eligible).toList();
        int slots = Math.min(3, pool.size());
        var result = new ArrayList<AugmentDefinition>(3);
        for (int slot = 0; slot < slots; slot++) {
            var rarity = finalRound ? AugmentRarity.PRISMATIC : AugmentRarity.roll(level, random.nextDouble(100));
            var remaining = pool.stream().filter(a -> !result.contains(a)).toList();
            var tier = remaining.stream().filter(a -> a.rarity() == rarity).toList();
            if (tier.isEmpty()) tier = remaining;
            var changed = tier.stream().filter(a -> !previous.contains(a.id())).toList();
            if (!changed.isEmpty()) tier = changed;
            var fresh = tier.stream().filter(a -> !owned.contains(a.id())).toList();
            if (!fresh.isEmpty()) tier = fresh;
            result.add(pick(tier, owned));
        }
        if (!result.isEmpty() && !previous.isEmpty() && result.stream().allMatch(a -> previous.contains(a.id()))) {
            var lastRarity = result.getLast().rarity();
            var alternatives = pool.stream().filter(a -> a.rarity() == lastRarity && !previous.contains(a.id())).toList();
            if (!alternatives.isEmpty()) result.set(result.size() - 1, pick(alternatives, owned));
        }
        return List.copyOf(result);
    }

    private AugmentDefinition pick(List<AugmentDefinition> tier, Set<String> owned) {
        double[] weights = tier.stream().mapToDouble(option -> weight.applyAsDouble(option.id(), owned)).toArray();
        double total = java.util.Arrays.stream(weights).sum();
        if (!(total > 0 && Double.isFinite(total))) throw new IllegalArgumentException("Invalid tier weight");
        double draw = random.nextDouble(total);
        for (int index = 0; index < weights.length; index++) {
            draw -= weights[index];
            if (draw < 0) return tier.get(index);
        }
        return tier.getLast();
    }
}
