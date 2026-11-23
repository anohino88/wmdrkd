package io.github.augmentwild.probe;

import io.github.augmentwild.paper.PaperArt;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.Properties;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent.Status;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Checks Paper's item component and the connection-specific pack gate. */
final class ArtProbe {
    static String packHash(JavaPlugin plugin) {
        try (var input = plugin.getResource("art-pack.sha1")) {
            return new String(input.readAllBytes(),StandardCharsets.UTF_8).strip();
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }

    static void run(Pig pig) {
        var host = (JavaPlugin) Bukkit.getPluginManager().getPlugin("AugmentWild");
        var hash = ArtProbe.packHash(host);
        var id = UUID.nameUUIDFromBytes(("augmentwild-art:"+hash).getBytes(StandardCharsets.UTF_8));
        var config = host.getConfig();
        String previousUrl = config.getString("art.url");
        String previousHash = config.getString("art.sha1");
        PaperArt art;
        try {
            config.set("art.url","https://example.com/augmentwild.zip");
            config.set("art.sha1",hash);
            PublicArtProbe.run(host,pig,id);
            art = new PaperArt(host,p -> true);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Public artwork lifecycle",failure);
        } finally {
            config.set("art.url",previousUrl);
            config.set("art.sha1",previousHash);
        }
        ItemDisplay[] shown = new ItemDisplay[1];
        int[] particles = {0};
        var observed = new java.util.ArrayList<ItemDisplay>();
        var p = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,method,args) -> {
            return switch (method.getName()) {
                case "getUniqueId" -> pig.getUniqueId();
                case "getWorld" -> pig.getWorld();
                case "getLocation" -> pig.getLocation();
                case "getPersistentDataContainer" -> pig.getPersistentDataContainer();
                case "getOpenInventory" -> null;
                case "isOnline" -> true;
                case "playSound" -> null;
                case "spawnParticle" -> { particles[0]++; yield null; }
                case "showEntity" -> { shown[0] = (ItemDisplay)args[1]; observed.add(shown[0]); yield null; }
                default -> throw new AssertionError("Unexpected artwork probe operation: " + method);
            };
        });
        String model = "augment/steel_heart";
        require(art.icon(p,new ItemStack(Material.GOLD_INGOT),model).getItemMeta().getItemModel() == null);
        art.status(new PlayerResourcePackStatusEvent(p,UUID.randomUUID(),Status.SUCCESSFULLY_LOADED));
        require(art.icon(p,new ItemStack(Material.GOLD_INGOT),model).getItemMeta().getItemModel() == null);
        art.status(new PlayerResourcePackStatusEvent(p,id,Status.SUCCESSFULLY_LOADED));
        var item = art.icon(p,new ItemStack(Material.GOLD_INGOT),model);
        require(item.getType() == Material.GOLD_INGOT &&
                "augmentwild:augment/steel_heart".equals(item.getItemMeta().getItemModel().toString()));
        verifyRecovery(art,p);
        try (var input = host.getResource("art-motifs.properties")) {
            var ids = new Properties();
            ids.load(input);
            require(ids.size() == 84);
            // Exercise the plugin's private cosmetic adapter using real Paper ItemDisplays.
            var preview = PaperArt.class.getDeclaredMethod("preview",Player.class,String.class,boolean.class);
            var cue = PaperArt.class.getDeclaredMethod("cue",Player.class,String.class);
            cue.setAccessible(true);
            var tick = PaperArt.class.getDeclaredMethod("tick");
            var clear = PaperArt.class.getDeclaredMethod("clear");
            preview.setAccessible(true);
            tick.setAccessible(true);
            clear.setAccessible(true);
            for (String key : ids.stringPropertyNames()) {
                shown[0] = null;
                cue.invoke(art,p,key);
                var display = shown[0];
                require(display != null && display.isValid() && !display.isPersistent() && !display.isVisibleByDefault());
                require(display.getItemStack().getItemMeta().getItemModel().getKey().equals("proc/" + key));
                cue.invoke(art,p,key);
                require(shown[0] == display);
                tick.invoke(art);
                int attack = display.getInterpolationDuration();
                require(attack >= 3 && attack <= 5);
                for (int i = 1; i <= attack; i++) tick.invoke(art);
                int release = display.getInterpolationDuration();
                require(release > attack);
                for (int i = 1; i < release; i++) tick.invoke(art);
                require(display.isValid());
                tick.invoke(art);
                require(!display.isValid());
                for (int i = attack + release + 1; i < 20; i++) tick.invoke(art);
            }
            preview.invoke(art,p,"model/premium",false);
            require(shown[0].isValid());
            clear.invoke(art);
            require(!shown[0].isValid());
            shown[0] = null;
            preview.invoke(art,p,"augment/steel_heart",true);
            require(shown[0] == null && particles[0] == 1);
            var qualityKey = new org.bukkit.NamespacedKey("augmentwild","art_quality");
            var storage = pig.getPersistentDataContainer();
            int cycles = 0;
            for (String quality : java.util.List.of("LOW","STANDARD","HIGH","ULTRA")) {
                storage.set(qualityKey,org.bukkit.persistence.PersistentDataType.STRING,quality);
                int peak = 0;
                int shownCount = 0;
                for (String key : ids.stringPropertyNames()) {
                    observed.clear();
                    preview.invoke(art,p,"augment/" + key,false);
                    int visible = observed.size();
                    require(quality.equals("LOW") ? visible == 0 : visible >= 1 && visible <= 3);
                    shownCount += visible;
                    peak = Math.max(peak,visible);
                    for (int step = 0; step < 24; step++) tick.invoke(art);
                    require(observed.stream().noneMatch(ItemDisplay::isValid));
                    cycles++;
                }
                Bukkit.getLogger().info("AW_ART_QUALITY quality=" + quality + " displays_created=" + shownCount
                        + " peak_private_displays=" + peak + " adapter_ticks=2016 remaining=0");
            }
            storage.set(qualityKey,org.bukkit.persistence.PersistentDataType.STRING,"STANDARD");
            var signatures = java.util.List.of("shadow_dash","earthquake_mage","time_rewind","void_lord","bomb_alchemy","duel_contract","calamity_giant");
            var signatureDurations = new java.util.HashSet<Integer>();
            for (String key : signatures) {
                preview.invoke(art,p,"augment/" + key,false);
                var display = shown[0];
                require(display.isValid() && display.getItemStack().getItemMeta().getItemModel().getKey().equals("fx/sig_" + key));
                var start = display.getTransformation().getTranslation();
                int ticks = 0, transforms = 0;
                var previous = display.getTransformation().getTranslation();
                while (display.isValid() && ticks < 25) {
                    tick.invoke(art);
                    ticks++;
                    var next = display.getTransformation().getTranslation();
                    if (!previous.equals(next)) transforms++;
                    previous = next;
                }
                require(!display.isValid() && !start.equals(previous) && transforms >= 3);
                signatureDurations.add(ticks);
            }
            require(signatureDurations.size() >= 6);
            Bukkit.getLogger().info("AW_ART_SIGNATURE_PASS active=7 multiple_transforms=true owner_only=true remaining=0");
            var sustain = PaperArt.class.getDeclaredMethod("sustain",Player.class,String.class,org.bukkit.Location.class,boolean.class);
            sustain.setAccessible(true);
            observed.clear();
            for (int i = 0; i < 1200; i++) {
                sustain.invoke(art,p,"void_lord",pig.getLocation(),false);
                tick.invoke(art);
                require(observed.size() == 1 && observed.getFirst().isValid());
            }
            tick.invoke(art);
            require(!observed.getFirst().isValid());
            var metrics = PaperArt.class.getDeclaredMethod("metrics");
            metrics.setAccessible(true);
            Bukkit.getLogger().info("AW_ART_SUSTAIN ticks=1200 created=1 remaining=0 " + metrics.invoke(art));
            for (int count : java.util.List.of(2,4,8,16)) {
                clear.invoke(art);
                observed.clear();
                var viewers = new java.util.ArrayList<Player>();
                for (int v = 0; v < count; v++) {
                    UUID uuid = UUID.randomUUID();
                    var viewer = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},
                            (obj,method,argv) -> method.getName().equals("getUniqueId") ? uuid
                                    : Proxy.getInvocationHandler(p).invoke(p,method,argv));
                    art.status(new PlayerResourcePackStatusEvent(viewer,id,Status.SUCCESSFULLY_LOADED));
                    viewers.add(viewer);
                }
                var measured = new long[100];
                for (int frame = 0; frame < measured.length; frame++) {
                    long start = System.nanoTime();
                    if (frame % 8 == 0)
                        for (int v = 0; v < viewers.size(); v++)
                            preview.invoke(art,viewers.get(v),"augment/" + signatures.get(v % signatures.size()),false);
                    tick.invoke(art);
                    measured[frame] = System.nanoTime() - start;
                    require(observed.stream().filter(ItemDisplay::isValid).count() <= 24);
                }
                String counters = (String)metrics.invoke(art);
                clear.invoke(art);
                require(observed.stream().noneMatch(ItemDisplay::isValid));
                java.util.Arrays.sort(measured);
                Bukkit.getLogger().info(String.format(java.util.Locale.ROOT,
                        "AW_ART_LOAD viewers=%d adapter_frames=%d mean_us=%.2f p95_us=%.2f p99_us=%.2f max_us=%.2f %s after_clear=0",
                        count,measured.length,java.util.Arrays.stream(measured).average().orElseThrow()/1000,
                        measured[94]/1000d,measured[98]/1000d,measured[99]/1000d,counters));
            }
            storage.remove(qualityKey);
            require(cycles == 336);
            Bukkit.getLogger().info("AW_ART_LIFECYCLE_PASS models=84 variable_timing=true remaining=0");
        } catch (ReflectiveOperationException | java.io.IOException failure) {
            throw new AssertionError("Paper cosmetic lifecycle",failure);
        }
        art.status(new PlayerResourcePackStatusEvent(p,id,Status.DECLINED));
        require(art.icon(p,new ItemStack(Material.GOLD_INGOT),model).getItemMeta().getItemModel() == null);
        Bukkit.getLogger().info("AW_ART_PROBE_PASS");
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    private static void verifyRecovery(PaperArt art,Player player) {
        try {
            var loader = PaperArt.class.getClassLoader();
            var screen = Class.forName("io.github.augmentwild.paper.PaperArt$Screen",true,loader).asSubclass(Enum.class);
            var title = PaperArt.class.getDeclaredMethod("title",Player.class,screen,String.class);
            var safe = PaperArt.class.getDeclaredMethod("toggleSafe",Player.class);
            var glyph = PaperArt.class.getDeclaredMethod("slotIcon",Player.class,String.class);
            for (var method : java.util.List.of(title,safe,glyph)) method.setAccessible(true);
            for (var value : screen.getEnumConstants()) {
                var component = (net.kyori.adventure.text.TextComponent) title.invoke(art,player,value,"AugmentWild");
                require(component.content().equals("AugmentWild") && component.children().isEmpty()
                        && net.kyori.adventure.key.Key.key("minecraft:default").equals(component.font()));
            }
            safe.invoke(art,player);
            require(art.icon(player,new ItemStack(Material.IRON_INGOT),"augment/steel_heart").getItemMeta().getItemModel() == null);
            require(glyph.invoke(art,player,"shadow_dash").equals(net.kyori.adventure.text.Component.empty()));
            safe.invoke(art,player);
            require(art.icon(player,new ItemStack(Material.IRON_INGOT),"augment/steel_heart").getItemMeta().getItemModel() != null);
            var inspector = Class.forName("io.github.augmentwild.paper.PackInspection",true,loader);
            var inspect = inspector.getDeclaredMethod("inspect",java.nio.file.Path.class);
            inspect.setAccessible(true);
            var fixture = java.nio.file.Path.of(System.getProperty("augmentwild.pack.fixture"));
            var archive = inspect.invoke(null,fixture);
            require(archive.toString().startsWith("Archive[release=7.8,"));
            var invalid = java.nio.file.Files.createTempFile("aw-invalid-pack-", ".zip");
            try {
                java.nio.file.Files.writeString(invalid,"not a resource pack");
                try {
                    inspect.invoke(null,invalid);
                    throw new AssertionError("Malformed ZIP accepted");
                } catch (java.lang.reflect.InvocationTargetException expected) {
                    require(expected.getCause() instanceof java.io.IOException);
                }
            } finally {
                java.nio.file.Files.deleteIfExists(invalid);
            }
            Bukkit.getLogger().info("AW_PACK_RECOVERY_PASS native_titles=12 safe_mode=true zip_refs=true corrupt_zip_rejected=true");
        } catch (ReflectiveOperationException | java.io.IOException failure) {
            throw new AssertionError("Pack fallback fixture",failure);
        }
    }

    private static void require(boolean condition) {
        if (!condition) throw new AssertionError("resource pack model gate");
    }
}
