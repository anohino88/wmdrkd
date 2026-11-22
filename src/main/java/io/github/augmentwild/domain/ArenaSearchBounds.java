package io.github.augmentwild.domain;

import java.util.HashSet;
import java.util.Set;
import java.util.random.RandomGenerator;

/** Search lattice of integer lower corners for a half-open arena rectangle. */
public record ArenaSearchBounds(int minX, int maxX, int minZ, int maxZ) {
    public record Corner(int x, int z) { }

    public static ArenaSearchBounds fit(double centerX, double centerZ, double borderSize, double worldLimit,
                                       int width, int height, double clearance) {
        double half = borderSize / 2;
        return new ArenaSearchBounds(
                (int) Math.ceil(Math.max(-worldLimit, centerX - half) + clearance),
                (int) Math.floor(Math.min(worldLimit, centerX + half) - clearance - width),
                (int) Math.ceil(Math.max(-worldLimit, centerZ - half) + clearance),
                (int) Math.floor(Math.min(worldLimit, centerZ + half) - clearance - height));
    }

    public ArenaSearchBounds near(int originX, int originZ, int width, int height, int radius) {
        return new ArenaSearchBounds(Math.max(minX, originX - width / 2 - radius),
                Math.min(maxX, originX - width / 2 + radius),
                Math.max(minZ, originZ - height / 2 - radius),
                Math.min(maxZ, originZ - height / 2 + radius));
    }

    public boolean contains(Corner corner) {
        return corner.x >= minX && corner.x <= maxX && corner.z >= minZ && corner.z <= maxZ;
    }

    public boolean empty() { return minX > maxX || minZ > maxZ; }

    public String centers(int width, int height) {
        if (empty()) return "없음";
        return "X " + (minX + width / 2.0) + " ~ " + (maxX + width / 2.0)
                + " / Z " + (minZ + height / 2.0) + " ~ " + (maxZ + height / 2.0);
    }

    public static final class Candidates {
        private final Set<Corner> tried = new HashSet<>();

        public Corner take(ArenaSearchBounds range, Corner preferred, RandomGenerator random) {
            if (range.empty()) return null;
            if (preferred != null && range.contains(preferred) && tried.add(preferred)) return preferred;
            for (int i = 0; i < 8; i++) {
                var corner = new Corner(random.nextInt(range.minX, range.maxX + 1),
                        random.nextInt(range.minZ, range.maxZ + 1));
                if (tried.add(corner)) return corner;
            }
            // At most the previously tried positions can fail before finding a new corner.
            for (int x = range.minX; x <= range.maxX; x++) {
                for (int z = range.minZ; z <= range.maxZ; z++) {
                    var corner = new Corner(x, z);
                    if (tried.add(corner)) return corner;
                }
            }
            return null;
        }
    }
}
