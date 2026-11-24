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
        GUARD(1,"fracture",Particle.CRIT,Sound.ITEM_SHIELD_BLOCK,16),
        HIT(1,"impact",Particle.DAMAGE_INDICATOR,Sound.ENTITY_PLAYER_ATTACK_WEAK,12),
        HEAVY(1,"impact",Particle.CRIT,Sound.ENTITY_PLAYER_ATTACK_STRONG,18),
        CRITICAL(1,"fracture",Particle.CRIT,Sound.ENTITY_PLAYER_ATTACK_CRIT,18),
        ARC(1,"crescent",Particle.SWEEP_ATTACK,Sound.ENTITY_PLAYER_ATTACK_SWEEP,24),
        BOLT(1,"bolt",Particle.ELECTRIC_SPARK,Sound.BLOCK_AMETHYST_BLOCK_HIT,5),
        TRAIL(5,"bolt",Particle.END_ROD,null,4),
        BLAST(1,"impact",Particle.CRIT,Sound.ENTITY_GENERIC_EXPLODE,32),
        RETURN(2,"time",Particle.REVERSE_PORTAL,Sound.ENTITY_ENDERMAN_TELEPORT,28),
        VOID(2,"helix",Particle.PORTAL,Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE,28),
        RESCUE(1,"fire",Particle.FLAME,Sound.ITEM_TOTEM_USE,32);
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

        boolean shortCue() { return this == DANGER || this == BOLT; }
        int decayStart() { return duration * 3 / 5; }
    }
    private record Key(UUID source, UUID target, UUID origin, String effect, Form form, UUID world, int x, int y, int z) {}
    private record Cue(Key key, Key visual, Player source, Player target, Location point, Point coordinates,
                       double radius, boolean particles, boolean sound) {}
    private record View(Location feet, Location eye, Point coordinates) {
        View(Location feet, Location eye) { this(feet,eye,new Point(eye)); }
    }
    private record Point(double x, double y, double z) {
        Point(Location location) { this(location.getX(),location.getY(),location.getZ()); }
    }
    private record SightRay(UUID world, Point from, Point to) {}
    private record Display(Cue cue, ItemDisplay model, List<Player> viewers, long started) {}
    @FunctionalInterface interface Spawn {
        ItemDisplay model(Location point, String name, Transformation pose);
    }
    interface Audience {
        Iterable<? extends Player> nearby(Location location);
    }

    private static final int RANGE = 24, MAX_QUEUED = 36, MAX_SOUNDS = 2, MAX_COSMETIC_PARTICLES = 16;
    private final JavaPlugin plugin;
    private final Predicate<Player> playable, packed;
    private final BooleanSupplier running, room;
    private final Spawn spawn;
    private final Audience audience;
    private final Map<Key,Cue> pending = new LinkedHashMap<>();
    private final List<Display> active = new ArrayList<>();
    private final java.util.Set<Key> lingeringModels = new java.util.HashSet<>();
    private record Audible(UUID listener, UUID source, Form form) {}
    private final Map<Audible,Long> recentSounds = new HashMap<>();
    private final Map<UUID,View> views = new HashMap<>();
    private final Map<SightRay,Boolean> sight = new HashMap<>();
    private final Map<UUID,Integer> particleBudget = new HashMap<>();
    private final Map<UUID,Integer> ringBudget = new HashMap<>();
    private final Map<UUID,Integer> soundBudget = new HashMap<>();
    private long plannedPublicTicks, playedPublicTicks, earlyPublicVisibility, earlyPublicBudget, earlyPublicCancel, mergedModels, budgetFallback;
    private long received, duplicate, dropped, fallback, sent, samples, sounds, updates, removed, hidden;
    private long tick, sightRays, ringRays, suppressedRingSamples;

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
        Key visual = form.shortCue() || form == Form.TRAIL ? null
                : new Key(key.source,key.target,null,key.effect,key.form,key.world,key.x,key.y,key.z);
        pending.put(key,new Cue(key,visual,source,target,point.clone(),new Point(point),radius,particles,sound));
    }

    private boolean eligible(Player viewer) {
        return viewer.isOnline() && viewer.getGameMode() != GameMode.SPECTATOR && playable.test(viewer);
    }

    static boolean loadedCorridor(Location eye, Location at) {
        var world = eye.getWorld();
        if (!world.equals(at.getWorld())) return false;
        int minX = Math.min(eye.getBlockX(),at.getBlockX()) >> 4;
        int maxX = Math.max(eye.getBlockX(),at.getBlockX()) >> 4;
        int minZ = Math.min(eye.getBlockZ(),at.getBlockZ()) >> 4;
        int maxZ = Math.max(eye.getBlockZ(),at.getBlockZ()) >> 4;
        // Paper ray tracing may touch chunk data. Never trace through a region that is not resident.
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++)
                if (!world.isChunkLoaded(x,z)) return false;
        return true;
    }

    private boolean sees(Location eye, Location at) {
        if (!loadedCorridor(eye,at)) return false;
        var delta = new org.bukkit.util.Vector(at.getX() - eye.getX(),at.getY() - eye.getY(),at.getZ() - eye.getZ());
        double length = delta.length();
        if (length < .05) return true;
        sightRays++;
        return eye.getWorld().rayTraceBlocks(eye,delta.multiply(1 / length),
                length - .04,FluidCollisionMode.NEVER,true) == null;
    }

    private boolean seesCached(View viewer, Location point, Point coordinates) {
        var ray = new SightRay(viewer.eye.getWorld().getUID(),viewer.coordinates,coordinates);
        return sight.computeIfAbsent(ray,unused -> sees(viewer.eye,point));
    }

    private View view(Player player) {
        return views.computeIfAbsent(player.getUniqueId(),unused -> new View(player.getLocation(),player.getEyeLocation()));
    }

    private void clearSight() {
        views.clear();
        sight.clear();
    }

    private double distanceSquared(Player viewer, Cue cue) {
        if (!eligible(viewer) || !viewer.getWorld().equals(cue.point.getWorld())) return Double.POSITIVE_INFINITY;
        return view(viewer).feet.distanceSquared(cue.point);
    }

    private boolean visible(Player viewer, Cue cue) {
        if (cue.target != null && !viewer.getUniqueId().equals(cue.key.target)
                && (!viewer.canSee(cue.target) || cue.target.isInvisible()
                    || !cue.target.getWorld().equals(cue.point.getWorld()))) return false;
        var observer = view(viewer);
        if (viewer.getUniqueId().equals(cue.key.source)) return seesCached(observer,cue.point,cue.coordinates);
        if (!viewer.canSee(cue.source) || cue.source.isInvisible()) return false;
        var source = view(cue.source);
        return observer.eye.distanceSquared(source.eye) <= RANGE * RANGE
                && seesCached(observer,source.eye,source.coordinates) && seesCached(observer,cue.point,cue.coordinates);
    }

    int size() { return active.size(); }

    void tick() {
        tick++;
        recentSounds.values().removeIf(until -> until <= tick);
        if (!running.getAsBoolean()) { clear(); return; }
        soundBudget.clear();
        ringBudget.clear();
        clearSight();
        particleBudget.clear();
        for (var iterator = active.iterator(); iterator.hasNext();) {
            Display display = iterator.next();
            Cue cue = display.cue;
            long age = tick - display.started;
            if (age >= cue.key.form.duration || !cue.point.isChunkLoaded() || !display.model.isValid() || !eligible(cue.source)
                    || !cue.point.getWorld().equals(cue.source.getWorld())) {
                remove(display,age < cue.key.form.duration ? "visibility" : "complete");
                iterator.remove();
                continue;
            }
            display.viewers.removeIf(viewer -> {
                if (modelFor(viewer,distanceSquared(viewer,cue)) && visible(viewer,cue)) return false;
                viewer.hideEntity(plugin,display.model);
                hidden++;
                return true;
            });
            if (display.viewers.isEmpty()) { remove(display,"visibility"); iterator.remove(); continue; }
            var form = cue.key.form;
            int decay = form.shortCue() ? form.duration / 2 : form.decayStart();
            if (age == 1) transform(display,1,form.shortCue() ? decay : 2);
            else if (age == decay) transform(display,3,form.duration-decay-1);
            else if (age == 3 && !form.shortCue()) transform(display,2,decay-3);

        }
        if (!pending.isEmpty()) {
            var batch = new ArrayList<>(pending.values());
            pending.clear();
            batch.sort(Comparator.comparingInt(cue -> cue.key.form.priority));
            for (var cue : batch) deliver(cue);
        }
    }

    private void transform(Display display,int stage,int duration) {
        display.model.setInterpolationDelay(0);
        display.model.setInterpolationDuration(duration);
        display.model.setTransformation(pose(display.cue,stage));
        updates++;
    }

    private void deliver(Cue cue) {
        if (!eligible(cue.source) || !cue.source.getWorld().equals(cue.point.getWorld()) || !cue.point.isChunkLoaded()) return;
        List<Player> viewers = new ArrayList<>();
        boolean lingering = cue.visual != null && lingeringModels.contains(cue.visual);
        Boolean needsModel = lingering ? false : null;
        if (lingering) mergedModels++;
        boolean targetSaw = false;
        for (Player viewer : audience.nearby(cue.point)) {
            double distance = distanceSquared(viewer,cue);
            if (distance > RANGE * RANGE || !visible(viewer,cue)) continue;
            if (viewer.getUniqueId().equals(cue.key.target)) targetSaw = true;
            boolean modelShown = false;
            if (cue.particles && cue.key.form != Form.TRAIL && modelFor(viewer,distance)) {
                if (needsModel == null) {
                    needsModel = reserve(cue);
                    if (!needsModel) budgetFallback++;
                }
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
        if (cue.visual != null) lingeringModels.add(cue.visual);
        plannedPublicTicks += cue.key.form.duration;
        for (var viewer : viewers) viewer.showEntity(plugin,model);
    }

    private boolean reserve(Cue cue) {
        if (room.getAsBoolean()) return true;
        var lower = active.stream().filter(display -> display.cue.key.form != Form.DANGER && tick - display.started >= 4
                && (display.cue.key.form.priority > cue.key.form.priority
                || display.cue.key.form.priority == cue.key.form.priority
                   && tick - display.started >= display.cue.key.form.decayStart()))
                .max(Comparator.comparingInt(display -> display.cue.key.form.priority));
        if (lower.isEmpty()) return false;
        remove(lower.get(),"budget");
        active.remove(lower.get());
        return true;
    }

    private static Transformation pose(Cue cue,int stage) {
        var form = cue.key.form;
        float yaw = (float)Math.toRadians(-cue.point.getYaw());
        float t = switch (stage) { case 1 -> .5f; case 2 -> .67f; case 3 -> 1f; default -> 0f; };
        boolean contact = stage == 1;
        float scale = switch (stage) { case 1 -> 1f; case 2 -> .77f; case 3 -> .035f; default -> .32f; };
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
            case BLAST -> {
                offset.y = contact ? .17f : .08f;
                rotation.rotateY(.44f*t);
                // Final shockwave stays wide while its height collapses, then the display expires.
                shape.set(stage == 0 ? .45f : contact ? 1.75f : stage == 2 ? 2.24f : .16f,
                        stage == 0 ? .18f : contact ? .64f : stage == 2 ? .28f : .03f,
                        stage == 0 ? .45f : contact ? 1.75f : stage == 2 ? 2.24f : .16f);
            }
            case DANGER -> {
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

    private boolean modelFor(Player viewer,double distance) {
        if (distance > RANGE * RANGE) return false;
        var quality = PlayerUi.quality(viewer);
        int range = quality == PlayerUi.Quality.STANDARD ? 17 : RANGE;
        return distance <= range * range && quality != PlayerUi.Quality.LOW && packed.test(viewer)
                && !PlayerUi.reducedFlash(viewer) && PlayerUi.enabled(viewer,PlayerUi.Option.PARTICLES);
    }

    private void ring(Player viewer,Cue cue) {
        if (cue.key.form != Form.DANGER && !PlayerUi.enabled(viewer,PlayerUi.Option.PARTICLES)) return;
        var id = viewer.getUniqueId();
        int count = Math.min(PlayerUi.quality(viewer).samples,32 - ringBudget.getOrDefault(id,0));
        if (count <= 0) return;
        ringBudget.merge(id,count,Integer::sum);
        var eye = view(viewer).eye;
        boolean bypassesSendCap = cue.key.form.particle == Form.DANGER.particle;
        for (int index = 0; index < count; index++) {
            // Reserve the same samples as before, but do not ray trace particles that the send cap rejects.
            if (!bypassesSendCap && particleBudget.getOrDefault(id,0) >= MAX_COSMETIC_PARTICLES) {
                suppressedRingSamples += count - index;
                break;
            }
            double angle = Math.TAU * index / count;
            var at = cue.point.clone().add(cue.radius*Math.cos(angle),.15,cue.radius*Math.sin(angle));
            if (at.isChunkLoaded() && eye.distanceSquared(at) <= RANGE * RANGE) {
                ringRays++;
                if (sees(eye,at)) particle(viewer,cue.key.form.particle,at,1);
            }
        }
    }

    private void particle(Player viewer,Particle particle,Location at,int count) {
        // An explicit ring is a warning channel. Cosmetic hits and trails share a separate cap.
        var id = viewer.getUniqueId();
        if (particle != Form.DANGER.particle && particleBudget.getOrDefault(id,0) >= MAX_COSMETIC_PARTICLES) return;
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

    private void remove(Display display,String reason) {
        long age = Math.min(display.cue.key.form.duration,tick-display.started);
        playedPublicTicks += age;
        if (age < display.cue.key.form.duration) {
            switch (reason) {
                case "budget" -> earlyPublicBudget++;
                case "visibility" -> earlyPublicVisibility++;
                default -> earlyPublicCancel++;
            }
        }
        if (display.cue.visual != null) lingeringModels.remove(display.cue.visual);
        display.model.remove();
        removed++;
    }

    void cancelWarning(UUID source,String effect,UUID origin) {
        Predicate<Key> matches = key -> key.form == Form.DANGER && key.source.equals(source)
                && key.effect.equals(effect) && (origin == null || origin.equals(key.origin));
        pending.keySet().removeIf(matches);
        active.removeIf(display -> {
            if (!matches.test(display.cue.key)) return false;
            remove(display,"cancelled");
            return true;
        });
    }

    void cancelPreview(UUID player) {
        pending.keySet().removeIf(key -> key.source.equals(player) && key.effect.equals("lab"));
        active.removeIf(display -> {
            var key = display.cue.key;
            if (!key.source.equals(player) || !key.effect.equals("lab")) return false;
            remove(display,"cancelled");
            return true;
        });
    }

    void forget(UUID player) {
        recentSounds.keySet().removeIf(key -> key.listener.equals(player) || key.source.equals(player));
        pending.keySet().removeIf(key -> key.source.equals(player) || player.equals(key.target));
        active.removeIf(display -> {
            if (display.cue.key.source.equals(player) || player.equals(display.cue.key.target)) {
                remove(display,"cancelled");
                return true;
            }
            display.viewers.removeIf(viewer -> {
                if (!viewer.getUniqueId().equals(player)) return false;
                viewer.hideEntity(plugin,display.model);
                return true;
            });
            if (!display.viewers.isEmpty()) return false;
            remove(display,"cancelled");
            return true;
        });
    }

    void clear() {
        recentSounds.clear();
        pending.clear();
        active.forEach(display -> remove(display,"cancelled"));
        active.clear();
        soundBudget.clear();
        ringBudget.clear();
        clearSight();
        particleBudget.clear();
    }

    void resetMetrics() {
        droppedByRole.clear();
        plannedPublicTicks = playedPublicTicks = earlyPublicVisibility = earlyPublicBudget = earlyPublicCancel = mergedModels = budgetFallback = 0;
        sightRays = ringRays = suppressedRingSamples = 0;
        received = duplicate = dropped = fallback = sent = samples = sounds = updates = removed = hidden = 0;
    }

    String metrics() {
        return "public_requests=" + received + " duplicate=" + duplicate + " queue_drops=" + dropped
                + " fallback=" + fallback + " particle_calls=" + sent + " particle_count=" + samples
                + " sound_calls=" + sounds + " public_updates=" + updates + " public_removed=" + removed + " visibility_revoked=" + hidden
                + " public_plan=" + plannedPublicTicks + " public_played=" + playedPublicTicks
                + " public_early_visibility=" + earlyPublicVisibility + " public_early_budget=" + earlyPublicBudget
                + " public_early_cancel=" + earlyPublicCancel + " public_merged=" + mergedModels
                + " public_budget_fallback=" + budgetFallback
                + " sight_rays=" + sightRays + " ring_ray_candidates=" + ringRays + " ring_samples_over_send_cap=" + suppressedRingSamples
                + " dropped_warning=" + droppedByRole.getOrDefault("warning",0L)
                + " dropped_impact=" + droppedByRole.getOrDefault("impact",0L)
                + " dropped_active=" + droppedByRole.getOrDefault("active",0L)
                + " dropped_synergy=" + droppedByRole.getOrDefault("synergy",0L)
                + " dropped_decoration=" + droppedByRole.getOrDefault("decoration",0L);
    }
}
