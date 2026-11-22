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
    static void run(Pig pig) {
        var host = (JavaPlugin) Bukkit.getPluginManager().getPlugin("AugmentWild");
        var hash = "0123456789012345678901234567890123456789";
        var id = UUID.nameUUIDFromBytes(("augmentwild-art:"+hash).getBytes(StandardCharsets.UTF_8));
        var config = host.getConfig();
        String previousUrl = config.getString("art.url");
        String previousHash = config.getString("art.sha1");
        PaperArt art;
        try {
            config.set("art.url","https://example.com/augmentwild.zip");
            config.set("art.sha1",hash);
            art = new PaperArt(host,p -> true);
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
                    for (int step = 0; step < 20; step++) tick.invoke(art);
                    require(observed.stream().noneMatch(ItemDisplay::isValid));
                    cycles++;
                }
                Bukkit.getLogger().info("AW_ART_QUALITY quality=" + quality + " displays_created=" + shownCount
                        + " peak_private_displays=" + peak + " adapter_ticks=1680 remaining=0");
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

    private static void require(boolean condition) {
        if (!condition) throw new AssertionError("resource pack model gate");
    }
}
