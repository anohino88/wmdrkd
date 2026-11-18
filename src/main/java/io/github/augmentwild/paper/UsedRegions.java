package io.github.augmentwild.paper;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.plugin.java.JavaPlugin;

final class UsedRegions {
    private final java.nio.file.Path path;

    UsedRegions(JavaPlugin plugin) {
        path = plugin.getDataFolder().toPath().resolve("used-regions.yml");
    }

    void start(ArenaSearchService.ArenaContext context) throws IOException, InvalidConfigurationException {
        var yaml = MatchFiles.load(path);
        var regions = new ArrayList<Map<?, ?>>(yaml.getMapList("regions"));
        var region = new LinkedHashMap<String, Object>();
        region.put("match-id", context.matchId().toString());
        region.put("world-id", context.worldId().toString());
        region.put("center-x", context.area().x());
        region.put("center-z", context.area().z());
        region.put("width", context.area().width());
        region.put("height", context.area().height());
        region.put("policy", context.policy());
        region.put("surface-step", context.surfaceStep());
        region.put("verified-at", context.verifiedAt().toString());
        region.put("started-at", Instant.now().toString());
        regions.add(region);
        yaml.set("regions", regions);
        MatchFiles.save(path, yaml);
    }

    void end(ArenaSearchService.ArenaContext context) throws IOException, InvalidConfigurationException {
        var yaml = MatchFiles.load(path);
        var regions = new ArrayList<Map<?, ?>>();
        for (var entry : yaml.getMapList("regions")) {
            var updated = new LinkedHashMap<Object, Object>(entry);
            if (context.matchId().toString().equals(entry.get("match-id"))) {
                updated.put("ended-at", Instant.now().toString());
            }
            regions.add(updated);
        }
        yaml.set("regions", regions);
        MatchFiles.save(path, yaml);
    }
}
