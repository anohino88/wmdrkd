package io.github.augmentwild.augment;

public final class AugmentMath {
    private AugmentMath() { }

    public static int experience(int original, double bonus) {
        if (original <= 0) return original;
        return (int) Math.min(Integer.MAX_VALUE, Math.round(original * (1 + bonus)));
    }

    public static double bonusHit(double damage, double base, double bonus) { return damage + base * bonus; }
    public static double mitigate(double damage, double reduction) { return damage * (1 - reduction); }
}
