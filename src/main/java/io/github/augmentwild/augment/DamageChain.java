package io.github.augmentwild.augment;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Tracks synchronous synthetic damage and limits repeated hits in one original event. */
public final class DamageChain {
    public enum Attack { MELEE, PROJECTILE, EFFECT }

    public record Origin(Attack attack, double baseDamage, double extraDamage, double mitigation) {
        public Origin {
            if (!Double.isFinite(baseDamage) || !Double.isFinite(extraDamage) || !Double.isFinite(mitigation)
                    || baseDamage < 0 || mitigation < 0) throw new IllegalArgumentException("Invalid original damage");
        }
    }

    public record Context(UUID match, UUID attacker, UUID victim, UUID original, String augment,
                          int depth, double damage, boolean critical, boolean mayLifesteal, Origin provenance) { }

    private final int maxDepth;
    private final int maxHitsPerTarget;
    public static final class Root {
        private final Map<UUID, Integer> hits = new HashMap<>();
        private final UUID id = UUID.randomUUID();
        private final Origin provenance;
        private final LinkedDamageBudget budget;

        public Root() { this(new Origin(Attack.EFFECT, 0, 0, 0)); }

        public Root(Origin provenance) { this(provenance, null); }

        public Root(Origin provenance, LinkedDamageBudget budget) {
            this.provenance = provenance;
            this.budget = budget;
        }

        public UUID id() { return id; }
        public LinkedDamageBudget budget() { return budget; }
    }

    private final Set<String> activeEffects = new HashSet<>();
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
        if (depth > maxDepth || !activeEffects.add(augment)) return false;
        root.hits.merge(victim, 1, Integer::sum);
        current = new Context(match, attacker, victim, root.id, augment, depth, amount, critical, false, root.provenance);
        try {
            return action.get();
        } finally {
            current = parent;
            activeEffects.remove(augment);
        }
    }
}
