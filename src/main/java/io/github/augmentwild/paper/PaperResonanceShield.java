package io.github.augmentwild.paper;

import io.github.augmentwild.augment.ResonanceShield;
import org.bukkit.event.entity.EntityDamageEvent;

@SuppressWarnings("deprecation")
public final class PaperResonanceShield {
    private PaperResonanceShield() { }

    /** Applies post-mitigation protection without increasing or spending vanilla absorption. */
    public static double protect(EntityDamageEvent event, ResonanceShield shield, long now) {
        var slot = EntityDamageEvent.DamageModifier.MAGIC;
        if (event.isCancelled() || !event.isApplicable(slot) || !Double.isFinite(event.getFinalDamage())) return 0;
        double spent = shield.preview(now, event.getFinalDamage());
        if (spent > 0) event.setDamage(slot, event.getDamage(slot) - spent);
        return spent;
    }
}
