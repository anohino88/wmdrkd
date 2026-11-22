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
        boolean invisible, member = true, online = true;
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
                    case "isOnline" -> online;
                    case "isInvisible" -> invisible;
                    case "canSee" -> true;
                    case "playSound" -> null;
                    case "spawnParticle" -> { particles.add(((Location)args[1]).clone()); yield null; }
                    case "showEntity" -> { shown.add((ItemDisplay)args[1]); yield null; }
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

    private static void require(boolean value,String reason) {
        if (!value) throw new AssertionError(reason);
    }
}
