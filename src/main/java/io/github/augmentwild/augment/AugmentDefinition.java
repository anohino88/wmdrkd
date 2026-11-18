package io.github.augmentwild.augment;

import java.util.Objects;

public record AugmentDefinition(String id, AugmentRarity rarity, String name, String condition, String unit,
                                Effect effect, double amount, int duration, int cooldown,
                                boolean enabled, boolean implemented) {
    public enum Effect { XP, MINING, SPEED, HEAL, REAR, FALL, ARMOR, BERSERK, DEFENSE, PURSUIT }

    public AugmentDefinition {
        Objects.requireNonNull(id);
        Objects.requireNonNull(rarity);
        Objects.requireNonNull(effect);
        if (!id.matches("[a-z0-9_]+") || name.isBlank() || condition.isBlank() || unit.isBlank()
                || !Double.isFinite(amount) || amount <= 0 || duration < 0 || cooldown < 0
                || (effect == Effect.DEFENSE && amount > .75)) {
            throw new IllegalArgumentException("Invalid augment definition " + id);
        }
    }

    public boolean selectable() { return enabled && implemented; }
}
