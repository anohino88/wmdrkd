package io.github.augmentwild.domain;

import java.util.Optional;
import java.util.random.RandomGenerator;

/** Samples block centers uniformly inside a half-open arena with a safety margin. */
public final class ArenaCoordinates {
    public record Column(int x, int z) { }

    private ArenaCoordinates() { }

    public static Optional<Column> sample(Arena area, int margin, RandomGenerator random) {
        if (margin < 0) throw new IllegalArgumentException("negative margin");
        int minX = (int) Math.ceil(area.minX() + margin - 0.5);
        int endX = (int) Math.ceil(area.maxX() - margin - 0.5);
        int minZ = (int) Math.ceil(area.minZ() + margin - 0.5);
        int endZ = (int) Math.ceil(area.maxZ() - margin - 0.5);
        if (minX >= endX || minZ >= endZ) return Optional.empty();
        return Optional.of(new Column(random.nextInt(minX, endX), random.nextInt(minZ, endZ)));
    }
}
