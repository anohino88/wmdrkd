package io.github.augmentwild.probe;

import io.github.augmentwild.paper.PaperMenu;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Real Paper inventories and a protocol client; clicks are injected at the Paper event boundary. */
final class InterfaceProbe {
    static void history(JavaPlugin plugin,Player player) {
        open(player,"aw history",54);
        require(top(player).getItem(9) != null,"archive page has a saved result");
        require(event(player,9,ClickType.NUMBER_KEY,InventoryAction.UNKNOWN).isCancelled(),"archive number-key lock");
        command(player,"aw history last");
        new org.bukkit.scheduler.BukkitRunnable() {
            private int attempts;
            @Override public void run() {
                String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(player.getOpenInventory().title());
                if (title.equals("경기 기록 상세")) {
                    require(top(player).getItem(9) != null,"saved result populated");
                    plugin.getLogger().info("AW_MATCH_ARCHIVE_RESTART_PASS");
                    cancel();
                } else if (++attempts >= 40) {
                    cancel();
                    throw new AssertionError("Persisted result did not open: " + title);
                }
            }
        }.runTaskTimer(plugin,1,1);
    }

    static void lobby(JavaPlugin plugin,Player player) {
        open(player,"aw menu",54);
        require(top(player).getItem(36) == null,"ordinary user has no admin section");
        require(top(player).getItem(2).getType() == Material.LIME_DYE,"packless join fallback");
        var ordinary = Bukkit.createInventory(null,27);
        player.openInventory(ordinary);
        var untouched = event(player,1,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        require(!untouched.isCancelled(),"ordinary inventory unaffected");
        open(player,"aw menu",54);
        var main = top(player);
        for (var click : List.of(ClickType.SHIFT_LEFT,ClickType.NUMBER_KEY,ClickType.DOUBLE_CLICK,ClickType.DROP,ClickType.SWAP_OFFHAND))
            require(event(player,11,click,InventoryAction.UNKNOWN).isCancelled(),"blocked " + click);
        require(event(player,54,ClickType.SHIFT_LEFT,InventoryAction.MOVE_TO_OTHER_INVENTORY).isCancelled(),"bottom shift");
        var item = new ItemStack(Material.STONE);
        var drag = new InventoryDragEvent(player.getOpenInventory(),item,item,false,java.util.Map.of(11,item,54,item));
        Bukkit.getPluginManager().callEvent(drag);
        require(drag.isCancelled() && top(player).getHolder() == main.getHolder(),"drag and control unchanged");
        click(player,11,ClickType.LEFT);
        later(plugin,() -> {
            require(top(player).getHolder() != main.getHolder() && top(player).getSize() == 54,"codex link");
            Set<ItemStack> icons = new HashSet<>();
            for (int page = 0; page < 3; page++) {
                click(player,53,ClickType.LEFT);
                for (int card = 0; card < 35; card++) {
                    var shown = top(player).getItem(1+card/7*9+card%7);
                    if (shown != null) icons.add(shown);
                }
            }
            require(icons.size() == 84,"84 real codex cards");
            click(player,1,ClickType.RIGHT);
            later(plugin,() -> {
                require(top(player).getSize() == 27 && top(player).getItem(10) != null
                        && top(player).getItem(13) != null && top(player).getItem(16) != null,"detail panels");
                click(player,26,ClickType.LEFT);
                later(plugin,() -> {
                    click(player,49,ClickType.LEFT);
                    later(plugin,() -> {
                        require(top(player).getItem(22).getType() == Material.ENCHANTED_BOOK,"family filter header");
                        click(player,27,ClickType.LEFT);
                        later(plugin,() -> {
                            require(top(player).getItem(1) != null && top(player).getItem(53) == null,"filtered page");
                            open(player,"aw ui",36);
                            click(player,30,ClickType.LEFT);
                            later(plugin,() -> {
                                require(top(player).getItem(24) != null && top(player).getItem(0) == null,"graphics tab");
                                open(player,"aw skill menu",27);
                                require(top(player).getItem(10) != null && top(player).getItem(12) != null,"three skill slots");
                                player.closeInventory();
                                plugin.getLogger().info("AW_PREMIUM_LOBBY_PASS pages=3 cards=84 details=true filters=true fallback=true permissions=true read_only=true");
                            });
                        });
                    });
                });
            });
        });
    }

    static void selection(JavaPlugin plugin,Player player) {
        var permissions = player.addAttachment(plugin);
        permissions.setPermission("augmentwild.admin",true);
        permissions.setPermission("augmentwild.admin.test",true);
        open(player,"aw admin test preview",54);
        require(top(player).getItem(42) != null,"experiment preview and distance");
        command(player,"aw admin test advance");
        later(plugin,() -> {
            open(player,"aw augment",36);
            for (int slot : new int[]{11,13,15,22,26,29,31,33})
                require(top(player).getItem(slot) != null,"choice slot " + slot);
            click(player,29,ClickType.LEFT);
            require(top(player).getSize() == 36,"comparison is read only");
            click(player,22,ClickType.LEFT);
            later(plugin,() -> {
                open(player,"aw augment",36);
                require(top(player).getItem(22).getType() == Material.BARRIER,"reroll spent once");
                click(player,11,ClickType.LEFT);
                later(plugin,() -> {
                    open(player,"aw menu",54);
                    click(player,10,ClickType.LEFT);
                    later(plugin,() -> {
                        require(top(player).getItem(1) != null && top(player).getItem(2) == null,"selected offer is owned");
                        plugin.getLogger().info("AW_PREMIUM_SELECTION_PASS choice=true reroll=true comparison=true lab=true");
                        skillControls(plugin,player,permissions);
                    });
                });
            });
        });
    }

    private static void skillControls(JavaPlugin plugin,Player player,org.bukkit.permissions.PermissionAttachment permissions) {
        for (String id : List.of("void_lord","bomb_alchemy","time_rewind"))
            command(player,"aw admin test grant " + player.getName() + " " + id);
        command(player,"aw skill bind 1 void_lord");
        command(player,"aw skill bind 2 bomb_alchemy");
        command(player,"aw skill bind 3 time_rewind");
        command(player,"aw skill select 1");
        open(player,"aw skill menu",27);
        require(top(player).getItem(6) != null && top(player).getItem(25) != null,"auto-bind preference and preview");
        player.closeInventory();
        try {
            var host = Bukkit.getPluginManager().getPlugin("AugmentWild");
            var service = host.getClass().getDeclaredField("matches");
            service.setAccessible(true);
            var matches = (io.github.augmentwild.paper.PaperMatchService) service.get(host);
            command(player,"aw skill input hotbar");
            player.setSneaking(true);
            var hotbar = new org.bukkit.event.player.PlayerItemHeldEvent(player,0,2);
            Bukkit.getPluginManager().callEvent(hotbar);
            require(!hotbar.isCancelled() && status(matches,player).contains("▶3"),"hotbar also keeps the vanilla selection");
            command(player,"aw skill input cycle");
            require(swap(player).isCancelled() && status(matches,player).contains("▶1"),"cycle switches from 3 to 1");
            require(swap(player).isCancelled() && status(matches,player).contains("▶1"),"duplicate cycle does not skip a slot");
            command(player,"aw skill input hotbar");
            later(plugin,() -> {
                require(swap(player).isCancelled(),"bound F consumed");
                require(status(matches,player).contains("재사용 대기"),"real cast enters cooldown");
                require(swap(player).isCancelled() && matches.castAugment(player,"void_lord").isEmpty(),"same tick cast deduplicated");
                driverAfterCast(plugin,player,permissions,matches);
            });
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("HUD test fixture access",failure);
        }
    }

    private static void driverAfterCast(JavaPlugin plugin,Player player,org.bukkit.permissions.PermissionAttachment permissions,
                                        io.github.augmentwild.paper.PaperMatchService matches) {
        later(plugin,() -> {
            require(matches.castAugment(player,"void_lord").contains("재사용 대기"),"cooldown rejection matches HUD");
            require(matches.castAugment(player,"bomb_alchemy").endsWith("발동"),"second skill casts independently");
            require(matches.castAugment(player,"time_rewind").contains("복귀 기록 대기"),"rewind awaits a real snapshot");
            player.setSneaking(false);
            plugin.getServer().getScheduler().runTaskLater(plugin,() -> {
                require(matches.castAugment(player,"time_rewind").endsWith("발동"),"third skill uses a safe historical position");
                require(status(matches,player).split("재사용 대기",-1).length == 4,"all three cooldowns are independent");
                releaseControls(plugin,player,permissions,matches);
            },105L);
        });
    }

    private static void releaseControls(JavaPlugin plugin,Player player,org.bukkit.permissions.PermissionAttachment permissions,
                                        io.github.augmentwild.paper.PaperMatchService matches) {
        try {
            var field = matches.getClass().getDeclaredField("placement");
            field.setAccessible(true);
            Object placement = field.get(matches);
            var protect = placement.getClass().getDeclaredMethod("protect",java.util.UUID.class,int.class);
            protect.setAccessible(true);
            protect.invoke(placement,player.getUniqueId(),1);
            require(status(matches,player).contains("부활 보호")
                    && matches.castAugment(player,"void_lord").contains("부활 보호"),"resurrection protection takes precedence over cooldown");
            command(player,"aw skill unbind");
            require(matches.skill(player,new String[]{"skill","use"}).contains("비었습니다"),"empty slot failure");
            player.setSneaking(true);
            require(!swap(player).isCancelled(),"empty slot leaves vanilla offhand action alone");
            player.setSneaking(false);
            var hotbar = new org.bukkit.event.player.PlayerItemHeldEvent(player,0,1);
            Bukkit.getPluginManager().callEvent(hotbar);
            require(!hotbar.isCancelled() && status(matches,player).contains("▶1"),"ordinary hotbar leaves skill selection alone");
            require(!swap(player).isCancelled(),"unmodified vanilla F");
            command(player,"aw skill input cycle");
            player.removeAttachment(permissions);
            plugin.getLogger().info("AW_HUD_INPUT_PASS slots=3 successful_casts=3 cooldown=true duplicate=true protection=true empty=true vanilla=true");
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Protection fixture access",failure);
        }
    }

    static void reconnected(JavaPlugin plugin,Player player) {
        String mode = player.getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey("augmentwild","skill_input"),org.bukkit.persistence.PersistentDataType.STRING);
        require("CYCLE".equals(mode),"input mode survives reconnect and snapshot restoration");
        open(player,"aw skill menu",27);
        require(top(player).getItem(18) == null,"finished match skill bindings do not survive");
        player.closeInventory();
        plugin.getLogger().info("AW_HUD_RECONNECT_PASS preference=true transient_slots_cleared=true");
    }

