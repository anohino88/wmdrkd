package io.github.augmentwild.paper;

import io.github.augmentwild.domain.MatchRules;
import org.bukkit.configuration.file.FileConfiguration;

public record PaperMatchSettings(MatchRules rules, int minimumPlayers, int maximumPlayers,
                                 int selectionSeconds, long placementTimeoutNanos, int borderDamageInterval, double borderDamagePoints) {
    public static PaperMatchSettings load(FileConfiguration config) {
        if (!config.getBoolean("gathering.pvp-enabled")) {
            throw new IllegalArgumentException("gathering.pvp-enabled must be true");
        }
        int minimum = config.getInt("game.min-players");
        int maximum = config.getInt("game.max-players");
        int gathering = config.getInt("game.gathering-seconds");
        int interval = config.getInt("game.selection-interval-seconds");
        int selection = config.getInt("game.selection-duration-seconds");
        int grace = config.getInt("game.pvp-grace-seconds");
        if (minimum < 2 || maximum < minimum || gathering <= 0 || interval <= 0 || gathering % interval != 0
                || selection < 0 || grace < 0
                || !config.getBoolean("game.pause-match-clock-during-selection")) {
            throw new IllegalArgumentException("Invalid game or player limits in config.yml");
        }
        int arenaSize = config.getInt("arena.width");
        if (arenaSize != config.getInt("arena.height") || arenaSize != config.getDouble("border.start-size")) {
            throw new IllegalArgumentException("arena.width, arena.height and border.start-size must match for the virtual border");
        }
        int damageInterval = config.getInt("border.damage-interval-seconds");
        double damagePoints = 2 * config.getDouble("border.damage-hearts");
        if (damageInterval < 1 || !Double.isFinite(damagePoints) || damagePoints < 0
                || config.getDouble("border.final-size") < 1) {
            throw new IllegalArgumentException("Invalid border damage or final size");
        }
        var rules = new MatchRules(gathering, (long) gathering + grace, config.getInt("respawn.delay-seconds"),
                config.getInt("combat.lives"), arenaSize, config.getInt("arena.maximum-size"),
                config.getInt("arena.spawn.min-player-distance"), config.getDouble("border.start-size"),
                config.getDouble("border.final-size"), config.getInt("border.shrink-duration-seconds"),
                config.getInt("tracker.amount"), config.getDouble("tracker.maximum-distance"), interval,
                config.getInt("augments.choices-per-round"), config.getInt("augments.rarity.silver-weight"),
                config.getInt("augments.rarity.gold-weight"), config.getInt("augments.rarity.prismatic-weight"),
                config.getInt("augments.proc.max-depth"));
        int placementSeconds = config.getInt("arena.spawn.placement-timeout-seconds");
        if (placementSeconds < 1) throw new IllegalArgumentException("placement timeout must be positive");
        return new PaperMatchSettings(rules, minimum, maximum, selection,
                java.util.concurrent.TimeUnit.SECONDS.toNanos(placementSeconds), damageInterval, damagePoints);
    }
}
