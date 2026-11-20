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

    private final io.github.augmentwild.domain.CombatHistory history = new io.github.augmentwild.domain.CombatHistory();
    private final java.util.Set<UUID> low = new java.util.HashSet<>();
    private static final java.time.format.DateTimeFormatter UTC = java.time.format.DateTimeFormatter
            .ofPattern("HH:mm:ss").withZone(java.time.ZoneOffset.UTC);

    void record(Player p, String text) {
        history.record(p.getUniqueId(),UTC.format(java.time.Instant.now()) + "  " + text);
    }

    boolean beginObservation(Object event) { return history.beginObservation(event); }

    String combatLog(Player p) {
        var rows = history.recent(p.getUniqueId());
        return rows.isEmpty() ? "현재 경기의 전투 기록이 없습니다."
                : "나의 전투 기록 · UTC · 최근 " + rows.size() + "건\n" + String.join("\n",rows);
    }

    void incoming(Player p, String text, long expiry) {
        if (PlayerUi.enabled(p,PlayerUi.Option.INCOMING))
            players.computeIfAbsent(p.getUniqueId(), ignored -> new View()).layers.until(Layer.INCOMING,Component.text(text),expiry);
    }

    void crisis(Player p, long now) {
        UUID id = p.getUniqueId();
        var max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (max == null || p.getHealth() > .25 * max.getValue() || !PlayerUi.enabled(p,PlayerUi.Option.CRISIS)) {
            if (low.remove(id)) remove(id,Layer.CRISIS);
        } else if (p.getHealth() > 0 && low.add(id)) {
            players.computeIfAbsent(id, ignored -> new View()).layers.until(Layer.CRISIS,Component.text("생존 위기 · 체력 25% 이하",NamedTextColor.RED),now+1_000_000_000L);
            if (PlayerUi.enabled(p,PlayerUi.Option.SOUND)) p.playSound(p.getLocation(),org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS,.5f,.7f);
        }
    }

    void border(Player p, io.github.augmentwild.domain.Arena area, boolean shrinking) {
        var loc = p.getLocation();
        double x = loc.getX(), z = loc.getZ();
        double distance = io.github.augmentwild.domain.CombatNavigation.edgeDistance(area,x,z);
        boolean inside = area.contains(x,z,0);
        if (!PlayerUi.enabled(p,PlayerUi.Option.BORDER) || inside && distance > 15) {
            remove(p.getUniqueId(),Layer.BORDER);
            return;
        }
        String arrow = io.github.augmentwild.domain.CombatNavigation.bearing(area.x()-x,area.z()-z,loc.getYaw());
        set(p.getUniqueId(),Layer.BORDER,Component.text((inside ? "경계 접근" : "위험 · 경계 밖")
                + (shrinking ? " · 축소 중" : " · 고정") + " · " + Math.round(distance) + "블록 · 안쪽 " + arrow));
    }

    private static final Map<org.bukkit.potion.PotionEffectType,String> EFFECTS = Map.of(
            org.bukkit.potion.PotionEffectType.POISON,"독", org.bukkit.potion.PotionEffectType.SLOWNESS,"감속",
            org.bukkit.potion.PotionEffectType.SPEED,"신속", org.bukkit.potion.PotionEffectType.STRENGTH,"공격 강화",
            org.bukkit.potion.PotionEffectType.RESISTANCE,"저항", org.bukkit.potion.PotionEffectType.ABSORPTION,"흡수");

    static String duration(int ticks) { return ticks < 0 ? "상시" : (ticks+19L)/20 + "초"; }

    void status(Player p, java.util.List<String> augmentStates, long second) {
        var id = p.getUniqueId();
        if (!PlayerUi.enabled(p,PlayerUi.Option.STATUS)) { remove(id,Layer.STATUS); return; }
        var lines = new java.util.ArrayList<String>();
        if (p.getFireTicks() > 0) lines.add("화상 " + duration(p.getFireTicks()));
        for (var type : java.util.List.of(org.bukkit.potion.PotionEffectType.POISON,org.bukkit.potion.PotionEffectType.SLOWNESS,
                org.bukkit.potion.PotionEffectType.RESISTANCE,org.bukkit.potion.PotionEffectType.STRENGTH,
                org.bukkit.potion.PotionEffectType.SPEED,org.bukkit.potion.PotionEffectType.ABSORPTION)) {
            var effect = p.getPotionEffect(type);
            if (effect != null) lines.add(EFFECTS.get(type) + " " + duration(effect.getDuration()));
        }
        if (p.getAbsorptionAmount() > 0) lines.add("흡수 +" + String.format(java.util.Locale.ROOT,"%.1f HP",p.getAbsorptionAmount()));
        lines.addAll(augmentStates);
        if (lines.isEmpty()) remove(id,Layer.STATUS);
        else {
            int start = 3 * Math.floorMod(second / 2,(lines.size()+2)/3);
            set(id,Layer.STATUS,Component.text(String.join(" · ",lines.subList(start,Math.min(start+3,lines.size())))));
        }
    }

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
        if (after <= before) return;
        String message = String.format(java.util.Locale.ROOT,"회복 +%.1f HP",after-before);
        record(player,message);
        damage(player,message);
    }
    void damage(Player p, String text) {
        if (PlayerUi.enabled(p, PlayerUi.Option.DAMAGE))
            players.computeIfAbsent(p.getUniqueId(), ignored -> new View()).layers.until(Layer.DAMAGE,
                    Component.text(text),System.nanoTime()+io.github.augmentwild.domain.CombatView.DAMAGE_VISIBLE);
    }

    void activated(Player player,String name) {
        record(player,"발동 · " + name);
        proc(player,name);
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
        if (!PlayerUi.enabled(player,PlayerUi.Option.INCOMING)) view.layers.remove(Layer.INCOMING);
        if (!PlayerUi.enabled(player,PlayerUi.Option.AUTO)) view.layers.remove(Layer.AUTO);
        Component next = view.layers.current(now);
        if (!Objects.equals(next, view.sent) || next != null && now - view.refresh >= REFRESH_NANOS) {
            player.sendActionBar(next == null ? Component.empty() : next);
            view.sent = next;
            view.refresh = now;
        }
        if (next == null) players.remove(player.getUniqueId());
    }

    void clear(UUID id, Player player) {
        low.remove(id);
        View view = players.remove(id);
        if (view != null && view.sent != null && player != null) player.sendActionBar(Component.empty());
    }

    void clear(JavaPlugin plugin) {
        history.clear();
        low.clear();
        for (UUID id : java.util.List.copyOf(players.keySet())) clear(id, plugin.getServer().getPlayer(id));
    }
}
