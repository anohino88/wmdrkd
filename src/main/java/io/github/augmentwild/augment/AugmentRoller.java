package io.github.augmentwild.augment;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

public final class AugmentRoller {
    private final AugmentRegistry registry;
    private final RandomGenerator random;

    public AugmentRoller(AugmentRegistry registry, RandomGenerator random) {
        this.registry = registry;
        this.random = random;
    }

    public List<AugmentDefinition> roll(int level, boolean finalRound, Set<String> owned, Set<String> previous) {
        var pool = registry.selectable().stream()
                .filter(a -> !finalRound || a.rarity() == AugmentRarity.PRISMATIC).toList();
        if (pool.size() < 3) throw new IllegalStateException("Insufficient implemented augments");
        var result = new ArrayList<AugmentDefinition>(3);
        for (int slot = 0; slot < 3; slot++) {
            var rarity = finalRound ? AugmentRarity.PRISMATIC : AugmentRarity.roll(level, random.nextDouble(100));
            var remaining = pool.stream().filter(a -> !result.contains(a)).toList();
            var tier = remaining.stream().filter(a -> a.rarity() == rarity).toList();
            if (tier.isEmpty()) tier = remaining;
            var changed = tier.stream().filter(a -> !previous.contains(a.id())).toList();
            if (!changed.isEmpty()) tier = changed;
            var fresh = tier.stream().filter(a -> !owned.contains(a.id())).toList();
            if (!fresh.isEmpty()) tier = fresh;
            result.add(tier.get(random.nextInt(tier.size())));
        }
        if (!previous.isEmpty() && result.stream().allMatch(a -> previous.contains(a.id()))) {
            var alternatives = pool.stream().filter(a -> !previous.contains(a.id())).toList();
            if (!alternatives.isEmpty()) result.set(2, alternatives.get(random.nextInt(alternatives.size())));
        }
        return List.copyOf(result);
    }
}
