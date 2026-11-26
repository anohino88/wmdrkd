package io.github.augmentwild.domain;

import java.time.Instant;
import java.util.UUID;

/** Immutable evidence from one observed server event. Root groups related attacks without merging damage. */
public record CombatEvent(long id, UUID match, Instant occurred, long elapsed,
                          UUID actor, UUID target, String source, Kind kind, String cause,
                          String augment, String skill, UUID root, double original, double finalDamage, double armor, double blocking,
                          double hp, double absorption, double shield, double healed,
                          boolean cancelled, boolean death, boolean critical, boolean blocked, boolean unclassified) {
    public enum Kind { MELEE, PROJECTILE, AUGMENT, ACTIVE, CHAIN, PERIODIC, DELAYED,
        ENVIRONMENT, BORDER, UNCLASSIFIED, HEAL, PROC, RESCUE, DEATH }

    public boolean applied() { return hp > 0 || absorption > 0 || shield > 0; }
}
