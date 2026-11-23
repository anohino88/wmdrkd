package io.github.augmentwild.paper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Match-local presentation only. Public models contain no augment identity. */
final class CombatCinematics {
    enum Form {
        DANGER(0,"rune",Particle.FLAME,Sound.ENTITY_TNT_PRIMED,12),
        GUARD(1,"fracture",Particle.CRIT,Sound.ITEM_SHIELD_BLOCK,8),
        HIT(1,"impact",Particle.DAMAGE_INDICATOR,Sound.ENTITY_PLAYER_ATTACK_WEAK,6),
        HEAVY(1,"impact",Particle.CRIT,Sound.ENTITY_PLAYER_ATTACK_STRONG,8),
        CRITICAL(1,"fracture",Particle.CRIT,Sound.ENTITY_PLAYER_ATTACK_CRIT,9),
        ARC(1,"crescent",Particle.SWEEP_ATTACK,Sound.ENTITY_PLAYER_ATTACK_SWEEP,8),
        BOLT(1,"bolt",Particle.ELECTRIC_SPARK,Sound.BLOCK_AMETHYST_BLOCK_HIT,5),
        TRAIL(5,"bolt",Particle.END_ROD,null,4),
        BLAST(1,"impact",Particle.CRIT,Sound.ENTITY_GENERIC_EXPLODE,10),
        RETURN(2,"time",Particle.REVERSE_PORTAL,Sound.ENTITY_ENDERMAN_TELEPORT,9),
        VOID(2,"helix",Particle.PORTAL,Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE,10),
        RESCUE(1,"fire",Particle.FLAME,Sound.ITEM_TOTEM_USE,12);
        final int priority, duration;
        final String model;
        final Particle particle;
        final Sound sound;
        Form(int priority,String model,Particle particle,Sound sound,int duration) {
            this.priority = priority;
            this.model = model;
            this.particle = particle;
            this.sound = sound;
            this.duration = duration;
        }
    }
    private record Key(UUID source, UUID target, UUID origin, String effect, Form form, UUID world, int x, int y, int z) {}
    private record Cue(Key key, Player source, Player target, Location point, double radius, boolean particles, boolean sound) {}
    private record Pair(UUID viewer, UUID source) {}
    private record Display(Cue cue, ItemDisplay model, List<Player> viewers, long started) {}
    @FunctionalInterface interface Spawn {
        ItemDisplay model(Location point, String name, Transformation pose);
    }
    interface Audience {
        Iterable<? extends Player> nearby(Location location);
    }

    private static final int RANGE = 24, MAX_QUEUED = 36, MAX_SOUNDS = 2;
    private final JavaPlugin plugin;
    private final Predicate<Player> playable, packed;
    private final BooleanSupplier running, room;
    private final Spawn spawn;
    private final Audience audience;
    private final Map<Key,Cue> pending = new LinkedHashMap<>();
    private final List<Display> active = new ArrayList<>();
    private record Audible(UUID listener, UUID source, Form form) {}
    private final Map<Audible,Long> recentSounds = new HashMap<>();
    private final Map<Pair,Boolean> sourceSight = new HashMap<>();
    private final Map<UUID,Integer> particleBudget = new HashMap<>();
    private final Map<UUID,Integer> ringBudget = new HashMap<>();
    private final Map<UUID,Integer> soundBudget = new HashMap<>();
    private long received, duplicate, dropped, fallback, sent, samples, sounds, updates, removed, hidden;
    private long tick;

    CombatCinematics(JavaPlugin plugin, Predicate<Player> playable, Predicate<Player> packed,
                      BooleanSupplier running, BooleanSupplier room, Spawn spawn, Audience audience) {
        this.plugin = plugin;
        this.playable = playable;
        this.packed = packed;
        this.running = running;
        this.room = room;
        this.spawn = spawn;
        this.audience = audience;
    }

    private final Map<String,Long> droppedByRole = new LinkedHashMap<>();

    private void recordDrop(Key key) {
        dropped++;
        String role = key.form != Form.DANGER
                && io.github.augmentwild.augment.AugmentCombinations.UNIQUE.containsKey(key.effect) ? "synergy" : switch (key.form) {
            case DANGER -> "warning";
            case HIT, HEAVY, CRITICAL, GUARD, ARC, BOLT, BLAST, RESCUE -> "impact";
            case RETURN, VOID -> "active";
            case TRAIL -> "decoration";
        };
        droppedByRole.merge(role,1L,Long::sum);
    }

