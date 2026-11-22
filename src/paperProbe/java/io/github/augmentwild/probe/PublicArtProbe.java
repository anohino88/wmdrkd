package io.github.augmentwild.probe;

import io.github.augmentwild.paper.PaperArt;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Real Paper item displays and ray traces. Viewer identity and subscriptions are synthetic. */
final class PublicArtProbe {
    private static final class Viewer {
        final UUID id = UUID.randomUUID();
        final List<ItemDisplay> shown = new ArrayList<>();
        final List<Location> particles = new ArrayList<>();
        final Location eye;
        final Player player;
        boolean invisible, member = true, online = true, capture = true;
        int hides;

        Viewer(Pig pigment,Location eye) {
            this.eye = eye;
            player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(self,method,args) -> {
                return switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getLocation", "getEyeLocation" -> eye.clone();
                    case "getWorld" -> eye.getWorld();
                    case "getGameMode" -> GameMode.SURVIVAL;
                    case "getPersistentDataContainer" -> pigment.getPersistentDataContainer();
                    case "getOpenInventory" -> null;
                case "isOnline" -> online;
                    case "isInvisible" -> invisible;
                    case "canSee" -> true;
                    case "playSound" -> null;
                    case "spawnParticle" -> { if (capture) particles.add(((Location)args[1]).clone()); yield null; }
                    case "showEntity" -> { if (capture) shown.add((ItemDisplay)args[1]); yield null; }
                    case "hideEntity" -> { hides++; yield null; }
                    default -> throw new AssertionError("Unexpected public artwork proxy method: " + method);
                };
            });
        }
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    static void run(JavaPlugin plugin,Pig pig,UUID packId) throws ReflectiveOperationException {
        var site = pig.getLocation();
        site.setX((site.getBlockX() & ~15) + 6.5);
        site.setZ((site.getBlockZ() & ~15) + 5.5);
        site.setY(Math.floor(site.getY()) + 24.5);
        var source = new Viewer(pig,site.clone().add(-2,0,0));
        var target = new Viewer(pig,site.clone().add(2,0,0));
        var unmodded = new Viewer(pig,site.clone().add(-1,0,1));
        var outsider = new Viewer(pig,site.clone().add(-1,0,-1));
        outsider.member = false;
        var viewers = List.of(source,target,unmodded,outsider);
        ClassLoader loader = PaperArt.class.getClassLoader();
        Class<?> audienceClass = Class.forName("io.github.augmentwild.paper.CombatCinematics$Audience",true,loader);
        Class<? extends Enum> formClass = Class.forName("io.github.augmentwild.paper.CombatCinematics$Form",true,loader).asSubclass(Enum.class);
        Object audience = Proxy.newProxyInstance(loader,new Class<?>[]{audienceClass},
                (self,method,args) -> viewers.stream().map(v -> v.player).toList());
        var constructor = PaperArt.class.getDeclaredConstructor(JavaPlugin.class,Predicate.class,BooleanSupplier.class,audienceClass);
        constructor.setAccessible(true);
        var running = new java.util.concurrent.atomic.AtomicBoolean(true);
        PaperArt art = constructor.newInstance(plugin,
                (Predicate<Player>) player -> viewers.stream().anyMatch(v -> v.player == player && v.member),
                (BooleanSupplier) running::get,audience);
        var combat = PaperArt.class.getDeclaredMethod("combat",Player.class,Player.class,UUID.class,String.class,formClass,Location.class,double.class);
        var cue = PaperArt.class.getDeclaredMethod("cue",Player.class,String.class);
        var tick = PaperArt.class.getDeclaredMethod("tick");
        var clear = PaperArt.class.getDeclaredMethod("clear");
        combat.setAccessible(true);
        cue.setAccessible(true);
        tick.setAccessible(true);
        clear.setAccessible(true);
        for (var v : List.of(source,target,outsider))
            art.status(new PlayerResourcePackStatusEvent(v.player,packId,PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
        var original = site.getBlock().getBlockData();
        try {
            var root = UUID.randomUUID();
            Object hit = Enum.valueOf(formClass,"HIT");
            Object guard = Enum.valueOf(formClass,"GUARD");
            var impact = target.eye.clone().add(-.2,0,0);
            combat.invoke(art,source.player,target.player,root,"first",hit,impact,0d);
            combat.invoke(art,source.player,target.player,root,"first",hit,impact,0d);
            combat.invoke(art,source.player,target.player,root,"second",guard,impact,0d);
            tick.invoke(art);
            require(source.shown.size() == 2 && target.shown.equals(source.shown),"shared display per distinct effect");
            require(source.shown.stream().allMatch(d -> d.getItemStack().getItemMeta().getItemModel().getKey().startsWith("fx/")),
                    "public model contains no exact augment ID");
            require(outsider.shown.isEmpty() && outsider.particles.isEmpty() && unmodded.particles.size() == 2,"audience and vanilla fallback");

            site.getBlock().setType(Material.STONE,false);
            tick.invoke(art);
            require(target.hides == 2,"revoke active model when source is occluded");
            combat.invoke(art,source.player,target.player,UUID.randomUUID(),"occluded",hit,impact,0d);
            tick.invoke(art);
            require(target.shown.size() == 2 && target.particles.size() == 1
                    && target.particles.getFirst().distanceSquared(target.eye) == 0,"occluded target receives local feedback");
            site.getBlock().setBlockData(original,false);

            source.invisible = true;
            combat.invoke(art,source.player,target.player,UUID.randomUUID(),"hidden",hit,impact,0d);
            tick.invoke(art);
            require(target.shown.size() == 2 && target.particles.size() == 2,"do not disclose concealed source");
            source.invisible = false;
            cue.invoke(art,source.player,"steel_heart");
            cue.invoke(art,source.player,"magic_missile");
            cue.invoke(art,source.player,"steel_heart");
            require(source.shown.stream().filter(d -> d.getItemStack().getItemMeta().getItemModel().getKey().startsWith("proc/")).count() == 2
                    && target.shown.size() == 2,"concurrent private effects deduplicate by ID");
            running.set(false);
            tick.invoke(art);
            require(source.shown.stream().filter(d -> d.getItemStack().getItemMeta().getItemModel().getKey().startsWith("fx/"))
                    .noneMatch(ItemDisplay::isValid),"pause clears public effects");
            plugin.getLogger().info("AW_PUBLIC_ART_PASS shared=2 mixed_pack=true wall=true concealed=true private_concurrent=2");
        } finally {
            site.getBlock().setBlockData(original,false);
            clear.invoke(art);
        }
        require(source.shown.stream().noneMatch(ItemDisplay::isValid),"final display cleanup");
    }

    static void startLoad(JavaPlugin probe,Location location) {
        new Load(probe,location).start();
    }

    /** Same JVM, seed, work cadence, terrain and tick counts across graphics settings. */
    private static final class Load implements org.bukkit.event.Listener {
        static final int WARMUP = 12, MEASURED = 48;
        static final List<String> MODES = List.of("OFF","LOW","STANDARD","HIGH","ULTRA");
        final JavaPlugin probe;
        final Location site;
        final Pig anchor;
        final org.bukkit.Chunk chunk;
        final List<Viewer> all = new ArrayList<>();
        final double[] timing = new double[MEASURED];
        final java.util.Random random = new java.util.Random();
        PaperArt art;
        UUID packId;
        java.lang.reflect.Method combat,cue,tick,clear,metrics;
        Object hit,guard,danger,trail;
        org.bukkit.scheduler.BukkitTask task;
        int mode, size = 2, frame;
        long gcAtStart;
        long heapAtStart, peakHeap;
        boolean recorded;

        Load(JavaPlugin probe,Location location) {
            this.probe = probe;
            site = location.clone();
            site.setX((site.getBlockX() & ~15)+7.5);
            site.setZ((site.getBlockZ() & ~15)+7.5);
            site.setY(Math.floor(site.getY())+24.5);
            chunk = site.getChunk();
            require(chunk.addPluginChunkTicket(probe),"retain VFX benchmark chunk");
            anchor = site.getWorld().spawn(site,Pig.class);
            anchor.setAI(false);
            anchor.setGravity(false);
            anchor.setSilent(true);
            for (int i = 0; i < 16; i++) {
                Viewer viewer = new Viewer(anchor,site.clone().add(i%4-1.5,0,i/4-1.5));
                viewer.capture = false;
                all.add(viewer);
            }
        }

        @SuppressWarnings({"rawtypes","unchecked"})
        void start() {
            var host = (JavaPlugin) probe.getServer().getPluginManager().getPlugin("AugmentWild");
            var config = host.getConfig();
            String oldUrl = config.getString("art.url"), oldHash = config.getString("art.sha1");
            String hash = ArtProbe.packHash(host);
            packId = UUID.nameUUIDFromBytes(("augmentwild-art:"+hash).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            try {
                config.set("art.url","https://example.com/augmentwild.zip");
                config.set("art.sha1",hash);
                ClassLoader loader = PaperArt.class.getClassLoader();
                Class<?> audienceClass = Class.forName("io.github.augmentwild.paper.CombatCinematics$Audience",true,loader);
                Class<? extends Enum> formClass = Class.forName("io.github.augmentwild.paper.CombatCinematics$Form",true,loader).asSubclass(Enum.class);
                Object audience = Proxy.newProxyInstance(loader,new Class<?>[]{audienceClass},
                        (self,method,args) -> all.subList(0,size).stream().map(v -> v.player).toList());
                var constructor = PaperArt.class.getDeclaredConstructor(JavaPlugin.class,Predicate.class,BooleanSupplier.class,audienceClass);
                constructor.setAccessible(true);
                art = constructor.newInstance(host,(Predicate<Player>) p -> true,(BooleanSupplier) () -> true,audience);
                combat = PaperArt.class.getDeclaredMethod("combat",Player.class,Player.class,UUID.class,String.class,formClass,Location.class,double.class);
                cue = PaperArt.class.getDeclaredMethod("cue",Player.class,String.class);
                tick = PaperArt.class.getDeclaredMethod("tick");
                clear = PaperArt.class.getDeclaredMethod("clear");
                metrics = PaperArt.class.getDeclaredMethod("metrics");
                for (var method : List.of(combat,cue,tick,clear,metrics)) method.setAccessible(true);
                hit = Enum.valueOf(formClass,"HIT");
                guard = Enum.valueOf(formClass,"GUARD");
                danger = Enum.valueOf(formClass,"DANGER");
                trail = Enum.valueOf(formClass,"TRAIL");
                probe.getServer().getPluginManager().registerEvents(this,probe);
                next();
                task = probe.getServer().getScheduler().runTaskTimer(probe,this::pulse,1,1);
            } catch (ReflectiveOperationException failure) {
                stop();
                throw new AssertionError("VFX load fixture setup",failure);
            } finally {
                config.set("art.url",oldUrl);
                config.set("art.sha1",oldHash);
            }
        }

        private void next() throws ReflectiveOperationException {
            clear.invoke(art);
            anchor.getPersistentDataContainer().set(new org.bukkit.NamespacedKey("augmentwild","art_quality"),
                    org.bukkit.persistence.PersistentDataType.STRING,mode == 0 ? "STANDARD" : MODES.get(mode));
            for (var v : all) art.status(new PlayerResourcePackStatusEvent(v.player,packId,PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
            frame = 0;
            recorded = false;
            random.setSeed(67_026L ^ size);
        }

        private void pulse() {
            try {
                if (frame == WARMUP) {
                    // Reset counters without recycling the JVM, world or player adapters.
                    clear.invoke(art);
                    gcAtStart = gc();
                    heapAtStart = heap();
                    peakHeap = heapAtStart;
                }
                if (frame++ % 6 == 0) {
                    for (int index = 0; index < size; index++) {
                        Viewer source = all.get(index);
                        Viewer target = all.get((index + 1 + random.nextInt(size-1)) % size);
                        UUID root = new UUID(random.nextLong(),random.nextLong());
                        if (mode == 0) continue;
                        cue.invoke(art,source.player,"sword_wind");
                        combat.invoke(art,source.player,target.player,root,"strike",hit,target.eye,0d);
                        combat.invoke(art,source.player,target.player,root,"guard",guard,target.eye,0d);
                        combat.invoke(art,source.player,null,root,"missile",trail,source.eye,0d);
                        if (index == 0) combat.invoke(art,source.player,null,root,"zone",danger,site,2d);
                    }
                }
                tick.invoke(art);
                recorded = true;
            } catch (ReflectiveOperationException failure) {
                stop();
                throw new AssertionError("VFX load fixture",failure);
            }
        }

        @org.bukkit.event.EventHandler
        public void measured(com.destroystokyo.paper.event.server.ServerTickEndEvent event) throws ReflectiveOperationException {
            if (!recorded || frame <= WARMUP) return;
            timing[frame-WARMUP-1] = event.getTickDuration();
            peakHeap = Math.max(peakHeap,heap());
            if (frame < WARMUP+MEASURED) return;
            double mean = java.util.Arrays.stream(timing).average().orElseThrow();
            long over = java.util.Arrays.stream(timing).filter(t -> t > 50).count();
            java.util.Arrays.sort(timing);
            String metricsLine = (String) metrics.invoke(art);
            int cap = Integer.parseInt(java.util.regex.Pattern.compile("peak_displays=(\\d+)").matcher(metricsLine)
                    .results().findFirst().orElseThrow().group(1));
            require(cap <= 24 && (mode < 2 ? cap == 0 : cap > 0),"VFX benchmark reached configured display path");
            require(mode == 0 ? metricsLine.contains("public_requests=0 ") : !metricsLine.contains("public_requests=0 "),
                    "VFX benchmark reached configured rendition");
            probe.getLogger().info(String.format(java.util.Locale.ROOT,
                    "AW_VFX_LOAD mode=%s viewers=%d ticks=%d seed=67026 mean=%.3f p50=%.3f p95=%.3f p99=%.3f max=%.3f over50=%d heap_start=%d heap_peak=%d gc_ms=%d %s",
                    MODES.get(mode),size,MEASURED,mean,percentile(.5),percentile(.95),percentile(.99),timing[MEASURED-1],over,
                    heapAtStart,peakHeap,gc()-gcAtStart,metricsLine));
            if (++mode == MODES.size()) {
                mode = 0;
                size *= 2;
            }
            if (size <= 16) next();
            else {
                stop();
                probe.getLogger().info("AW_VFX_LOAD_PASS stages=20 total_ticks=960");
            }
        }

        private void stop() {
            if (task != null) task.cancel();
            org.bukkit.event.HandlerList.unregisterAll(this);
            anchor.remove();
            chunk.removePluginChunkTicket(probe);
            if (clear != null) {
                try { clear.invoke(art); }
                catch (ReflectiveOperationException failure) { throw new AssertionError("VFX load fixture cleanup",failure); }
            }
        }
        double percentile(double p) { return timing[(int)Math.ceil(p*MEASURED) - 1]; }
        static long heap() { return java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed(); }
        static long gc() {
            return java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().stream()
                    .mapToLong(g -> Math.max(0,g.getCollectionTime())).sum();
        }
    }

    private static void require(boolean value,String reason) {
        if (!value) throw new AssertionError(reason);
    }
}
