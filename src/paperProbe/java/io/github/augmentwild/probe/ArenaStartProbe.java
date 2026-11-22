package io.github.augmentwild.probe;

import io.github.augmentwild.domain.Arena;
import io.github.augmentwild.paper.ArenaSearchService;
import io.github.augmentwild.paper.SafeArenaSurface;
import org.bukkit.Material;
import org.bukkit.World;

/** Paper block collisions and world border behavior in the disposable server. */
final class ArenaStartProbe {
    private ArenaStartProbe() { }

    static void run(World world) {
        var border = world.getWorldBorder();
        var center = border.getCenter();
        double size = border.getSize();
        try {
            border.setCenter(7300,-3800);
            var area = new Arena(7300,-3800,1000,1000);
            border.setSize(500);
            verify(!ArenaSearchService.validPlacement(world,area,0),"500 cannot fit 1000");
            verify(ArenaSearchService.validPlacement(world,new Arena(7300,-3800,128,128),0),"lab fits 500");
            border.setSize(1000);
            verify(ArenaSearchService.validPlacement(world,area,0),"aligned 1000 touches half-open edges");
            verify(!ArenaSearchService.validPlacement(world,area,1),"clearance excludes exact fit");
            border.setCenter(7300.25,-3800);
            verify(!ArenaSearchService.validPlacement(world,area,0),"unaligned exact fit");
            border.setCenter(7300,-3800);
            for (int width : new int[]{2000,5000}) {
                border.setSize(width);
                verify(ArenaSearchService.validPlacement(world,area,0),"offset fit " + width);
                verify(!ArenaSearchService.validPlacement(world,new Arena(0,0,1000,1000),0),"spawn outside border");
            }
        } finally {
            border.setCenter(center);
            border.setSize(size);
        }

        var spot = world.getSpawnLocation();
        int x = (spot.getBlockX() >> 4) * 16 + 8;
        int z = (spot.getBlockZ() >> 4) * 16 + 8;
        int y = world.getMaxHeight()-5;
        if (!world.isChunkLoaded(x >> 4,z >> 4)) throw new AssertionError("fixture spawn chunk not loaded");
        var old = new java.util.LinkedHashMap<org.bukkit.block.Block,org.bukkit.block.data.BlockData>();
        var biome = world.getBiome(x,y,z);
        try {
            world.setBiome(x,y,z,org.bukkit.block.Biome.PLAINS);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = 0; dy <= 3; dy++) {
                        var block = world.getBlockAt(x+dx,y+dy,z+dz);
                        old.put(block,block.getBlockData());
                        block.setType(dy == 0 ? Material.STONE : Material.AIR,false);
                    }
                }
            }
            verify(SafeArenaSurface.spawnAt(world,x,z) != null,"flat spawn");
            var neighbor = world.getBlockAt(x+1,y+1,z);
            neighbor.setType(Material.STONE,false);
            verify(SafeArenaSurface.spawnAt(world,x,z) != null,"one-block uphill spawn");
            neighbor.setType(Material.LAVA,false);
            verify(SafeArenaSurface.spawnAt(world,x,z) == null,"hazard rejected");
            neighbor.setType(Material.AIR,false);
            world.setBiome(x,y,z,org.bukkit.block.Biome.OCEAN);
            verify(SafeArenaSurface.spawnAt(world,x,z) == null,"ocean spawn rejected");
        } finally {
            old.forEach((block,data) -> block.setBlockData(data,false));
            world.setBiome(x,y,z,biome);
        }
    }

    private static void verify(boolean condition,String reason) {
        if (!condition) throw new AssertionError(reason);
    }
}
