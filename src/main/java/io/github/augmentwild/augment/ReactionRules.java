package io.github.augmentwild.augment;

/** Arithmetic shared by event handlers and deterministic combat tests. */
public final class ReactionRules {
    private ReactionRules() {}

    public static double ricochet(double confirmedDamage, double fraction, double cap) {
        return Math.min(cap, confirmedDamage * fraction);
    }

    public static double overheal(double health, double maximum, double heal, double fraction, double cap) {
        return Math.min(cap, Math.max(0, health + heal - maximum) * fraction);
    }

    public static boolean reachedMaximum(int before, int after, int cap) {
        return before < cap && after == cap;
    }

    public static boolean marked(java.util.UUID target, java.util.UUID marked, long now, long expires) {
        return target.equals(marked) && now < expires;
    }
}
