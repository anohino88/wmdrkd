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
                          boolean critical, boolean blocked, Location impact, long time, String incoming, String source) {}

    private static final class Hit {
        final long start;
        final Location at;
        final String target;
        String label;
        double hp, absorption, shield;
        boolean critical, blocked;
        Hit(Sample s) { start = s.time; at = s.impact; target = s.victim.getName(); label = s.label; }
    }
    private record Launch(UUID shooter, Location at) {}
    private final Map<Projectile,Launch> launches = new WeakHashMap<>();

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void launch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (projectile.getShooter() instanceof LivingEntity shooter && arena.test(projectile.getLocation()))
            launches.put(projectile,new Launch(shooter.getUniqueId(),projectile.getLocation()));
    }

    private String direction(EntityDamageEvent event, Player victim, boolean synthetic) {
        if (synthetic || !(event instanceof EntityDamageByEntityEvent hit)) return null;
        var source = event.getDamageSource().getCausingEntity();
        if (!(source instanceof LivingEntity) || victim.equals(source)
                || source instanceof Player player && !eligible(player)) return null;
        Location origin = null;
        if (hit.getDamager() instanceof Projectile projectile) {
            var launch = launches.get(projectile);
            if (launch != null && source.getUniqueId().equals(launch.shooter())) origin = launch.at();
        } else if (hit.getDamager().equals(source) && (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)) origin = source.getLocation();
        var at = victim.getLocation();
        if (origin == null || !at.getWorld().equals(origin.getWorld())) return null;
        String arrow = io.github.augmentwild.domain.CombatNavigation.bearing(origin.getX()-at.getX(),origin.getZ()-at.getZ(),at.getYaw());
        return arrow.isEmpty() ? null : "피격 방향 " + arrow;
    }

    private final Map<UUID,Sample> pending = new HashMap<>();
    private record Heal(EntityRegainHealthEvent event, Player player, double before, double expected) {}
    private final Map<UUID,Heal> heals = new HashMap<>();
    private final Map<CombatView.OriginTarget,Hit> groups = new LinkedHashMap<>();
    private final List<Map.Entry<CombatView.OriginTarget,Hit>> ready = new ArrayList<>();
    private final Map<TextDisplay,Long> numbers = new HashMap<>();
    private final Map<UUID,Double> dealt = new HashMap<>(), received = new HashMap<>();
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
        if (e.getEntity() instanceof Player p && eligible(p) && hud.beginObservation(e)) {
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
                || !hud.beginObservation(e)) return;
        var trace = augments.trace(e);
        boolean blocked = e.isApplicable(EntityDamageEvent.DamageModifier.BLOCKING)
                && e.getDamage(EntityDamageEvent.DamageModifier.BLOCKING) < 0;
        boolean crit = trace.critical() || e instanceof EntityDamageByEntityEvent attack && attack.isCritical();
        String label = trace.synthetic() ? "연쇄 · " + augments.effectName(trace.augment())
                : trace.augment() != null ? "증강 포함" : e.getCause()==EntityDamageEvent.DamageCause.PROJECTILE ? "원거리" : "공격";
        var source = e.getDamageSource().getCausingEntity();
        boolean environmental = source == null && switch (e.getCause()) {
            case FALL, FIRE, FIRE_TICK, LAVA, HOT_FLOOR, FREEZE, DROWNING, SUFFOCATION, VOID,
                    WORLD_BORDER, STARVATION, CONTACT, FLY_INTO_WALL, CRAMMING -> true;
            default -> false;
        };
        String incoming = v instanceof Player victim ? direction(e,victim,trace.synthetic()) : null;
        if (environmental) incoming = "환경 피해";
        String sourceName = source instanceof LivingEntity ? source.getName() : environmental ? "환경 · " + e.getCause().name() : "출처 미확인";
        pending.put(v.getUniqueId(), new Sample(e,p,v,trace.origin()==null ? UUID.randomUUID() : trace.origin(),label,
                DamageObservation.capture(e,v),
                trace.shield(),crit,blocked,v.getEyeLocation(),System.nanoTime(),incoming,sourceName));
    }
    void settle(UUID id) {
        Heal h = heals.remove(id);
        if (h != null && !h.event.isCancelled()) {
            double gain = CombatView.confirmed(h.player.getHealth()-h.before,h.expected);
            if (gain > 0 && eligible(h.player)) hud.heal(h.player,0,gain);
        }
        Sample s = pending.remove(id);
        if (s == null || s.event.isCancelled()) return;
        var observed = s.receipt.confirm(DamageObservation.read(s.victim),false);
        double health = observed.hp(), abs = observed.absorption();
        if (health + abs + s.shield <= 0 && !s.blocked) return;
        if (health + abs + s.shield > 0) {
            String debit = (health > 0 ? " 체력 −" + fmt(health) : "")
                    + (abs > 0 ? " 흡수 −" + fmt(abs) : "") + (s.shield > 0 ? " 보호막 −" + fmt(s.shield) : "");
            if (s.victim instanceof Player victim) {
                hud.record(victim,"피격 · " + s.source + debit);
                if (s.incoming != null) hud.incoming(victim,s.incoming,s.time+1_000_000_000L);
                else hud.remove(id,io.github.augmentwild.domain.HudMessages.Layer.INCOMING);
            }
            if (s.owner != null) hud.record(s.owner,"공격 · " + s.victim.getName() + " · " + s.label + debit);
        }
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
                String text = (h.blocked || h.shield > 0 ? "⬡ " : h.critical ? "✦ " : "⚔ ") + h.target + " · " + (h.critical ? "치명타! " : "") + h.label
                        + (h.hp > 0 ? " −" + fmt(h.hp) + " HP" : "")
                        + (h.absorption > 0 ? " · 흡수 −" + fmt(h.absorption) : "")
                        + (h.shield > 0 ? " · 보호막 −" + fmt(h.shield) : "") + (h.blocked ? " · 방패 방어" : "");
                alerts.computeIfAbsent(p,ignored -> new ArrayList<>()).add(text);
                if (PlayerUi.enabled(p,PlayerUi.Option.SOUND))
                    p.playSound(p.getLocation(), h.blocked ? Sound.ITEM_SHIELD_BLOCK : Sound.ENTITY_ARROW_HIT_PLAYER,.35f,h.critical ? 1.8f : 1f);
                if (h.shield > 0 && PlayerUi.enabled(p,PlayerUi.Option.PARTICLES)) p.spawnParticle(Particle.END_ROD,h.at,3);
                if (floating && numbers.size() < maxNumbers && p.getWorld().equals(h.at.getWorld()) && h.at.isChunkLoaded()) {
                    try {
                        double offset = (entry.getKey().hashCode() % 5) * .09;
                        var number = h.at.getWorld().spawn(h.at.clone().add(offset,0,-offset),TextDisplay.class, d -> {
                        d.setVisibleByDefault(false); d.setPersistent(false);
                        d.setBillboard(Display.Billboard.CENTER);
                        d.setSeeThrough(false);
                        d.text(Component.text(text,h.shield > 0 ? net.kyori.adventure.text.format.NamedTextColor.AQUA
                                : h.critical ? net.kyori.adventure.text.format.NamedTextColor.GOLD : net.kyori.adventure.text.format.NamedTextColor.WHITE));
                    });
                        var pose = number.getTransformation();
                        pose.getTranslation().y = .55f;
                        pose.getScale().mul(1.12f);
                        number.setInterpolationDelay(0);
                        number.setInterpolationDuration(12);
                        number.setTransformation(pose);
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
        launches.clear(); ready.clear(); pending.clear(); heals.clear(); groups.clear(); dealt.clear(); received.clear();
        numbers.keySet().forEach(TextDisplay::remove); numbers.clear();
    }
}
