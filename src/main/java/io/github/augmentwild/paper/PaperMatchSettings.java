package io.github.augmentwild.paper;

import io.github.augmentwild.domain.MatchRules;
import org.bukkit.configuration.file.FileConfiguration;

public record PaperMatchSettings(MatchRules rules, int minimumPlayers, int maximumPlayers,
                                 int selectionSeconds, int normalSupplySecond, int premiumSupplySecond) {
    public static PaperMatchSettings load(FileConfiguration config) {
        int minimum = config.getInt("game.min-players");
        int maximum = config.getInt("game.max-players");
        int gathering = config.getInt("game.gathering-seconds");
        int interval = config.getInt("game.selection-interval-seconds");
        int selection = config.getInt("game.selection-duration-seconds");
        int grace = config.getInt("game.pvp-grace-seconds");
        int normal = config.getInt("supply.normal.time-seconds");
        int premium = config.getInt("supply.premium.time-seconds");
        if (minimum < 2 || maximum < minimum || gathering <= 0 || interval <= 0 || gathering % interval != 0
                || selection < 0 || grace < 0 || normal < 0 || premium < normal || premium > gathering
                || !config.getBoolean("game.pause-match-clock-during-selection")) {
            throw new IllegalArgumentException("Invalid game, supply, or player limits in config.yml");
        }
        int arenaSize = Math.min(config.getInt("arena.width"), config.getInt("arena.height"));
        var rules = new MatchRules(gathering, (long) gathering + grace, config.getInt("respawn.delay-seconds"),
                config.getInt("combat.lives"), arenaSize, config.getInt("arena.maximum-size"),
                config.getInt("arena.spawn.min-player-distance"), config.getDouble("border.start-size"),
                config.getDouble("border.final-size"), config.getInt("border.shrink-duration-seconds"),
                config.getInt("tracker.amount"), config.getDouble("tracker.maximum-distance"), interval,
                config.getInt("augments.choices-per-round"), config.getInt("augments.rarity.silver-weight"),
                config.getInt("augments.rarity.gold-weight"), config.getInt("augments.rarity.prismatic-weight"),
                config.getInt("augments.proc.max-depth"));
        return new PaperMatchSettings(rules, minimum, maximum, selection, normal, premium);
    }
}
