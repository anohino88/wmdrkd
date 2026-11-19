package io.github.augmentwild.augment;

/** Keys used by the live engine, the codex and lab progress commands. */
public enum AugmentEvolution {
    STEEL("steel_heart", "steel_heart", "steel-evolution-stacks", "중첩"),
    MISSILE("magic_missile", "missile_shots", "missile-evolution-shots", "발사"),
    BERSERK("berserk_engine", "berserk_seconds", "berserk-evolution-seconds", "최고 속도 초"),
    BURN("infernal_conductor", null, "burn-evolution-damage", "화상 피해"),
    TANK("tank_engine", "tank_engine", "tank-evolution-stacks", "중첩"),
    SWORD("sword_wind", "sword_hits", "sword-evolution-hits", "검기 적중");

    public final String augment, counter, goal, unit;

    AugmentEvolution(String augment, String counter, String goal, String unit) {
        this.augment = augment;
        this.counter = counter;
        this.goal = goal;
        this.unit = unit;
    }

    public static AugmentEvolution of(String id) {
        for (var entry : values()) if (entry.augment.equals(id)) return entry;
        return null;
    }

    public double progress(AugmentProgress progress, double burn) {
        return this == BURN ? burn : progress.value(counter);
    }

    public boolean complete(double progress, double goal) { return progress >= goal; }
}
