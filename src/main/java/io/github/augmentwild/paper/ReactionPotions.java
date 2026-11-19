package io.github.augmentwild.paper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Tracks only effects introduced into empty slots. Any subsequent Bukkit change releases ownership. */
final class ReactionPotions {
    private record Slot(UUID entity, PotionEffectType type) {}
    private record Grant(UUID owner, String augment, LivingEntity target) {}
    private final Map<Slot, Grant> grants = new HashMap<>();

    boolean apply(UUID owner, String augment, LivingEntity target, PotionEffect effect) {
        Slot slot = new Slot(target.getUniqueId(), effect.getType());
        var previous = target.getPotionEffect(effect.getType());
        boolean emptyOrOurs = previous == null || grants.containsKey(slot);
        if (previous != null && previous.getAmplifier() >= effect.getAmplifier()
                && (previous.isInfinite() || previous.getDuration() >= effect.getDuration())) return false;
        if (!target.addPotionEffect(effect)) return false;
        if (emptyOrOurs) grants.put(slot, new Grant(owner, augment, target));
        return true;
    }

    void changed(UUID entity, PotionEffectType type) {
        grants.remove(new Slot(entity, type));
    }

    void clear(UUID owner, String augment) {
        for (var entry : Map.copyOf(grants).entrySet()) {
            Grant grant = entry.getValue();
            if (owner != null && !grant.owner().equals(owner) || augment != null && !grant.augment().equals(augment)) continue;
            if (grants.remove(entry.getKey(), grant)) grant.target().removePotionEffect(entry.getKey().type());
        }
    }
}
