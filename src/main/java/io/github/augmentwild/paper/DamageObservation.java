package io.github.augmentwild.paper;

import io.github.augmentwild.domain.CombatView.Receipt;
import io.github.augmentwild.domain.CombatView.Vitals;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;

/** MONITOR captures a proposed debit. Confirm it at the next observable health boundary. */
public final class DamageObservation {
    private DamageObservation() {}
    public static Vitals read(LivingEntity entity) {
        return new Vitals(entity.getHealth(),entity.getAbsorptionAmount());
    }
    @SuppressWarnings("deprecation")
    public static Receipt capture(EntityDamageEvent event, LivingEntity victim) {
        var before = read(victim);
        double absorption = event.isApplicable(EntityDamageEvent.DamageModifier.ABSORPTION)
                ? Math.max(0,-event.getDamage(EntityDamageEvent.DamageModifier.ABSORPTION)) : 0;
        return new Receipt(before,new Vitals(Math.min(before.hp(),Math.max(0,event.getFinalDamage())),
                Math.min(before.absorption(),absorption)));
    }
}