    void offer(Player source, Player target, UUID origin, String effect, Form form, Location point, double radius,
               boolean particles, boolean sound) {
        if (!running.getAsBoolean() || !eligible(source) || !source.getWorld().equals(point.getWorld())
                || !point.isChunkLoaded() || !particles && !sound) return;
        received++;
        var key = new Key(source.getUniqueId(),target == null ? null : target.getUniqueId(),origin,effect,form,
                point.getWorld().getUID(),point.getBlockX(),point.getBlockY(),point.getBlockZ());
        if (pending.containsKey(key)) { duplicate++; return; }
        if (pending.size() >= MAX_QUEUED) {
            Key lowest = pending.keySet().stream().max(Comparator.comparingInt(k -> k.form.priority)).orElseThrow();
            if (lowest.form.priority <= form.priority) {
                recordDrop(key);
                return;
            }
            recordDrop(lowest);
            pending.remove(lowest);
        }
        pending.put(key,new Cue(key,source,target,point.clone(),radius,particles,sound));
    }

    private boolean eligible(Player viewer) {
        return viewer.isOnline() && viewer.getGameMode() != GameMode.SPECTATOR && playable.test(viewer);
    }

    private static boolean sees(Location eye, Location at) {
        var world = eye.getWorld();
        int minX = Math.min(eye.getBlockX(),at.getBlockX()) >> 4;
        int maxX = Math.max(eye.getBlockX(),at.getBlockX()) >> 4;
        int minZ = Math.min(eye.getBlockZ(),at.getBlockZ()) >> 4;
        int maxZ = Math.max(eye.getBlockZ(),at.getBlockZ()) >> 4;
        // Paper ray tracing may touch chunk data. Never trace through a region that is not resident.
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++)
                if (!world.isChunkLoaded(x,z)) return false;
        var delta = at.toVector().subtract(eye.toVector());
        double length = delta.length();
        return length < .05 || world.rayTraceBlocks(eye,delta.multiply(1 / length),
                length - .04,FluidCollisionMode.NEVER,true) == null;
    }

    private boolean visible(Player viewer, Cue cue) {
        if (!eligible(viewer) || !viewer.getWorld().equals(cue.point.getWorld())
                || viewer.getLocation().distanceSquared(cue.point) > RANGE * RANGE) return false;
        if (cue.target != null && !viewer.getUniqueId().equals(cue.key.target)
                && (!viewer.canSee(cue.target) || cue.target.isInvisible()
                    || !cue.target.getWorld().equals(cue.point.getWorld()))) return false;
        if (viewer.getUniqueId().equals(cue.key.source)) return sees(viewer.getEyeLocation(),cue.point);
        if (!viewer.canSee(cue.source) || cue.source.isInvisible()) return false;
        var eye = viewer.getEyeLocation();
        Location source = cue.source.getEyeLocation();
        var pair = new Pair(viewer.getUniqueId(),cue.key.source);
        return sourceSight.computeIfAbsent(pair,k -> eye.distanceSquared(source) <= RANGE * RANGE && sees(eye,source))
                && sees(eye,cue.point);
    }

    int size() { return active.size(); }

    void tick() {
        tick++;
        recentSounds.values().removeIf(until -> until <= tick);
        if (!running.getAsBoolean()) { clear(); return; }
        soundBudget.clear();
        ringBudget.clear();
        sourceSight.clear();
        particleBudget.clear();
        for (var iterator = active.iterator(); iterator.hasNext();) {
            Display display = iterator.next();
            Cue cue = display.cue;
            long age = tick - display.started;
            if (age >= cue.key.form.duration || !display.model.isValid() || !eligible(cue.source)
                    || !cue.point.getWorld().equals(cue.source.getWorld())) {
                remove(display);
                iterator.remove();
                continue;
            }
            display.viewers.removeIf(viewer -> {
                if (visible(viewer,cue) && modelFor(viewer,cue)) return false;
                viewer.hideEntity(plugin,display.model);
                hidden++;
                return true;
            });
            if (display.viewers.isEmpty()) { remove(display); iterator.remove(); continue; }
            if (age == 1 || age == cue.key.form.duration / 2) {
                display.model.setInterpolationDelay(0);
                display.model.setInterpolationDuration(cue.key.form.duration / 2);
                display.model.setTransformation(pose(cue,age == 1 ? 1 : 2));
                updates++;
            }
        }
        if (!pending.isEmpty()) {
            var batch = new ArrayList<>(pending.values());
            pending.clear();
            batch.sort(Comparator.comparingInt(cue -> cue.key.form.priority));
            for (var cue : batch) deliver(cue);
        }
    }

    private void deliver(Cue cue) {
        if (!eligible(cue.source) || !cue.source.getWorld().equals(cue.point.getWorld()) || !cue.point.isChunkLoaded()) return;
        List<Player> viewers = new ArrayList<>();
        Boolean needsModel = null;
        boolean targetSaw = false;
        for (Player viewer : audience.nearby(cue.point)) {
            if (!visible(viewer,cue)) continue;
            if (viewer.getUniqueId().equals(cue.key.target)) targetSaw = true;
            boolean modelShown = false;
            if (cue.particles && cue.key.form != Form.TRAIL && modelFor(viewer,cue)) {
                if (needsModel == null) needsModel = reserve(cue);
                if (needsModel) {
                    viewers.add(viewer);
                    modelShown = true;
                }
            }
            if (!modelShown && cue.particles && (cue.key.form == Form.DANGER || PlayerUi.enabled(viewer,PlayerUi.Option.PARTICLES))) {
                particle(viewer,cue.key.form.particle,cue.point,1);
                fallback++;
            }
            if (cue.radius > 0 && cue.particles) ring(viewer,cue);
            if (cue.sound) sound(viewer,cue.key.source,cue.key.form,cue.point);
        }
        // A concealed source may give the victim generic feedback solely at their current position.
        if (!targetSaw && cue.target != null && eligible(cue.target)
                && cue.target.getWorld().equals(cue.point.getWorld())
                && cue.target.getLocation().distanceSquared(cue.point) < 9
                && cue.key.form != Form.DANGER && cue.key.form != Form.TRAIL) {
            Location own = cue.target.getEyeLocation();
            Form feedback = cue.key.form == Form.GUARD ? Form.GUARD : Form.HIT;
            if (cue.particles && PlayerUi.enabled(cue.target,PlayerUi.Option.PARTICLES))
                particle(cue.target,feedback.particle,own,2);
            if (cue.sound) sound(cue.target,cue.key.source,feedback,own);
        }
        if (viewers.isEmpty()) return;
        ItemDisplay model = spawn.model(cue.point,cue.key.form.model,pose(cue,0));
        active.add(new Display(cue,model,viewers,tick));
        for (var viewer : viewers) viewer.showEntity(plugin,model);
    }

    private boolean reserve(Cue cue) {
        if (room.getAsBoolean()) return true;
        var lower = active.stream().filter(display -> display.cue.key.form.priority > cue.key.form.priority)
                .max(Comparator.comparingInt(display -> display.cue.key.form.priority));
        if (lower.isEmpty()) return false;
        remove(lower.get());
        active.remove(lower.get());
        return true;
    }

    private static Transformation pose(Cue cue,int stage) {
        var form = cue.key.form;
        float yaw = (float)Math.toRadians(-cue.point.getYaw());
        float t = stage / 2f;
        boolean contact = stage == 1;
        float scale = stage == 0 ? .18f : contact ? 1f : .07f;
        var offset = new Vector3f();
        var rotation = new Quaternionf().rotateY(yaw);
        var shape = new Vector3f(scale);
        switch (form) {
            case ARC -> {
                offset.set(-.38f + t*.76f,.23f*t,.24f*t);
                offset.rotateY(yaw);
                rotation.rotateY(-1.1f+2.2f*t).rotateZ(-.42f+.7f*t);
                shape.mul(1.75f,contact ? .66f : .28f,1.32f);
            }
            case GUARD -> {
                offset.set(0,.25f*t,.1f*t);
                rotation.rotateZ(-.52f*t).rotateX(.28f*t);
                shape.mul(1.4f,1.1f,.75f);
            }
            case BLAST,DANGER -> {
                offset.y = .06f;
                rotation.rotateY(.8f*t);
                shape.mul(1.4f,.3f,1.4f);
            }
            case BOLT,TRAIL -> {
                offset.set(0,-.04f,.45f*t).rotateY(yaw);
                rotation.rotateZ(1.5f*t);
                shape.mul(.48f,.48f,1.55f);
            }
            case RETURN -> {
                offset.set(0,.35f*(1-t),-.25f*t);
                rotation.rotateY(-2.4f*t);
                shape.mul(.95f,.74f,.95f);
            }
            case VOID -> {
                offset.y = .33f*(1-t);
                rotation.rotateX(.5f+t).rotateY(2.7f*t);
                shape.mul(1.2f,.86f,1.2f);
            }
            case RESCUE -> {
                offset.y = .6f*t;
                rotation.rotateX(-.32f*t);
                shape.mul(1.45f,1.2f,.7f);
            }
            case HIT,HEAVY,CRITICAL -> {
                offset.y = .2f*t;
                rotation.rotateZ(.35f*t);
                shape.mul(form == Form.HEAVY ? 1.24f : .83f,form == Form.CRITICAL ? 1.4f : 1f,.8f);
            }
        }
        return new Transformation(offset,rotation,shape,new Quaternionf());
    }

    private boolean modelFor(Player viewer,Cue cue) {
        var quality = PlayerUi.quality(viewer);
        int range = quality == PlayerUi.Quality.STANDARD ? 17 : RANGE;
        return packed.test(viewer) && !PlayerUi.reducedFlash(viewer) && PlayerUi.enabled(viewer,PlayerUi.Option.PARTICLES)
                && quality != PlayerUi.Quality.LOW && viewer.getLocation().distanceSquared(cue.point) <= range * range;
    }

    private void ring(Player viewer,Cue cue) {
        if (cue.key.form != Form.DANGER && !PlayerUi.enabled(viewer,PlayerUi.Option.PARTICLES)) return;
        var id = viewer.getUniqueId();
        int count = Math.min(PlayerUi.quality(viewer).samples,32 - ringBudget.getOrDefault(id,0));
        if (count <= 0) return;
        ringBudget.merge(id,count,Integer::sum);
        var eye = viewer.getEyeLocation();
        for (int index = 0; index < count; index++) {
            double angle = Math.TAU * index / count;
            var at = cue.point.clone().add(cue.radius*Math.cos(angle),.15,cue.radius*Math.sin(angle));
            if (at.isChunkLoaded() && eye.distanceSquared(at) <= RANGE*RANGE && sees(eye,at))
                particle(viewer,cue.key.form.particle,at,1);
        }
    }

    private void particle(Player viewer,Particle particle,Location at,int count) {
        // An explicit ring is a warning channel. Cosmetic hits and trails share a separate cap.
        var id = viewer.getUniqueId();
        if (particle != Form.DANGER.particle && particleBudget.getOrDefault(id,0) >= 16) return;
        particleBudget.merge(id,1,Integer::sum);
        viewer.spawnParticle(particle,at,count,0,0,0,0);
        sent++;
        samples += count;
    }

    private void sound(Player viewer,UUID source,Form form,Location at) {
        if (form.sound == null || !PlayerUi.enabled(viewer,PlayerUi.Option.SOUND) || soundBudget.getOrDefault(viewer.getUniqueId(),0) >= MAX_SOUNDS) return;
        var key = new Audible(viewer.getUniqueId(),source,form);
        if (recentSounds.containsKey(key)) return;
        recentSounds.put(key,tick+(form == Form.DANGER ? 30 : 4));
        soundBudget.merge(viewer.getUniqueId(),1,Integer::sum);
        viewer.playSound(at,form.sound,.36f,1f);
        sounds++;
    }

    private void remove(Display display) {
        display.model.remove();
        removed++;
    }

    void cancelWarning(UUID source,String effect,UUID origin) {
        Predicate<Key> matches = key -> key.form == Form.DANGER && key.source.equals(source)
                && key.effect.equals(effect) && (origin == null || origin.equals(key.origin));
        pending.keySet().removeIf(matches);
        active.removeIf(display -> {
            if (!matches.test(display.cue.key)) return false;
            remove(display);
            return true;
        });
    }

    void forget(UUID player) {
        recentSounds.keySet().removeIf(key -> key.listener.equals(player) || key.source.equals(player));
        pending.keySet().removeIf(key -> key.source.equals(player) || player.equals(key.target));
        active.removeIf(display -> {
            if (display.cue.key.source.equals(player) || player.equals(display.cue.key.target)) {
                remove(display);
                return true;
            }
            display.viewers.removeIf(viewer -> {
                if (!viewer.getUniqueId().equals(player)) return false;
                viewer.hideEntity(plugin,display.model);
                return true;
            });
            if (!display.viewers.isEmpty()) return false;
            remove(display);
            return true;
        });
    }

    void clear() {
        recentSounds.clear();
        pending.clear();
        active.forEach(this::remove);
        active.clear();
        soundBudget.clear();
        ringBudget.clear();
        sourceSight.clear();
        particleBudget.clear();
    }

    void resetMetrics() {
        droppedByRole.clear();
        received = duplicate = dropped = fallback = sent = samples = sounds = updates = removed = hidden = 0;
    }

    String metrics() {
        return "public_requests=" + received + " duplicate=" + duplicate + " queue_drops=" + dropped
                + " fallback=" + fallback + " particle_calls=" + sent + " particle_count=" + samples
                + " sound_calls=" + sounds + " public_updates=" + updates + " public_removed=" + removed + " visibility_revoked=" + hidden
                + " dropped_warning=" + droppedByRole.getOrDefault("warning",0L)
                + " dropped_impact=" + droppedByRole.getOrDefault("impact",0L)
                + " dropped_active=" + droppedByRole.getOrDefault("active",0L)
                + " dropped_synergy=" + droppedByRole.getOrDefault("synergy",0L)
                + " dropped_decoration=" + droppedByRole.getOrDefault("decoration",0L);
    }
}
