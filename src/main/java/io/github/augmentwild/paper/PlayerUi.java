package io.github.augmentwild.paper;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.Component;

public final class PlayerUi implements Listener {
    public enum Option {
        HEALTH("상대 체력바"), DAMAGE("피해량 표시"), SIDEBAR("경기 사이드바"), SKILL("스킬 상태"),
        PROC("증강 발동 알림"), PARTICLES("강조 파티클"), SOUND("효과음"), FEED("킬 피드"), SUPPLY("보급 안내"),
        INCOMING("피격 방향"), STATUS("상태 효과"), BORDER("자기장 경고"), CRISIS("생존 위기"),
        GROWTH("진화와 시너지"), KILL_FX("처치 연출"), AUTO("자동 발동 상태");
        final String title;
        final NamespacedKey key;
        Option(String title) {
            this.title = title;
            key = new NamespacedKey("augmentwild", "ui_" + name().toLowerCase(Locale.ROOT));
        }
    }
    public static boolean enabled(Player player, Option option) {
        return !player.getPersistentDataContainer().has(option.key, PersistentDataType.BYTE);
    }
    private static final NamespacedKey FAVORITES = new NamespacedKey("augmentwild", "favorite_augments");
    public static Set<String> favorites(Player player) {
        String stored = player.getPersistentDataContainer().getOrDefault(FAVORITES, PersistentDataType.STRING, "");
        return stored.isEmpty() ? Set.of() : Set.copyOf(Arrays.asList(stored.split(",")));
    }
    public static boolean toggleFavorite(Player player, String id) {
        var ids = new TreeSet<>(favorites(player));
        if (!ids.remove(id)) {
            if (ids.size() >= 8) return false;
            ids.add(id);
        }
        if (ids.isEmpty()) player.getPersistentDataContainer().remove(FAVORITES);
        else player.getPersistentDataContainer().set(FAVORITES, PersistentDataType.STRING, String.join(",",ids));
        return true;
    }

    static void audience(Location at, Option option, java.util.function.Consumer<Player> send) {
        for (Player viewer : at.getNearbyPlayers(32))
            if (enabled(viewer,option)) send.accept(viewer);
    }
    private static class Menu implements PaperMenu {
        final UUID owner;
        final Inventory inventory;
        final boolean settings;
        Menu(Player player, boolean settings) {
            owner = player.getUniqueId();
            this.settings = settings;
            inventory = Bukkit.createInventory(this, settings ? ((Option.values().length + 8) / 9) * 9 : 27, Component.text(settings ? "화면 설정" : "경기 결과"));
        }
        public Inventory getInventory() { return inventory; }
        public boolean availableWhileRestricted() { return true; }
    }
    public static String open(Player p) {
        var menu = new Menu(p, true);
        for (var option : Option.values()) paint(p, menu, option);
        p.openInventory(menu.inventory);
        return "화면 설정";
    }
    public static void toggle(Player p, Option option) {
        if (enabled(p, option)) p.getPersistentDataContainer().set(option.key, PersistentDataType.BYTE, (byte)1);
        else p.getPersistentDataContainer().remove(option.key);
    }
    private static void paint(Player p, Menu menu, Option option) {
        boolean on = enabled(p, option);
        menu.inventory.setItem(option.ordinal(), PaperIntel.item(PaperIntel.card(on ? Material.LIME_DYE : Material.GRAY_DYE,
                option.title + (on ? " ON" : " OFF"))));
    }
    public static void result(Player player, List<String> lines) {
        var menu = new Menu(player, false);
        for (int i = 0; i < Math.min(lines.size(), menu.inventory.getSize()); i++)
            menu.inventory.setItem(i, PaperIntel.item(PaperIntel.card(Material.PAPER, lines.get(i))));
        player.openInventory(menu.inventory);
    }
    @EventHandler public void click(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        boolean cancelled = e.isCancelled();
        int slot = PaperMenu.lock(e, false);
        if (cancelled) return;
        if (!menu.settings || slot < 0 || slot >= Option.values().length || !(e.getWhoClicked() instanceof Player p) || !p.getUniqueId().equals(menu.owner)) return;
        var option = Option.values()[slot];
        toggle(p,option);
        paint(p, menu, option);
    }
    @EventHandler public void drag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }
}
