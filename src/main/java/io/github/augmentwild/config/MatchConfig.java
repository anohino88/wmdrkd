package io.github.augmentwild.config;

/** Immutable settings intended to be captured at match start. */
public record MatchConfig(int minPlayers, int maxPlayers, int arenaWidth, int arenaHeight,
                          int respawnDelaySeconds, int combatLives) {
    public MatchConfig {
        if (minPlayers < 2 || maxPlayers < minPlayers || arenaWidth <= 0 || arenaHeight <= 0
                || respawnDelaySeconds < 0 || combatLives < 1) {
            throw new IllegalArgumentException("Invalid match configuration");
        }
    }
}
