package io.github.augmentwild.domain;

/** Horizontal directions relative to Minecraft yaw (zero faces +Z). */
public final class CombatNavigation {
    private CombatNavigation() {}
    private static final String[] ARROWS = {"↑","↗","→","↘","↓","↙","←","↖"};

    public static String bearing(double dx, double dz, float yaw) {
        if (!Double.isFinite(dx) || !Double.isFinite(dz) || !Float.isFinite(yaw) || dx == 0 && dz == 0) return "";
        double angle = Math.toDegrees(Math.atan2(-dx, dz)) - yaw;
        return ARROWS[Math.floorMod(Math.round(angle / 45), 8)];
    }

    public static double edgeDistance(Arena area, double x, double z) {
        double dx = Math.min(x - area.minX(), area.maxX() - x);
        double dz = Math.min(z - area.minZ(), area.maxZ() - z);
        return dx >= 0 && dz >= 0 ? Math.min(dx, dz) : Math.hypot(Math.min(0, dx), Math.min(0, dz));
    }
}
