package io.github.augmentwild.paper;

import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * No permanent inventory item or extra hotbar slot is required.
 * Sneak + empty main-hand right click in the air opens the match menu.
 */
public final class PaperQuickMenu implements Listener {
    private final JavaPlugin plugin;
    private final PaperMatchService matches;

    private static final class Menu implements PaperMenu {
        final UUID owner;
        final UUID match;
        final boolean skills;
        final Inventory inventory;
        final List<String> available;
        Menu(Player player, UUID match, boolean skills, List<String> available) {
            this.owner = player.getUniqueId();
            this.match = match;
            this.skills = skills;
            this.available = List.copyOf(available);
            this.inventory = Bukkit.createInventory(this, 27,
                    Component.text(skills ? "증강 스킬 · 빠른 설정" : "증강 야생 · 빠른 메뉴"));
        }
        @Override public boolean availableWhileRestricted() { return true; }
        @Override public Inventory getInventory() { return inventory; }
    }

    private static final class ConfirmStop implements PaperMenu {
        final UUID owner;
        final UUID match;
        final Inventory inventory;
        ConfirmStop(Player player, UUID match) {
            this.owner = player.getUniqueId();
            this.match = match;
            this.inventory = Bukkit.createInventory(this, 9, Component.text("1인 전투 실험 종료 확인"));
            inventory.setItem(3, PaperIntel.item(PaperIntel.card(Material.BARRIER, "실험 종료 확인")));
            inventory.setItem(5, PaperIntel.item(PaperIntel.card(Material.LIME_DYE, "취소 · 메뉴로 돌아가기")));
        }
        @Override public boolean availableWhileRestricted() { return true; }
        @Override public Inventory getInventory() { return inventory; }
    }

    public PaperQuickMenu(JavaPlugin plugin, PaperMatchService matches) {
        this.plugin = plugin;
        this.matches = matches;
    }

    private static void item(Menu menu, int slot, Material material, String name) {
        menu.inventory.setItem(slot, PaperIntel.item(PaperIntel.card(material, name)));
    }

    public String open(Player player) {
        if (!player.hasPermission("augmentwild.play")) return "플레이어 권한이 없습니다.";
        if (matches.picking(player)) return matches.openAugments(player);
        Menu menu = new Menu(player, matches.menuSession(), false, List.of());
        item(menu, 10, Material.LIME_DYE, matches.inMatch(player) ? "경기 나가기" : "경기 참가");
        item(menu, 11, Material.CLOCK, "경기 현황");
        item(menu, 12, Material.ENCHANTED_BOOK, "증강 선택 / 도감");
        item(menu, 13, Material.COMPARATOR, "화면 설정");
        item(menu, 14, Material.NETHER_STAR, "증강 스킬 / 퀵슬롯");
        item(menu, 15, Material.DIAMOND, "가까운 적 추적 · 다이아몬드 소비");
        item(menu, 16, Material.COMPASS, "보급 길잡이");
        item(menu, 21, Material.WRITABLE_BOOK, "전투 기록");
        item(menu, 22, Material.PAPER, "최근 경기 결과");
        if (player.hasPermission("augmentwild.admin") && player.hasPermission("augmentwild.admin.test")) {
            if (matches.soloInProgress(player)) item(menu, 24, Material.BARRIER, "1인 전투 실험 종료");
            else item(menu, 23, Material.ENDER_EYE, "1인 전투 실험 시작 · 관리자 전용");
        }
        player.openInventory(menu.inventory);
        return "빠른 메뉴 · 웅크리기 + 빈손 우클릭(공중)으로 다시 열 수 있습니다.";
    }

