package io.github.augmentwild.paper;

import io.github.augmentwild.domain.CombatView;
import java.util.function.Predicate;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class TargetSight {
    private TargetSight() {}
    public static RayTraceResult trace(Location eye, Vector direction, Predicate<Entity> candidate) {
        return eye.getWorld().rayTrace(eye,direction,CombatView.RANGE,FluidCollisionMode.NEVER,false,0,candidate);
    }
}
