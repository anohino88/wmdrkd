package io.github.augmentwild.paper;

import io.github.augmentwild.domain.HudMessages;
import io.github.augmentwild.domain.CombatEvent;
import io.github.augmentwild.domain.HudMessages.Layer;
import io.github.augmentwild.domain.HudMessages.Kind;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** The only writer to the match action bar. Bukkit state stays on the server thread. */
final class PaperHud {
    private static final long REFRESH_NANOS = 1_000_000_000L;
    private final Map<UUID, View> players = new HashMap<>();
    private HudMessages.Metrics queueMetrics = new HudMessages.Metrics();
    private long actionSends, barUpdates, skillQueries, duplicateInputs;
    void skillQueried() { skillQueries++; }
    void duplicateInput() { duplicateInputs++; }
    String metrics() {
        var times = new java.util.StringJoiner(",");
        for (Kind kind : Kind.values()) times.add(kind.name() + "=" + kind.visibleNanos/1_000_000 + "/"
                + queueMetrics.visibleNanos(kind)/1_000_000 + "/" + queueMetrics.completed(kind));
        return "action=" + actionSends + " skill_bar=" + barUpdates + " queries=" + skillQueries
                + " duplicate_inputs=" + duplicateInputs + " peak_queue=" + queueMetrics.peak() + " merged=" + queueMetrics.merged()
                + " expired=" + queueMetrics.expired() + " budget=" + queueMetrics.budget()
                + " dwell_ms_plan_total_completed=" + times;
    }

    private final io.github.augmentwild.domain.CombatHistory history = new io.github.augmentwild.domain.CombatHistory();
    private final Map<UUID,Double> healing = new HashMap<>();
    double healed(UUID id) { return healing.getOrDefault(id,0d); }
    private final java.util.Set<UUID> low = new java.util.HashSet<>();
    private static final java.time.format.DateTimeFormatter UTC = java.time.format.DateTimeFormatter
            .ofPattern("HH:mm:ss").withZone(java.time.ZoneOffset.UTC);

    private UUID match;
    private long sequence;
    private java.util.function.LongSupplier elapsed = () -> 0L;

    void startJournal(UUID match,java.util.function.LongSupplier elapsed) {
        this.match = match;
        this.elapsed = elapsed;
        sequence = 0;
    }

    CombatEvent event(UUID actor,UUID target,String source,CombatEvent.Kind kind,String cause,String augment,String skill,
                      UUID root,double original,double finalDamage,double hp,double absorption,double shield,double gain,
                      boolean cancelled,boolean death,boolean critical,boolean blocked,boolean unknown) {
        return new CombatEvent(++sequence,match,java.time.Instant.now(),elapsed.getAsLong(),actor,target,source,kind,cause,
                augment,skill,root,original,finalDamage,hp,absorption,shield,gain,cancelled,death,critical,blocked,unknown);
    }

    java.util.List<CombatEvent> events(UUID id) { return history.events(id); }