    private String openSkills(Player player) {
        Menu menu = new Menu(player, matches.menuSession(), true, matches.availableSkills(player));
        item(menu, 10, Material.IRON_SWORD, "1번 스킬 슬롯 선택");
        item(menu, 11, Material.IRON_SWORD, "2번 스킬 슬롯 선택");
        item(menu, 12, Material.IRON_SWORD, "3번 스킬 슬롯 선택");
        item(menu, 13, Material.ARROW, "다음 슬롯");
        item(menu, 14, Material.BLAZE_POWDER, "선택된 스킬 사용");
        item(menu, 15, Material.BARRIER, "선택 슬롯 해제");
        int index = 0;
        for (String id : menu.available) {
            if (index >= 9) break;
            item(menu, 18 + index++, Material.ENCHANTED_BOOK, "지정: " + id);
        }
        player.openInventory(menu.inventory);
        player.sendMessage(matches.skill(player, new String[]{"skill", "status"}));
        return "스킬 슬롯을 고른 뒤 아래 증강을 클릭해 지정하세요.";
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void gesture(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_AIR
                || !event.getPlayer().isSneaking()
                || event.getItem() != null && event.getItem().getType() != Material.AIR
                || !event.getPlayer().hasPermission("augmentwild.play")) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(open(event.getPlayer()));
    }

    private String confirmStop(Player player) {
        if (!player.hasPermission("augmentwild.admin") || !player.hasPermission("augmentwild.admin.test")
                || !matches.soloInProgress(player)) return "진행 중인 본인 1인 실험이 없습니다.";
        player.openInventory(new ConfirmStop(player, matches.menuSession()).inventory);
        return "실험 종료 여부를 확인하세요.";
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ConfirmStop confirm) {
            int selection = PaperMenu.lock(event, false);
            if (selection < 0 || !(event.getWhoClicked() instanceof Player p)
                    || !p.getUniqueId().equals(confirm.owner)) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!p.isOnline() || !confirm.match.equals(matches.menuSession())
                        || p.getOpenInventory().getTopInventory().getHolder() != confirm) return;
                p.closeInventory();
                if (selection == 3 && p.hasPermission("augmentwild.admin")
                        && p.hasPermission("augmentwild.admin.test") && matches.soloInProgress(p)) {
                    p.sendMessage(matches.stop());
                } else if (selection == 5) p.sendMessage(open(p));
            });
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        boolean blocked = event.isCancelled();
        int slot = PaperMenu.lock(event, false);
        if (blocked || slot < 0 || !(event.getWhoClicked() instanceof Player player)
                || !player.getUniqueId().equals(menu.owner)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !menu.match.equals(matches.menuSession())) return;
            if (player.getOpenInventory().getTopInventory().getHolder() != menu) return;
            player.closeInventory();
            String response = menu.skills ? skillClick(player, menu, slot) : mainClick(player, slot);
            if (response != null && !response.isBlank()) player.sendMessage(response);
        });
    }

    private String mainClick(Player player, int slot) {
        return switch (slot) {
            case 10 -> matches.inMatch(player) ? matches.leave(player) : matches.join(player);
            case 11 -> matches.status(player);
            case 12 -> matches.openAugments(player);
            case 13 -> matches.ui(player);
            case 14 -> openSkills(player);
            case 15 -> player.hasPermission("augmentwild.track")
                    ? matches.locate(player) : "위치 추적 권한이 없습니다.";
            case 16 -> matches.guide(player);
            case 21 -> matches.combatLog(player);
            case 22 -> matches.result(player);
            case 23 -> player.hasPermission("augmentwild.admin") && player.hasPermission("augmentwild.admin.test")
                    ? matches.soloStart(player) : "관리자 권한이 없습니다.";
            case 24 -> confirmStop(player);
            default -> open(player);
        };
    }

    private String skillClick(Player player, Menu menu, int slot) {
        String response = switch (slot) {
            case 10, 11, 12 -> matches.skill(player, new String[]{"skill", "select", Integer.toString(slot - 9)});
            case 13 -> matches.skill(player, new String[]{"skill", "next"});
            case 14 -> matches.skill(player, new String[]{"skill", "use"});
            case 15 -> matches.skill(player, new String[]{"skill", "unbind"});
            default -> slot >= 18 && slot < 18 + menu.available.size()
                    ? matches.skill(player, new String[]{"skill", "bind", menu.available.get(slot - 18)}) : "";
        };
        // Changes are applied using the same validated command path.
        if (slot != 14 && slot >= 10 && slot <= 26) {
            openSkills(player);
        }
        return response;
    }

    @EventHandler public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu
                || event.getView().getTopInventory().getHolder() instanceof ConfirmStop) event.setCancelled(true);
    }
}
