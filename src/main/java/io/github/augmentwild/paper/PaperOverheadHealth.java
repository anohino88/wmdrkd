package io.github.augmentwild.paper;

import io.github.augmentwild.domain.CombatView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Health labels above visible opponents. One non-persistent display per target,
 * revealed only to eligible viewers after range and line-of-sight checks.
 * The client naturally culls labels outside its own camera viewport, so the
 * server does not guess a client's configurable field of view.
 */
final class PaperOverheadHealth {
    private static final int SCAN_TICKS = 5;
    private static final double HEIGHT_ABOVE_EYES = 0.6;
    private final JavaPlugin plugin;
    private final Predicate<Player> active;
    private final Map<UUID, Tag> tags = new HashMap<>();
    private final Map<UUID, Set<UUID>> visible = new HashMap<>();
    private long followedTick = -1;

    private static final class Tag {
        final TextDisplay display;
        String text;
        Tag(TextDisplay display) { this.display = display; }
    }

    PaperOverheadHealth(JavaPlugin plugin, Predicate<Player> active) {
        this.plugin = plugin;
        this.active = active;
    }

    private boolean eligible(Player player) {
        return player != null && player.isOnline() && !player.isDead()
                && player.getGameMode() != GameMode.SPECTATOR && active.test(player);
    }

    /** Called once for each match participant every server tick. */
    void update(Player viewer, long tick) {
        follow(tick);
        UUID viewerId = viewer.getUniqueId();
        if (!eligible(viewer) || !PlayerUi.enabled(viewer, PlayerUi.Option.HEALTH)) {
            hideViewer(viewerId);
            return;
        }
        if (tick % SCAN_TICKS != 0) return;

        var wanted = new HashSet<UUID>();
        Location eye = viewer.getEyeLocation();
        for (Player target : viewer.getWorld().getPlayers()) {
            if (target.equals(viewer) || !eligible(target) || !viewer.canSee(target)) continue;
            if (!CombatView.inRange(viewer.getLocation().distanceSquared(target.getLocation()))) continue;
            // Do not reveal opponents through walls. TextDisplay also obeys normal depth testing.
            if (!viewer.hasLineOfSight(target)) continue;
            wanted.add(target.getUniqueId());
            var tag = tags.computeIfAbsent(target.getUniqueId(), id -> create(target));
            refresh(tag, target);
            if (visible.computeIfAbsent(viewerId, ignored -> new HashSet<>()).add(target.getUniqueId())) {
                viewer.showEntity(plugin, tag.display);
            }
        }
        var shown = visible.get(viewerId);
        if (shown == null) return;
        for (UUID old : new HashSet<>(shown)) {
            if (!wanted.contains(old)) {
                Tag tag = tags.get(old);
                if (tag != null) viewer.hideEntity(plugin, tag.display);
                shown.remove(old);
            }
        }
        if (shown.isEmpty()) visible.remove(viewerId);
    }

    private Tag create(Player target) {
        var marker = target.getWorld().spawn(labelAt(target), TextDisplay.class, entity -> {
            entity.setVisibleByDefault(false);
            entity.setPersistent(false);
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setSeeThrough(false);
            entity.setShadowed(true);
            entity.setAlignment(TextDisplay.TextAlignment.CENTER);
            entity.setLineWidth(220);
            entity.setTeleportDuration(1);
        });
        return new Tag(marker);
    }

    private static Location labelAt(Player player) {
        return player.getEyeLocation().add(0, HEIGHT_ABOVE_EYES, 0);
    }

    private static String health(Player player) {
        double maximum = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        return String.format(Locale.ROOT, "%s · ❤ %.1f / %.1f HP · 흡수 +%.1f",
                player.getName(), player.getHealth(), maximum, player.getAbsorptionAmount());
    }

    private static void refresh(Tag tag, Player target) {
        String text = health(target);
        if (!text.equals(tag.text)) {
            tag.display.text(Component.text(text, NamedTextColor.WHITE));
            tag.text = text;
        }
    }

    /** Follow moving targets at most once per server tick, not once per viewer. */
    private void follow(long tick) {
        if (followedTick == tick) return;
        followedTick = tick;
        for (UUID id : new ArrayList<>(tags.keySet())) {
            Player target = Bukkit.getPlayer(id);
            Tag tag = tags.get(id);
            if (tag == null) continue;
            if (!eligible(target) || !tag.display.isValid()
                    || !tag.display.getWorld().equals(target.getWorld())) {
                removeTarget(id);
                continue;
            }
            Location next = labelAt(target);
            if (tag.display.getLocation().distanceSquared(next) > 0.0004) tag.display.teleport(next);
            if (tick % SCAN_TICKS == 0) refresh(tag, target);
        }
    }

    private void hideViewer(UUID viewerId) {
        Set<UUID> targets = visible.remove(viewerId);
        Player viewer = Bukkit.getPlayer(viewerId);
        if (targets == null || viewer == null) return;
        for (UUID targetId : targets) {
            Tag tag = tags.get(targetId);
            if (tag != null) viewer.hideEntity(plugin, tag.display);
        }
    }

    private void removeTarget(UUID targetId) {
        Tag tag = tags.remove(targetId);
        if (tag == null) return;
        for (var entry : new ArrayList<>(visible.entrySet())) {
            if (!entry.getValue().remove(targetId)) continue;
            Player viewer = Bukkit.getPlayer(entry.getKey());
            if (viewer != null) viewer.hideEntity(plugin, tag.display);
            if (entry.getValue().isEmpty()) visible.remove(entry.getKey());
        }
        tag.display.remove();
    }

    void detach(UUID id) {
        hideViewer(id);
        removeTarget(id);
    }

    void clear() {
        for (UUID viewer : new ArrayList<>(visible.keySet())) hideViewer(viewer);
        for (UUID id : new ArrayList<>(tags.keySet())) removeTarget(id);
        followedTick = -1;
    }
}
