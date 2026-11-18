package io.github.augmentwild.augment;

public enum AugmentRarity {
    SILVER, GOLD, PRISMATIC;

    public record Odds(double silver, double gold, double prismatic) { }

    public static Odds odds(int level) {
        int bounded = Math.clamp(level, 0, 50);
        return new Odds(80 - 1.46 * bounded, 18 + 0.5 * bounded, 2 + 0.96 * bounded);
    }

    public static AugmentRarity roll(int level, double percent) {
        if (!(percent >= 0 && percent < 100)) throw new IllegalArgumentException("invalid roll");
        var odds = odds(level);
        if (percent < odds.silver()) return SILVER;
        return percent < odds.silver() + odds.gold() ? GOLD : PRISMATIC;
    }
}
