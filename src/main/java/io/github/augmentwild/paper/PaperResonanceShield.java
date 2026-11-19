package io.github.augmentwild.paper;

import io.github.augmentwild.augment.ResonanceShield;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;

@SuppressWarnings("deprecation")
public final class PaperResonanceShield {
    private static final DamageModifier[] MODIFIERS = DamageModifier.values();
    private static final int SEARCH_STEPS = 32;

    private PaperResonanceShield() { }

    public record Receipt(double reserved, double protectedBase) {
        /** A subsequent BASE replacement owns that value. Observe only at MONITOR. */
        public double confirm(EntityDamageEvent event) {
            if (event.isCancelled() || event.getDamage() != protectedBase || !Double.isFinite(event.getFinalDamage())) return 0;
            return Math.clamp(event.getFinalDamage() + reserved, 0, reserved);
        }
    }

    /** Finds the raw value giving the desired post-mitigation damage with native modifiers recalculated. */
    public static Receipt protect(EntityDamageEvent event, ResonanceShield shield, long now) {
        return reduce(event, shield.preview(now, event.getFinalDamage()));
    }

    static Receipt reduce(EntityDamageEvent event, double points) {
        double initial = event.getFinalDamage();
        double base = event.getDamage();
        if (event.isCancelled() || !Double.isFinite(initial) || !Double.isFinite(base) || base <= 0) return null;
        double requested = Math.min(points, initial);
        if (requested <= 0) return null;

        double[] original = new double[MODIFIERS.length];
        for (var modifier : MODIFIERS) {
            original[modifier.ordinal()] = event.isApplicable(modifier) ? event.getDamage(modifier) : Double.NaN;
        }
        double target = initial - requested;
        double low = 0;
        double high = base;
        for (int i = 0; i < SEARCH_STEPS; i++) {
            double candidate = (low + high) / 2;
            restore(event, original);
            event.setDamage(candidate);
            if (event.getFinalDamage() < target) low = candidate;
            else high = candidate;
        }
        restore(event, original);
        event.setDamage(high);
        double adjusted = event.getFinalDamage();
        double drop = initial - adjusted;
        if (!(drop > 0 && adjusted >= 0)
                || event.isApplicable(DamageModifier.ABSORPTION)
                   && Math.abs(original[DamageModifier.ABSORPTION.ordinal()] - event.getDamage(DamageModifier.ABSORPTION)) > 1e-7) {
            restore(event, original);
            return null;
        }
        return new Receipt(Math.min(requested, drop), high);
    }

    private static void restore(EntityDamageEvent event, double[] original) {
        for (var modifier : MODIFIERS) {
            double value = original[modifier.ordinal()];
            if (!Double.isNaN(value)) event.setDamage(modifier, value);
        }
    }
}
