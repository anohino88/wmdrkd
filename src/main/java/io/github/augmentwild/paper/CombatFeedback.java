package io.github.augmentwild.paper;

import io.github.augmentwild.domain.CombatView;
import java.util.*;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.plugin.java.JavaPlugin;

final class CombatFeedback implements Listener {
    private record Sample(EntityDamageEvent event, Player owner, LivingEntity victim, UUID root, String label,
                          CombatView.Receipt receipt, double shield,
                          boolean critical, boolean blocked, Location impact, long time) {}

    private static final class Hit {
        final long start;
        final Location at;
        final String target;
        String label;
        double hp, absorption, shield;
        boolean critical, blocked;
        Hit(Sample s) { start = s.time; at = s.impact; target = s.victim.getName(); label = s.label; }
    }
    private final Map<UUID,Sample> pending = new HashMap<>();
    private record Heal(EntityRegainHealthEvent event, Player player, double before, double expected) {}
    private final Map<UUID,Heal> heals = new HashMap<>();
    private final Map<CombatView.OriginTarget,Hit> groups = new LinkedHashMap<>();
    private final List<Map.Entry<CombatView.OriginTarget,Hit>> ready = new ArrayList<>();
    private final Map<TextDisplay,Long> numbers = new HashMap<>();
    private final Map<UUID,Double> dealt = new HashMap<>(), received = new HashMap<>();
    private final Set<EntityDamageEvent> seen = Collections.newSetFromMap(new WeakHashMap<>());
    private final Predicate<Player> active;
    private final Predicate<Location> arena;
    private final PaperAugments augments;
    private final PaperHud hud;
    private final JavaPlugin plugin;
    private boolean floating;
    private final int maxNumbers;
    CombatFeedback(JavaPlugin plugin, Predicate<Player> active, Predicate<Location> arena, PaperAugments augments, PaperHud hud) {
        floating = plugin.getConfig().getBoolean("hud.floating-damage");
        maxNumbers = Math.clamp(plugin.getConfig().getInt("hud.max-floating-numbers",16),0,64);
        this.plugin = plugin; this.active = active; this.arena = arena; this.augments = augments; this.hud = hud;
    }
    private boolean eligible(Player p) { return active.test(p) && p.getGameMode() != GameMode.SPECTATOR; }
    @EventHandler(priority=EventPriority.LOWEST)
    public void before(EntityDamageEvent e) { settle(e.getEntity().getUniqueId()); }
    @EventHandler(priority=EventPriority.LOWEST)
    public void beforeHeal(EntityRegainHealthEvent e) { settle(e.getEntity().getUniqueId()); }
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void healed(EntityRegainHealthEvent e) {
        if (e.getEntity() instanceof Player p && eligible(p)) {
            double missing = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue() - p.getHealth();
            heals.put(p.getUniqueId(), new Heal(e,p,p.getHealth(), Math.min(missing,e.getAmount())));
        }
    }
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void damage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity v)) return;
        Player p = e.getDamageSource().getCausingEntity() instanceof Player attacker && eligible(attacker)
                && !attacker.equals(v) ? attacker : null;
        if (!(v instanceof Player enemy ? eligible(enemy) : p != null && v instanceof Enemy && arena.test(v.getLocation()))
                || !seen.add(e)) return;
        var trace = augments.trace(e);
        double abs = e.isApplicable(EntityDamageEvent.DamageModifier.ABSORPTION)
                ? Math.max(0,-e.getDamage(EntityDamageEvent.DamageModifier.ABSORPTION)) : 0;
        boolean blocked = e.isApplicable(EntityDamageEvent.DamageModifier.BLOCKING)
                && e.getDamage(EntityDamageEvent.DamageModifier.BLOCKING) < 0;
        boolean crit = trace.critical() || e instanceof EntityDamageByEntityEvent attack && attack.isCritical();
        String label = trace.synthetic() ? "연쇄 · " + augments.effectName(trace.augment())
                : trace.augment() != null ? "증강 포함" : e.getCause()==EntityDamageEvent.DamageCause.PROJECTILE ? "원거리" : "공격";
        pending.put(v.getUniqueId(), new Sample(e,p,v,trace.origin()==null ? UUID.randomUUID() : trace.origin(),label,
                new CombatView.Receipt(new CombatView.Vitals(v.getHealth(),v.getAbsorptionAmount()),
                        new CombatView.Vitals(Math.min(v.getHealth(),Math.max(0,e.getFinalDamage())), Math.min(abs,v.getAbsorptionAmount()))),
                trace.shield(),crit,blocked,v.getEyeLocation(),System.nanoTime()));
    }
    void settle(UUID id) {
        Heal h = heals.remove(id);
        if (h != null && !h.event.isCancelled()) {
            double gain = CombatView.confirmed(h.player.getHealth()-h.before,h.expected);
            if (gain > 0 && eligible(h.player)) hud.damage(h.player,"회복 +" + fmt(gain) + " HP");
        }
        Sample s = pending.remove(id);
        if (s == null || s.event.isCancelled()) return;
        var observed = s.receipt.confirm(new CombatView.Vitals(s.victim.getHealth(),s.victim.getAbsorptionAmount()),false);
        double health = observed.hp(), abs = observed.absorption();
        if (health + abs + s.shield <= 0 && !s.blocked) return;
        if (health > 0 && s.victim instanceof Player) received.merge(id,health,Double::sum);
        if (s.owner == null) return;
        if (health > 0) dealt.merge(s.owner.getUniqueId(),health,Double::sum);
        var key = new CombatView.OriginTarget(s.owner.getUniqueId(),id,s.root);
        var existing = groups.get(key);
        if (existing != null && s.time - existing.start >= CombatView.DAMAGE_WINDOW) {
            ready.add(Map.entry(key,existing));
            groups.remove(key);
        }
        var hit = groups.computeIfAbsent(key, ignored -> new Hit(s));
        if (!hit.label.equals(s.label)) hit.label = "복합 공격";
        hit.hp += health; hit.absorption += abs; hit.shield += s.shield; hit.critical |= s.critical; hit.blocked |= s.blocked;
    }
    void tick(long now) {
        finishSamples();
        Map<Player,List<String>> alerts = new HashMap<>();

        groups.entrySet().removeIf(entry -> {
            Hit h = entry.getValue();
            if (now - h.start < CombatView.DAMAGE_WINDOW) return false;
            ready.add(Map.entry(entry.getKey(),h));
            return true;
        });
        for (var entry : ready) {
            Hit h = entry.getValue();
            Player p = Bukkit.getPlayer(entry.getKey().attacker());
            if (p != null && eligible(p) && PlayerUi.enabled(p,PlayerUi.Option.DAMAGE)) {
                String text = h.target + " · " + (h.critical ? "치명타! " : "") + h.label
                        + (h.hp > 0 ? " −" + fmt(h.hp) + " HP" : "")
                        + (h.absorption > 0 ? " · 흡수 −" + fmt(h.absorption) : "")
                        + (h.shield > 0 ? " · 보호막 −" + fmt(h.shield) : "") + (h.blocked ? " · 방패 방어" : "");
                alerts.computeIfAbsent(p,ignored -> new ArrayList<>()).add(text);
                if (PlayerUi.enabled(p,PlayerUi.Option.SOUND))
                    p.playSound(p.getLocation(), h.blocked ? Sound.ITEM_SHIELD_BLOCK : Sound.ENTITY_ARROW_HIT_PLAYER,.35f,h.critical ? 1.8f : 1f);
                if (h.shield > 0 && PlayerUi.enabled(p,PlayerUi.Option.PARTICLES)) p.spawnParticle(Particle.END_ROD,h.at,3);
                if (floating && numbers.size() < maxNumbers && p.getWorld().equals(h.at.getWorld()) && h.at.isChunkLoaded()) {
                    try {
                        var number = h.at.getWorld().spawn(h.at,TextDisplay.class, d -> {
                        d.setVisibleByDefault(false); d.setPersistent(false);
                        d.setBillboard(Display.Billboard.CENTER); d.text(Component.text(text));
                    });
                        numbers.put(number,now+CombatView.DAMAGE_VISIBLE);
                        p.showEntity(plugin, number);
                    } catch (RuntimeException failure) {
                        floating = false;
                        plugin.getLogger().log(java.util.logging.Level.WARNING,"떠오르는 피해 표시 중단",failure);
                    }
                }
            }
        }
        ready.clear();
        alerts.forEach((p,lines) -> hud.damage(p,String.join(" | ",lines.stream().limit(3).toList())));
        numbers.entrySet().removeIf(entry -> {
            if (entry.getValue() > now) return false;
            entry.getKey().remove(); return true;
        });
    }
    void finishSamples() {
        List.copyOf(pending.keySet()).forEach(this::settle);
        List.copyOf(heals.keySet()).forEach(this::settle);
    }
    String totals(UUID id) { return "확인한 체력 피해 · 가한 " + fmt(dealt.getOrDefault(id,0d)) + " / 받은 " + fmt(received.getOrDefault(id,0d)); }
    private static String fmt(double v) { return String.format(Locale.ROOT,"%.1f",v); }
    void clear() {
        ready.clear(); pending.clear(); heals.clear(); groups.clear(); seen.clear(); dealt.clear(); received.clear();
        numbers.keySet().forEach(TextDisplay::remove); numbers.clear();
    }
}
