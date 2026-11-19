package io.github.augmentwild.probe;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import io.github.augmentwild.augment.DamageChain;
import io.github.augmentwild.augment.LinkedDamageBudget;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Pig;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Bounded real-entity workload. There are no clients, inventories, or match participants. */
final class SyntheticCombatLoad implements Listener {
    private static final int TICKS = 30;
    private static final int REQUESTS_PER_ORIGIN = 6;
    private final JavaPlugin plugin;
    private final Location site;
    private final UUID match = UUID.randomUUID();
    private final DamageChain chain = new DamageChain(2, 10);
    private final List<Pig> pigs = new ArrayList<>();
    private final List<Double> steps = new ArrayList<>();
    private final List<Double> serverTicks = new ArrayList<>();
    private int size = 2;
    private int rounds;
    private int attempted;
    private int events;
    private int landed;
    private double healthDamage;

    SyntheticCombatLoad(JavaPlugin plugin, Location site) {
        this.plugin = plugin;
        this.site = site;
    }

    void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        next();
    }

    private void next() {
        var world = site.getWorld();
        try {
            for (int i = 0; i < size; i++) {
                Pig pig = world.spawn(site.clone().add(i % 4, 1, i / 4), Pig.class);
                pig.setAI(false);
                pig.setGravity(false);
                pig.setSilent(true);
                pigs.add(pig);
            }
            plugin.getServer().getScheduler().runTask(plugin, this::pulse);
        } catch (RuntimeException | Error failure) {
            cleanup();
            throw failure;
        }
    }

    private void pulse() {
        try {
            long time = System.nanoTime();
            for (int source = 0; source < size; source++) {
                Pig attacker = pigs.get(source);
                Pig victim = pigs.get((source + 1) % size);
                var origin = new DamageChain.Origin(DamageChain.Attack.MELEE, 4, 0, 0);
                var budget = new LinkedDamageBudget(8, false, victim.getUniqueId(), 0);
                chain.root(new DamageChain.Root(origin, budget), () -> {
                    for (int hit = 0; hit < REQUESTS_PER_ORIGIN; hit++) damage(attacker, victim, budget);
                });
                victim.setHealth(10);
            }
            steps.add((System.nanoTime() - time) / 1_000_000.0);
            rounds++;
            if (rounds < TICKS) plugin.getServer().getScheduler().runTask(plugin, this::pulse);
        } catch (RuntimeException | Error failure) {
            cleanup();
            throw failure;
        }
    }

    private void damage(Pig attacker, Pig victim, LinkedDamageBudget budget) {
        attempted++;
        var admission = budget.offer(victim.getUniqueId(), 2);
        boolean hit = chain.hit(match, attacker.getUniqueId(), victim.getUniqueId(), "load_probe", admission.allowed(), false, () -> {
            double before = victim.getHealth();
            victim.damage(admission.allowed(), attacker);
            double delta = before - victim.getHealth();
            healthDamage += delta;
            return delta > 0;
        });
        if (hit) landed++;
        else budget.refund(victim.getUniqueId(), admission.allowed());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void countEvent(EntityDamageEvent event) {
        if (chain.current() != null && pigs.contains(event.getEntity())) events++;
    }

    @EventHandler
    public void observeTick(ServerTickEndEvent event) {
        if (rounds == 0) return;
        serverTicks.add(event.getTickDuration());
        if (rounds < TICKS) return;
        int oldSize = size;
        var removed = List.copyOf(pigs);
        cleanup();
        long remaining = removed.stream().filter(Pig::isValid).count();
        if (events <= 0 || landed <= 0 || remaining > 0) throw new AssertionError("Combat load probe did not complete");
        plugin.getLogger().info(String.format(Locale.ROOT,
                "SYNTHETIC_LOAD_TEST entities=%d ticks=%d attempts=%d events=%d landed=%d health_damage=%.1f callback_p95_ms=%.3f server_tick_p95_ms=%.3f server_tick_max_ms=%.3f entities_remaining=%d",
                oldSize, TICKS, attempted, events, landed, healthDamage, percentile(steps), percentile(serverTicks),
                Collections.max(serverTicks), remaining));
        if (oldSize == 16) {
            HandlerList.unregisterAll(this);
            plugin.getLogger().info("AW_COMBAT_LOAD_PASS");
            return;
        }
        size = oldSize == 2 ? 8 : 16;
        rounds = attempted = events = landed = 0;
        healthDamage = 0;
        steps.clear();
        serverTicks.clear();
        plugin.getServer().getScheduler().runTask(plugin, this::next);
    }

    private void cleanup() {
        pigs.forEach(Pig::remove);
        pigs.clear();
    }

    private static double percentile(List<Double> values) {
        var sorted = values.stream().sorted().toList();
        return sorted.get((int) Math.ceil(sorted.size() * .95) - 1);
    }
}
