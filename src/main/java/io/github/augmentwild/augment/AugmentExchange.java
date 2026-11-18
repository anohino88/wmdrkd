package io.github.augmentwild.augment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Two-phase selection. Existing ownership is untouched until a complete exchange is committed. */
public final class AugmentExchange {
    public record Commit(List<String> removed, List<String> awarded) { }

    private final List<String> removed;
    private final List<String> candidates;
    private final List<String> selected = new ArrayList<>();
    private final int rewardCount;
    private boolean committed;

    public AugmentExchange(List<String> removed, List<String> candidates, int rewardCount) {
        this.removed = List.copyOf(removed);
        this.candidates = List.copyOf(candidates);
        this.rewardCount = rewardCount;
        if (rewardCount < 1 || candidates.size() < rewardCount || Set.copyOf(candidates).size() != candidates.size()
                || Set.copyOf(removed).size() != removed.size()) throw new IllegalArgumentException("Invalid exchange");
    }

    public List<String> candidates() {
        return candidates.stream().filter(id -> !selected.contains(id)).toList();
    }

    public boolean reserves(String id) {
        return candidates.contains(id);
    }

    public boolean select(String id) {
        if (committed || selected.size() >= rewardCount || !candidates().contains(id)) return false;
        selected.add(id);
        return true;
    }

    public int remaining() {
        return rewardCount - selected.size();
    }

    public Optional<Commit> finalizeAgainst(Set<String> owned) {
        if (committed || remaining() != 0 || !owned.containsAll(removed)) return Optional.empty();
        var conflict = new HashSet<>(owned);
        conflict.retainAll(selected);
        if (!conflict.isEmpty()) return Optional.empty();
        committed = true;
        return Optional.of(new Commit(removed, List.copyOf(selected)));
    }
}
