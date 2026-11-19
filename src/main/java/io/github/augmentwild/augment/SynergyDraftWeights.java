package io.github.augmentwild.augment;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.ToDoubleBiFunction;

public final class SynergyDraftWeights implements ToDoubleBiFunction<String, Set<String>> {
    private final List<Set<String>> groups;
    private final double one;
    private final double two;

    public SynergyDraftWeights(Collection<Set<String>> groups, double one, double two) {
        if (!(one >= 1 && two >= one && Double.isFinite(two))) throw new IllegalArgumentException("Invalid draft weights");
        this.groups = groups.stream().map(Set::copyOf).toList();
        this.one = one;
        this.two = two;
    }

    @Override
    public double applyAsDouble(String candidate, Set<String> owned) {
        if (owned.contains(candidate)) return 1;
        int best = 0;
        for (Set<String> group : groups) {
            if (group.contains(candidate)) best = Math.max(best, (int) group.stream().filter(owned::contains).count());
        }
        return best >= 2 ? two : best == 1 ? one : 1;
    }
}
