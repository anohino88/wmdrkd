package io.github.augmentwild.paper;

import java.util.function.Predicate;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

final class PaperAugmentMovement {
    record History(long second, Location location, double health) { }

    private PaperAugmentMovement() { }

    static Location toward(Player player, Vector direction, double range, Predicate<Location> inside) {
        if (direction.setY(0).lengthSquared() < 1e-6) return null;
        direction.normalize();
        var view = player.getEyeLocation();
        var obstruction = view.getWorld().rayTraceBlocks(view, direction, range, FluidCollisionMode.NEVER, true);
        if (obstruction != null) range = Math.max(0, obstruction.getHitPosition().distance(view.toVector()) - 1);
        var origin = player.getLocation();
        for (double distance = range; distance >= 1; distance--) {
            var column = origin.clone().add(direction.clone().multiply(distance));
            var target = SafeArenaSurface.at(column.getWorld(), column.getBlockX(), column.getBlockZ());
            if (target != null && inside.test(target) && Math.abs(target.getY() - origin.getY()) <= 2) {
                target.setYaw(origin.getYaw());
                target.setPitch(origin.getPitch());
                return target;
            }
        }
        return null;
    }
}
