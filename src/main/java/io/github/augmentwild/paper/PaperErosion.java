package io.github.augmentwild.paper;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

final class PaperErosion {
    private record Lease(double damage, long expiresAt) { }
    private record Target(LivingEntity entity, Map<UUID, Lease> sources) { }

    private final Map<UUID, Target> targets = new HashMap<>();
    private final NamespacedKey key;
    private final double limit;
    private long evaluated = -1;

    PaperErosion(JavaPlugin plugin, double limit) {
        this.key = new NamespacedKey(plugin, "official_erosion");
        this.limit = limit;
    }

    double drain(UUID source, LivingEntity entity, long now, double amount, int recoverySeconds) {
        if (entity.getAttribute(Attribute.MAX_HEALTH) == null) return 0;
        Target target = targets.computeIfAbsent(entity.getUniqueId(), id -> new Target(entity, new HashMap<>()));
        Map<UUID, Lease> sources = target.sources();
        double previous = effective(target);
        var lease = sources.get(source);
        double applied = Math.min(limit, (lease == null ? 0 : lease.damage()) + amount);
        sources.put(source, new Lease(applied, now + recoverySeconds));
        update(target);
        return Math.max(0, effective(target) - previous);
    }

    void tick(long now) {
        if (evaluated == now) return;
        evaluated = now;
        for (Iterator<Target> iterator = targets.values().iterator(); iterator.hasNext();) {
            Target target = iterator.next();
            target.sources().values().removeIf(lease -> lease.expiresAt() <= now);
            if (!target.entity().isValid() || target.entity().isDead()) target.sources().clear();
            update(target);
            if (target.sources().isEmpty()) iterator.remove();
        }
    }

    void remove(UUID player) {
        for (Iterator<Target> iterator = targets.values().iterator(); iterator.hasNext();) {
            Target target = iterator.next();
            if (target.entity().getUniqueId().equals(player)) target.sources().clear();
            else target.sources().remove(player);
            update(target);
            if (target.sources().isEmpty()) iterator.remove();
        }
    }

    void clear() {
        for (Target target : targets.values()) {
            target.sources().clear();
            update(target);
        }
        targets.clear();
        evaluated = -1;
    }

    private double effective(Target target) {
        return Math.min(limit, target.sources().values().stream().mapToDouble(Lease::damage).sum());
    }

    private void update(Target target) {
        var health = target.entity().getAttribute(Attribute.MAX_HEALTH);
        if (health == null) return;
        double restore = health.getModifier(key) == null ? 0 : -health.getModifier(key).getAmount();
        double damage = Math.clamp(effective(target), 0, Math.max(0, health.getValue() + restore - 1));
        PaperAugmentAttribute.update(health, key, -damage, Operation.ADD_NUMBER, damage > 0);
        if (!target.entity().isDead() && target.entity().getHealth() > health.getValue()) {
            target.entity().setHealth(health.getValue());
        }
    }
}
