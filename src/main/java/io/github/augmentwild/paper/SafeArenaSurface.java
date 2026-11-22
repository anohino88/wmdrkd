package io.github.augmentwild.paper;

import java.util.Set;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

public final class SafeArenaSurface {
    private static final Set<String> OCEANS = Set.of("ocean", "deep_ocean", "warm_ocean", "lukewarm_ocean",
            "deep_lukewarm_ocean", "cold_ocean", "deep_cold_ocean", "frozen_ocean", "deep_frozen_ocean");

    private SafeArenaSurface() { }

    static boolean ocean(World world, int x, int y, int z) {
        int level = Math.clamp(y, world.getMinHeight(), world.getMaxHeight() - 1);
        return OCEANS.contains(world.getBiome(x, level, z).getKey().getKey());
    }

    static Location at(World world, int x, int z) {
        return at(world, x, z, 0);
    }

    public static Location spawnAt(World world, int x, int z) {
        return at(world, x, z, 1);
    }

    private static Location at(World world, int x, int z, int slope) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return null;
        int floor = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        var point = new Location(world, x + 0.5, floor + 1, z + 0.5);
        return valid(point, slope) ? point : null;
    }

    static boolean valid(Location point) { return valid(point, 0); }

    static boolean validSpawn(Location point) { return valid(point, 1); }

    private static boolean valid(Location point, int slope) {
        World world = point.getWorld();
        int x = point.getBlockX(), z = point.getBlockZ(), floor = point.getBlockY() - 1;
        if (world == null || floor < world.getMinHeight() || floor + 2 >= world.getMaxHeight()
                || !world.getWorldBorder().isInside(point)) return false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int columnX = x + dx, columnZ = z + dz;
                if (!world.isChunkLoaded(columnX >> 4, columnZ >> 4)) return false;
                int surface = world.getHighestBlockYAt(columnX, columnZ, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                int allowedSlope = dx == 0 && dz == 0 ? 0 : slope;
                if (Math.abs(surface - floor) > allowedSlope || surface + 2 >= world.getMaxHeight()
                        || ocean(world, columnX, surface, columnZ)
                        || !safeFloor(world.getBlockAt(columnX, surface, columnZ).getType())
                        || !world.getBlockAt(columnX, surface + 1, columnZ).getType().isAir()
                        || !world.getBlockAt(columnX, surface + 2, columnZ).getType().isAir()) return false;
            }
        }
        return true;
    }

    private static boolean safeFloor(Material material) {
        return switch (material) {
            case GRASS_BLOCK, DIRT, STONE, DEEPSLATE, SAND, RED_SAND, GRAVEL, PODZOL,
                    COARSE_DIRT, ROOTED_DIRT, MOSS_BLOCK, TERRACOTTA, SNOW_BLOCK -> true;
            default -> false;
        };
    }
}
