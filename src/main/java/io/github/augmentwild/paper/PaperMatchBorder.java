package io.github.augmentwild.paper;

import io.github.augmentwild.domain.Arena;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Server;
import org.bukkit.WorldBorder;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/** Owns one virtual border per match and restores each player's previous display. */
public final class PaperMatchBorder {
    private final Server server;
    private final WorldBorder display;
    private final Map<UUID, WorldBorder> previous = new HashMap<>();

    PaperMatchBorder(Server server, Arena area) {
        this.server = server;
        display = createDisplay(server, area);
    }

    public static WorldBorder createDisplay(Server server, Arena area) {
        if (area.width() != area.height()) throw new IllegalArgumentException("Virtual border must be square");
        var border = server.createWorldBorder();
        border.setCenter(area.x(), area.z());
        border.setSize(area.width());
        return border;
    }

    public static void applyDamage(LivingEntity entity, double healthPoints) {
        entity.damage(healthPoints, DamageSource.builder(DamageType.OUTSIDE_BORDER).build());
    }

    void update(Arena area) {
        if (display.getSize() != area.width()) display.setSize(area.width());
    }

    void attach(Player player) {
        UUID id = player.getUniqueId();
        if (!previous.containsKey(id)) {
            previous.put(id, player.getWorldBorder());
            player.setWorldBorder(display);
        }
    }

    void refresh(Player player) {
        if (!previous.containsKey(player.getUniqueId())) attach(player);
        else if (player.getWorldBorder() == display) player.setWorldBorder(display);
    }

    void detach(Player player) {
        UUID id = player.getUniqueId();
        if (!previous.containsKey(id)) return;
        var original = previous.remove(id);
        if (player.getWorldBorder() != display) return;
        try {
            player.setWorldBorder(original);
        } catch (UnsupportedOperationException wrongWorld) {
            player.setWorldBorder(null);
        }
    }

    void clear() {
        for (UUID id : java.util.List.copyOf(previous.keySet())) {
            Player player = server.getPlayer(id);
            if (player != null) detach(player);
        }
        previous.clear();
    }
}
