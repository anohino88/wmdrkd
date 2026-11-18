package io.github.augmentwild.augment;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Tracks synchronous synthetic damage and limits repeated hits in one original event. */
public final class DamageChain {
    public record Context(UUID match, UUID attacker, UUID victim, UUID original, String augment,
                          int depth, double damage, boolean critical, boolean mayLifesteal) { }

    private final int maxDepth;
    private final int maxHitsPerTarget;
    public static final class Root {
        private final Map<UUID, Integer> hits = new HashMap<>();
        private final UUID id = UUID.randomUUID();
    }

    private Root root;
    private Context current;

    public DamageChain(int maxDepth, int maxHitsPerTarget) {
        if (maxDepth < 0 || maxHitsPerTarget < 1) throw new IllegalArgumentException();
        this.maxDepth = maxDepth;
        this.maxHitsPerTarget = maxHitsPerTarget;
    }

    public Context current() { return current; }

    public Root currentRoot() { return root; }

    public void root(Runnable action) {
        root(new Root(), action);
    }

    public void root(Root origin, Runnable action) {
        if (root != null) return;
        root = origin;
        try {
            action.run();
        } finally {
            root = null;
        }
    }

    public boolean hit(UUID match, UUID attacker, UUID victim, String augment, double amount,
                       boolean critical, Supplier<Boolean> action) {
        if (root == null || attacker.equals(victim) || !Double.isFinite(amount) || amount <= 0
                || root.hits.getOrDefault(victim, 0) >= maxHitsPerTarget) return false;
        Context parent = current;
        int depth = parent == null ? 1 : parent.depth() + 1;
        if (depth > maxDepth || (parent != null && parent.augment().equals(augment))) return false;
        root.hits.merge(victim, 1, Integer::sum);
        current = new Context(match, attacker, victim, root.id, augment, depth, amount, critical, false);
        try {
            return action.get();
        } finally {
            current = parent;
        }
    }
}
