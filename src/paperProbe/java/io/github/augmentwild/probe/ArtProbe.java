package io.github.augmentwild.probe;

import io.github.augmentwild.paper.PaperArt;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
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
        var p = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,method,args) -> {
            if (method.getName().equals("getUniqueId")) return pig.getUniqueId();
            throw new AssertionError("Unexpected artwork probe operation: " + method);
        });
        String model = "augment/steel_heart";
        require(art.icon(p,new ItemStack(Material.GOLD_INGOT),model).getItemMeta().getItemModel() == null);
        art.status(new PlayerResourcePackStatusEvent(p,UUID.randomUUID(),Status.SUCCESSFULLY_LOADED));
        require(art.icon(p,new ItemStack(Material.GOLD_INGOT),model).getItemMeta().getItemModel() == null);
        art.status(new PlayerResourcePackStatusEvent(p,id,Status.SUCCESSFULLY_LOADED));
        var item = art.icon(p,new ItemStack(Material.GOLD_INGOT),model);
        require(item.getType() == Material.GOLD_INGOT &&
                "augmentwild:augment/steel_heart".equals(item.getItemMeta().getItemModel().toString()));
        art.status(new PlayerResourcePackStatusEvent(p,id,Status.DECLINED));
        require(art.icon(p,new ItemStack(Material.GOLD_INGOT),model).getItemMeta().getItemModel() == null);
        Bukkit.getLogger().info("AW_ART_PROBE_PASS");
    }

    private static void require(boolean condition) {
        if (!condition) throw new AssertionError("resource pack model gate");
    }
}
