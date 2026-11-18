package io.github.augmentwild.paper;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** Lightweight, match-owned homing paths with no spawned projectile entities. */
final class PaperMissiles {
    private PaperMissiles() { }

    static final class Missile {
        final Set<UUID> struck = new HashSet<>();
        final Location position;
        int remainingTicks;
        int bounces;
        LivingEntity target;

        Missile(Location position, LivingEntity target, int remainingTicks, int bounces) {
            this.position = position;
            this.target = target;
            this.remainingTicks = remainingTicks;
            this.bounces = bounces;
        }

        boolean advance(Player source, double speed, BiPredicate<Player, LivingEntity> eligible) {
            if (--remainingTicks < 0 || !eligible.test(source, target) || struck.contains(target.getUniqueId())
                    || !position.getWorld().equals(target.getWorld())) {
                remainingTicks = 0;
                return false;
            }
            Vector offset = target.getEyeLocation().toVector().subtract(position.toVector());
            double distance = offset.length();
            if (distance > 0) {
                double step = Math.min(speed, distance);
                Vector direction = offset.normalize();
                var block = position.getWorld().rayTraceBlocks(position, direction, step, FluidCollisionMode.NEVER, true);
                if (block != null) {
                    remainingTicks = 0;
                    return false;
                }
                position.add(direction.multiply(step));
            }
            return distance <= speed;
        }
    }
}