    void record(Player p,CombatEvent event,String text) {
        history.record(p.getUniqueId(),event,UTC.format(event.occurred()) + "  " + text);
    }

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
            players.computeIfAbsent(p.getUniqueId(), ignored -> new View(queueMetrics)).layers.until(Layer.INCOMING,Component.text(text),expiry);
    }

    void crisis(Player p, long now) {
        UUID id = p.getUniqueId();
        var max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (max == null || p.getHealth() > .25 * max.getValue() || !PlayerUi.enabled(p,PlayerUi.Option.CRISIS)) {
            if (low.remove(id)) remove(id,Layer.CRISIS);
        } else if (p.getHealth() > 0 && low.add(id)) {
            players.computeIfAbsent(id, ignored -> new View(queueMetrics)).layers.until(Layer.CRISIS,Component.text("생존 위기 · 체력 25% 이하",NamedTextColor.RED),now+1_000_000_000L);
            if (PlayerUi.enabled(p,PlayerUi.Option.SOUND)) p.playSound(p.getLocation(),org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS,.5f,.7f);
        }
    }

    void border(Player p, io.github.augmentwild.domain.Arena area, boolean shrinking) {
        var loc = p.getLocation();
        double x = loc.getX(), z = loc.getZ();
        double distance = io.github.augmentwild.domain.CombatNavigation.edgeDistance(area,x,z);
        boolean inside = area.contains(x,z,0);
        if (inside && (!PlayerUi.enabled(p,PlayerUi.Option.BORDER) || distance > 15)) {
            remove(p.getUniqueId(),Layer.BORDER);
            return;
        }
        players.computeIfAbsent(p.getUniqueId(),ignored -> new View(queueMetrics)).outside = !inside;
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
        final HudMessages<Component> layers;
        View(HudMessages.Metrics metrics) { layers = new HudMessages<>(metrics); }
        Component sent, skills, skillSent;
        BossBar skillBar;
        long lastInputSound = Long.MIN_VALUE;
        boolean outside;
        long refresh;
    }

    void set(UUID id, Layer layer, Component message) {
        var view = players.computeIfAbsent(id, ignored -> new View(queueMetrics));
        if (layer == Layer.SKILL) view.skills = message;
        else view.layers.set(layer, message);
    }

    void skillInput(Player p,String result) { input(p,result,null); }

    void input(Player p,String result,Boolean success) {
        if (!PlayerUi.enabled(p,PlayerUi.Option.SKILL)) return;
        var view = players.computeIfAbsent(p.getUniqueId(),ignored -> new View(queueMetrics));
        long now = System.nanoTime();
        view.layers.offer(Kind.INPUT,result,now,ignored -> Component.text(result,
                Boolean.FALSE.equals(success) ? NamedTextColor.RED : NamedTextColor.AQUA));
        if (success != null && PlayerUi.enabled(p,PlayerUi.Option.SOUND)
                && (view.lastInputSound == Long.MIN_VALUE || now - view.lastInputSound >= 380_000_000L)) {
            p.playSound(p.getLocation(),success ? org.bukkit.Sound.UI_BUTTON_CLICK : org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS,.28f,success ? 1.5f : .7f);
            view.lastInputSound = now;
        }
    }

    void notification(Player p,Kind kind,String id,String label) {
        var view = players.computeIfAbsent(p.getUniqueId(),ignored -> new View(queueMetrics));
        view.layers.offer(kind,id,System.nanoTime(),count -> Component.text(label + (count > 1 ? " ×" + count : ""),
                kind == Kind.SUPPLY ? NamedTextColor.GOLD : NamedTextColor.LIGHT_PURPLE));
    }

    void phase(UUID id,String text) {
        players.computeIfAbsent(id,ignored -> new View(queueMetrics)).layers.until(Layer.PHASE,
                Component.text(text,NamedTextColor.GOLD),System.nanoTime()+2_500_000_000L);
    }

    void danger(Player p,long remainingMillis) {
        if (remainingMillis > 0) players.computeIfAbsent(p.getUniqueId(),ignored -> new View(queueMetrics))
                .layers.until(Layer.DANGER,Component.text("위험 · 폭발 범위를 벗어나세요",NamedTextColor.RED),
                        System.nanoTime()+Math.min(remainingMillis,450)*1_000_000L);
    }

    void rescued(Player p,String name) {
        UUID id = p.getUniqueId();
        record(p,event(null,id,null,CombatEvent.Kind.RESCUE,"DEATH_PREVENTED",null,null,null,
                Double.NaN,Double.NaN,0,0,0,0,false,false,false,false,false),"치명 피해 방지 · " + name);
        players.computeIfAbsent(p.getUniqueId(),ignored -> new View(queueMetrics)).layers.until(Layer.RESCUE,Component.text("치명 피해 방지 · " + name),System.nanoTime()+1_200_000_000L);
    }
    void heal(Player player,double before,double after) {
        heal(player,before,after,"UNCLASSIFIED",null,Double.NaN);
    }
    void heal(Player player,double before,double after,String cause,String augment,double attempted) {
        if (after <= before) return;
        healing.merge(player.getUniqueId(),after-before,Double::sum);
        String message = String.format(java.util.Locale.ROOT,"회복 +%.1f HP",after-before);
        UUID id = player.getUniqueId();
        record(player,event(null,id,null,CombatEvent.Kind.HEAL,cause,augment,null,null,
                attempted,Double.NaN,0,0,0,after-before,false,false,false,false,false),message + " · " + cause);
        damage(player,message);
    }
    void damage(Player p, String text) {
        if (PlayerUi.enabled(p, PlayerUi.Option.DAMAGE))
            players.computeIfAbsent(p.getUniqueId(), ignored -> new View(queueMetrics)).layers.offer(Kind.COMBAT,text,
                    System.nanoTime(),ignored -> Component.text(text));
    }

    void activated(Player player,String id,String name,boolean activeCast) {
        UUID owner = player.getUniqueId();
        record(player,event(owner,owner,null,CombatEvent.Kind.PROC,"PROC",id,activeCast ? id : null,null,
                Double.NaN,Double.NaN,0,0,0,0,false,false,false,false,false),"발동 · " + name);
        if (!activeCast && PlayerUi.enabled(player,PlayerUi.Option.PROC))
            notification(player,Kind.PROC,id,"증강 발동 · " + name);
    }

    void proc(Player player, String name) {
        if (PlayerUi.enabled(player,PlayerUi.Option.PROC)) notification(player,Kind.PROC,name,name);
    }

    void suspend(UUID id,boolean respawning) {
        View view = players.get(id);
        if (view == null) return;
        for (var kind : Kind.values()) if (!respawning || kind != Kind.INPUT) view.layers.remove(kind);
        for (var layer : java.util.List.of(Layer.BORDER,Layer.DANGER,Layer.CRISIS,Layer.INCOMING,
                Layer.TRACK,Layer.AUTO,Layer.STATUS,Layer.SCOUT,Layer.SUPPLY,Layer.SELECTION,Layer.SHIELD))
            view.layers.remove(layer);
        view.outside = false;
        low.remove(id);
    }

    void remove(UUID id, Layer layer) {
        View view = players.get(id);
        if (view == null) return;
        if (layer == Layer.SKILL) view.skills = null;
        else view.layers.remove(layer);
    }

    void render(Player player, long now) {
        View view = players.get(player.getUniqueId());
        if (view == null) return;
        if (!PlayerUi.enabled(player,PlayerUi.Option.PROC)) view.layers.remove(Kind.PROC);
        if (!PlayerUi.enabled(player,PlayerUi.Option.DAMAGE)) view.layers.remove(Kind.COMBAT);
        if (!PlayerUi.enabled(player,PlayerUi.Option.SKILL)) view.layers.remove(Kind.INPUT);
        if (!PlayerUi.enabled(player,PlayerUi.Option.GROWTH)) { view.layers.remove(Kind.GROWTH); view.layers.remove(Kind.MILESTONE); }
        if (!PlayerUi.enabled(player,PlayerUi.Option.SUPPLY)) view.layers.remove(Kind.SUPPLY);
        if (!PlayerUi.enabled(player,PlayerUi.Option.INCOMING)) view.layers.remove(Layer.INCOMING);
        if (!PlayerUi.enabled(player,PlayerUi.Option.AUTO)) view.layers.remove(Layer.AUTO);
        renderSkills(player,view);
        Component warning = view.outside ? view.layers.layer(Layer.BORDER,now) : null;
        Component next = view.layers.current(now,warning);
        if (!Objects.equals(next, view.sent) || next != null && now - view.refresh >= REFRESH_NANOS) {
            player.sendActionBar(next == null ? Component.empty() : next);
            actionSends++;
            view.sent = next;
            view.refresh = now;
        }
        if (next == null && view.skills == null && view.layers.queued() == 0) players.remove(player.getUniqueId());
    }

    private void renderSkills(Player player, View view) {
        var skills = PlayerUi.enabled(player,PlayerUi.Option.SKILL) ? view.skills : null;
        if (skills == null) {
            if (view.skillBar != null) { player.hideBossBar(view.skillBar); barUpdates++; }
            view.skillBar = null;
            view.skillSent = null;
        } else if (!skills.equals(view.skillSent)) {
            if (view.skillBar == null) {
                view.skillBar = BossBar.bossBar(skills,1,BossBar.Color.YELLOW,BossBar.Overlay.PROGRESS);
                player.showBossBar(view.skillBar);
            } else view.skillBar.name(skills);
            barUpdates++;
            view.skillSent = skills;
        }
    }

    void clear(UUID id, Player player) {
        low.remove(id);
        View view = players.remove(id);
        if (view == null || player == null) return;
        if (view.sent != null) player.sendActionBar(Component.empty());
        if (view.skillBar != null) player.hideBossBar(view.skillBar);
    }

    void clear(JavaPlugin plugin) {
        history.clear();
        healing.clear();
        low.clear();
        for (UUID id : java.util.List.copyOf(players.keySet())) clear(id, plugin.getServer().getPlayer(id));
        queueMetrics = new HudMessages.Metrics();
        actionSends = barUpdates = skillQueries = duplicateInputs = 0;
    }
}