    private static String status(io.github.augmentwild.paper.PaperMatchService matches,Player p) {
        return matches.skill(p,new String[]{"skill","status"});
    }

    private static org.bukkit.event.player.PlayerSwapHandItemsEvent swap(Player player) {
        var event = new org.bukkit.event.player.PlayerSwapHandItemsEvent(player,
                new ItemStack(Material.STONE),new ItemStack(Material.AIR));
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }

    private static void open(Player p,String command,int size) {
        command(p,command);
        require(PaperMenu.protectedInventory(top(p)) && top(p).getSize() == size,command + " size " + top(p).getSize());
    }

    private static void command(Player p,String line) {
        var aw = java.util.Objects.requireNonNull(Bukkit.getPluginCommand("aw"));
        require(aw.execute(p,"aw",line.substring(3).split(" ")),"plugin command " + line);
    }

    private static org.bukkit.inventory.Inventory top(Player p) { return p.getOpenInventory().getTopInventory(); }

    private static InventoryClickEvent event(Player p,int slot,ClickType click,InventoryAction action) {
        var event = new InventoryClickEvent(p.getOpenInventory(),SlotType.CONTAINER,slot,click,action);
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }

    private static void click(Player p,int slot,ClickType click) {
        require(event(p,slot,click,InventoryAction.PICKUP_ALL).isCancelled(),"menu click is read-only " + slot);
    }

    private static void later(JavaPlugin plugin,Runnable action) {
        Bukkit.getScheduler().runTaskLater(plugin,action,3L);
    }

    private static void require(boolean check,String description) {
        if (!check) throw new AssertionError("Premium interface " + description);
    }
}
