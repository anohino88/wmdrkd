package io.github.augmentwild.augment;

/** Health points and damage points use the Minecraft scale of two points per heart. */
public record AugmentBalance(int steelFirstEnd, int steelSecondEnd, double steelSecondPoints,
                             double steelThirdPoints, double heavyFistPvpCap) {
    public AugmentBalance {
        if (steelFirstEnd < 1 || steelSecondEnd < steelFirstEnd
                || !Double.isFinite(steelSecondPoints) || steelSecondPoints < 0
                || !Double.isFinite(steelThirdPoints) || steelThirdPoints < 0
                || !Double.isFinite(heavyFistPvpCap) || heavyFistPvpCap <= 0) {
            throw new IllegalArgumentException("Invalid resonance balance settings");
        }
    }

    public double steelHealth(int stacks, double firstPoints) {
        return Math.min(stacks, steelFirstEnd) * firstPoints
                + Math.clamp(stacks - steelFirstEnd, 0, steelSecondEnd - steelFirstEnd) * steelSecondPoints
                + Math.max(0, stacks - steelSecondEnd) * steelThirdPoints;
    }

    public double heavyFist(double maxHealth, double ratio, boolean pvp) {
        double points = maxHealth * ratio;
        return pvp ? Math.min(points, heavyFistPvpCap) : points;
    }
}
