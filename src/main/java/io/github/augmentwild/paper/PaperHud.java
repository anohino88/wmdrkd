package io.github.augmentwild.paper;

import io.github.augmentwild.domain.HudMessages;
import io.github.augmentwild.domain.HudMessages.Layer;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** The only writer to the match action bar. Bukkit state stays on the server thread. */
final class PaperHud {
    private static final long REFRESH_NANOS = 1_000_000_000L;
    private final Map<UUID, View> players = new HashMap<>();

    private static final class View {
        final HudMessages<Component> layers = new HudMessages<>();
        Component sent;
        long refresh;
    }

    void set(UUID id, Layer layer, Component message) {
        players.computeIfAbsent(id, ignored -> new View()).layers.set(layer, message);
    }

    void skillInput(Player p, String result) {
        if (PlayerUi.enabled(p,PlayerUi.Option.SKILL))
            players.computeIfAbsent(p.getUniqueId(),ignored -> new View()).layers.until(Layer.SKILL_INPUT,Component.text(result),System.nanoTime()+1_200_000_000L);
    }
    void rescued(Player p,String name) {
        players.computeIfAbsent(p.getUniqueId(),ignored -> new View()).layers.until(Layer.RESCUE,Component.text("치명 피해 방지 · " + name),System.nanoTime()+1_200_000_000L);
    }
    void heal(Player player, double before, double after) {
        if (after > before) damage(player,String.format(java.util.Locale.ROOT,"회복 +%.1f HP",after-before));
    }
    void damage(Player p, String text) {
        if (PlayerUi.enabled(p, PlayerUi.Option.DAMAGE))
            players.computeIfAbsent(p.getUniqueId(), ignored -> new View()).layers.until(Layer.DAMAGE,
                    Component.text(text),System.nanoTime()+io.github.augmentwild.domain.CombatView.DAMAGE_VISIBLE);
    }

    void proc(Player player, String name) {
        players.computeIfAbsent(player.getUniqueId(), ignored -> new View()).layers
                .offerProc(Component.text("증강 발동 · " + name, NamedTextColor.LIGHT_PURPLE), System.nanoTime(), 2 * REFRESH_NANOS);
    }

    void remove(UUID id, Layer layer) {
        View view = players.get(id);
        if (view != null) view.layers.remove(layer);
    }

    void render(Player player, long now) {
        View view = players.get(player.getUniqueId());
        if (view == null) return;
        if (!PlayerUi.enabled(player,PlayerUi.Option.PROC)) view.layers.remove(Layer.PROC);
        if (!PlayerUi.enabled(player,PlayerUi.Option.DAMAGE)) view.layers.remove(Layer.DAMAGE);
        if (!PlayerUi.enabled(player,PlayerUi.Option.SKILL)) { view.layers.remove(Layer.SKILL); view.layers.remove(Layer.SKILL_INPUT); }
        Component next = view.layers.current(now);
        if (!Objects.equals(next, view.sent) || next != null && now - view.refresh >= REFRESH_NANOS) {
            player.sendActionBar(next == null ? Component.empty() : next);
            view.sent = next;
            view.refresh = now;
        }
        if (next == null) players.remove(player.getUniqueId());
    }

    void clear(UUID id, Player player) {
        View view = players.remove(id);
        if (view != null && view.sent != null && player != null) player.sendActionBar(Component.empty());
    }

    void clear(JavaPlugin plugin) {
        for (UUID id : java.util.List.copyOf(players.keySet())) clear(id, plugin.getServer().getPlayer(id));
    }
}
