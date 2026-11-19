package io.github.augmentwild.paper;

import java.time.Duration;
import org.bukkit.configuration.file.FileConfiguration;

record IntelSettings(int cost, double trackerRange, long trackDuration, long trackCooldown, long trackUpdate,
                     boolean revealName, double scoutRange, long aim, long scoutCooldown, long resultLifetime, long engagementWindow,
                     int historySize, long historyWindow, long deathDeliveryLifetime) {
    static IntelSettings load(FileConfiguration config) {
        int cost = config.getInt("tracker.amount");
        double range = config.getDouble("tracker.maximum-distance");
        double scout = config.getDouble("scouting.maximum-distance");
        int entries = config.getInt("death-review.max-records");
        if (cost < 1 || cost > 64 || !(range == -1 || Double.isFinite(range) && range > 0)
                || !(scout > 0 && scout <= 128) || entries < 1 || entries > 1024) {
            throw new IllegalArgumentException("Invalid tracker, scouting or death-review limits");
        }
        return new IntelSettings(cost, range, time(config, "tracker.duration-seconds"),
                time(config, "tracker.cooldown-seconds"), time(config, "tracker.update-seconds"), config.getBoolean("tracker.reveal-name"),
                scout, time(config, "scouting.aim-seconds"), time(config, "scouting.cooldown-seconds"),
                time(config, "scouting.result-seconds"), time(config, "scouting.engagement-seconds"), entries, time(config, "death-review.window-seconds"),
                time(config, "death-review.delivery-seconds"));
    }

    static long time(FileConfiguration config, String path) {
        long seconds = config.getLong(path);
        if (seconds < 1 || seconds > 3600) throw new IllegalArgumentException("Invalid seconds at " + path);
        return Duration.ofSeconds(seconds).toNanos();
    }
}
