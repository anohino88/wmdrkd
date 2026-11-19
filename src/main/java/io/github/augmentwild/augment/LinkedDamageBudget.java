package io.github.augmentwild.augment;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** One original hit, with independent raw damage budgets per target across deferred effects. */
public final class LinkedDamageBudget {
    private record Target(double requested, double committed) { }
    public record Admission(double allowed, boolean overBudget) { }

    private final double limit;
    private final boolean enforce;
    private final Map<UUID, Target> targets = new HashMap<>();

    public LinkedDamageBudget(double limit, boolean enforce, UUID primary, double originalBonus) {
        if (!(limit >= 0 && Double.isFinite(limit) && originalBonus >= 0 && Double.isFinite(originalBonus))) {
            throw new IllegalArgumentException("Invalid linked damage budget");
        }
        this.limit = limit;
        this.enforce = enforce;
        targets.put(primary, new Target(originalBonus, originalBonus));
    }

    public Admission offer(UUID victim, double raw) {
        if (!(raw > 0 && Double.isFinite(raw))) return new Admission(0, false);
        var previous = targets.getOrDefault(victim, new Target(0, 0));
        double allowed = enforce ? Math.min(raw, Math.max(0, limit - previous.committed())) : raw;
        targets.put(victim, new Target(previous.requested() + raw, previous.committed() + allowed));
        return new Admission(allowed, previous.requested() + raw > limit);
    }

    public void refund(UUID victim, double raw) {
        var previous = targets.get(victim);
        if (previous != null) {
            targets.put(victim, new Target(previous.requested(), Math.max(0, previous.committed() - raw)));
        }
    }
}
