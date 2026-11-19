package io.github.augmentwild.paper;

import io.github.augmentwild.augment.ResonanceShield;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;

@SuppressWarnings("deprecation")
public final class PaperResonanceShield {
    private PaperResonanceShield() { }

    public record Receipt(double reserved, double base, double beforeMagic, double afterMagic) {
        /** Observe only at MONITOR. A later listener can cancel or replace damage modifiers. */
        public double confirm(EntityDamageEvent event) {
            double finalDamage = event.getFinalDamage();
            if (event.isCancelled() || event.getDamage() <= 0 || !Double.isFinite(finalDamage)) return 0;
            double magic = event.getDamage(DamageModifier.MAGIC);
            double surviving = event.getDamage() == base || magic > afterMagic
                    ? Math.clamp(beforeMagic - magic, 0, reserved) : reserved;
            return Math.clamp(finalDamage + surviving, 0, surviving);
        }
    }

    /** Applies post-mitigation protection without increasing or spending vanilla absorption. */
    public static Receipt protect(EntityDamageEvent event, ResonanceShield shield, long now) {
        var slot = DamageModifier.MAGIC;
        if (event.isCancelled() || !event.isApplicable(slot) || !Double.isFinite(event.getFinalDamage())) return null;
        double spent = shield.preview(now, event.getFinalDamage());
        if (spent <= 0) return null;
        double before = event.getDamage(slot);
        double after = before - spent;
        event.setDamage(slot, after);
        return new Receipt(spent, event.getDamage(), before, after);
    }
}
