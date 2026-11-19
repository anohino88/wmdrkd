package io.github.augmentwild.probe;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import io.github.augmentwild.augment.DamageChain;
import io.github.augmentwild.augment.LinkedDamageBudget;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Pig;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Bounded real-entity workload. There are no clients, inventories, or match participants. */
final class SyntheticCombatLoad implements Listener {
    private static final long SEED = 67_026L;
    private final int warmup, samples, repetitions;
    private Random sequence;
    private int repetition = 1;
    private static final int REQUESTS_PER_ORIGIN = 6;
    private final JavaPlugin plugin;
    private final Location site;
    private final Chunk chunk;
    private final UUID match = new UUID(0, SEED);
    private final DamageChain chain = new DamageChain(2, 10);
    private final List<Pig> pigs = new ArrayList<>();
    private final List<Double> steps = new ArrayList<>();
    private final List<Double> serverTicks = new ArrayList<>();
    private BukkitTask pulses;
    private boolean retained;
    private int measuredPulse;
    private int size = 2;
    private int rounds;
    private int attempted;
    private int events;
    private int landed;
    private double healthDamage;

    SyntheticCombatLoad(JavaPlugin plugin, Location site) {
        this.plugin = plugin;
        boolean extended = "1".equals(System.getenv("AW_EXTENDED_LOAD"));
        warmup = extended ? 50 : 0;
        samples = extended ? 300 : 30;
        repetitions = extended ? 2 : 1;
        this.site = new Location(site.getWorld(), (site.getBlockX() & ~15) + 5.5, site.getBlockY() + 24.0,
                (site.getBlockZ() & ~15) + 5.5);
        this.chunk = this.site.getChunk();
    }

    void start() {
        if (!chunk.addPluginChunkTicket(plugin)) throw new AssertionError("Could not retain synthetic combat chunk");
        retained = true;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        next();
    }

    private void next() {
        var world = site.getWorld();
        sequence = new Random(SEED ^ size);
        try {
            for (int i = 0; i < size; i++) {
                Pig pig = world.spawn(site.clone().add(i % 4, 1, i / 4), Pig.class);
                pig.setAI(false);
                pig.setGravity(false);
                pig.setSilent(true);
                pig.setCollidable(false);
                pig.setRemoveWhenFarAway(false);
                pig.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1);
                pigs.add(pig);
            }
            pulses = plugin.getServer().getScheduler().runTaskTimer(plugin, this::pulse, 1L, 1L);
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        }
    }

    private void pulse() {
        try {
            long time = System.nanoTime();
            int offset = sequence.nextInt(size);
            int distance = 1 + sequence.nextInt(size - 1);
            for (int source = 0; source < size; source++) {
                Pig attacker = pigs.get((source + offset) % size);
                Pig victim = pigs.get((source + offset + distance) % size);
                if (!attacker.isValid() || !victim.isValid()) throw new AssertionError("Synthetic entity unavailable at pulse " + rounds);
                // Independent fixture origins start vulnerable even when an empty server does not tick the entity.
                // The next six requests use normal immunity and never reset it within a chain.
                victim.setNoDamageTicks(0);
                var origin = new DamageChain.Origin(DamageChain.Attack.MELEE, 4, 0, 0);
                var budget = new LinkedDamageBudget(8, false, victim.getUniqueId(), 0);
                chain.root(new DamageChain.Root(origin, budget), () -> {
                    for (int hit = 0; hit < REQUESTS_PER_ORIGIN; hit++) damage(attacker, victim, budget);
                });
                victim.setHealth(10);
            }
            if (++rounds > warmup) steps.add((System.nanoTime() - time) / 1_000_000.0);
            if (rounds == warmup + samples) pulses.cancel();
        } catch (RuntimeException | Error failure) {
            close();
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
        var context = chain.current();
        if (context == null || !pigs.contains(event.getEntity())) return;
        var cause = event.getDamageSource().getCausingEntity();
        if (cause == null || !context.attacker().equals(cause.getUniqueId())
                || !context.victim().equals(event.getEntity().getUniqueId())) {
            throw new AssertionError("Synthetic damage was attributed to the wrong source or victim");
        }
        events++;
    }

    @EventHandler
    public void observeTick(ServerTickEndEvent event) {
        if (rounds == measuredPulse) return;
        measuredPulse = rounds;
        if (rounds == warmup) {
            attempted = events = landed = 0;
            healthDamage = 0;
        }
        if (rounds <= warmup) return;
        serverTicks.add(event.getTickDuration());
        if (rounds < warmup + samples) return;
        int oldSize = size;
        var removed = List.copyOf(pigs);
        cleanup();
        long remaining = removed.stream().filter(Pig::isValid).count();
        if (serverTicks.size() != samples || events != oldSize * samples || landed != oldSize * samples || remaining > 0) {
            close();
            throw new AssertionError("Unexpected native outcomes: events=" + events + " landed=" + landed
                    + " expected=" + oldSize * samples + " remaining=" + remaining + " tick_samples=" + serverTicks.size());
        }
        plugin.getLogger().info(String.format(Locale.ROOT,
                "SYNTHETIC_LOAD_TEST seed=%d repetition=%d entities=%d warmup=%d ticks=%d attempts=%d events=%d landed=%d health_damage=%.1f callback_p95_ms=%.3f server_tick_p50_ms=%.3f server_tick_p95_ms=%.3f server_tick_p99_ms=%.3f server_tick_max_ms=%.3f server_ticks_over_50=%d/%d entities_remaining=%d",
                SEED, repetition, oldSize, warmup, samples, attempted, events, landed, healthDamage, percentile(steps, .95),
                percentile(serverTicks, .5), percentile(serverTicks, .95), percentile(serverTicks, .99),
                Collections.max(serverTicks), serverTicks.stream().filter(ms -> ms > 50).count(), serverTicks.size(), remaining));
        if (oldSize == 16) {
            if (repetition == repetitions) {
                close();
                plugin.getLogger().info("AW_COMBAT_LOAD_PASS");
                return;
            }
            repetition++;
            size = 2;
        } else size *= 2;
        rounds = measuredPulse = attempted = events = landed = 0;
        healthDamage = 0;
        steps.clear();
        serverTicks.clear();
        plugin.getServer().getScheduler().runTask(plugin, this::next);
    }

    void close() {
        cleanup();
        HandlerList.unregisterAll(this);
        if (retained && !chunk.removePluginChunkTicket(plugin)) throw new AssertionError("Synthetic combat chunk ticket missing at cleanup");
        retained = false;
    }

    private void cleanup() {
        if (pulses != null) pulses.cancel();
        pigs.forEach(Pig::remove);
        pigs.clear();
    }

    private static double percentile(List<Double> values, double fraction) {
        var sorted = values.stream().sorted().toList();
        return sorted.get((int) Math.ceil(sorted.size() * fraction) - 1);
    }
}
