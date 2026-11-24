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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
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

    // Map physical inventory positions to the existing actions. Headers have no action.
    private static final java.util.Map<Integer,Integer> MAIN_ACTIONS = java.util.Map.ofEntries(
            java.util.Map.entry(9,10), java.util.Map.entry(10,11), java.util.Map.entry(11,16),
            java.util.Map.entry(12,15), java.util.Map.entry(14,12), java.util.Map.entry(15,18),
            java.util.Map.entry(16,14), java.util.Map.entry(17,13), java.util.Map.entry(18,21),
            java.util.Map.entry(19,22), java.util.Map.entry(20,19), java.util.Map.entry(21,17),
            java.util.Map.entry(22,20), java.util.Map.entry(23,23), java.util.Map.entry(24,24),
            java.util.Map.entry(25,25), java.util.Map.entry(26,26));

    private static final class Menu implements PaperMenu {
        final UUID owner;
        final UUID match;
        final boolean skills;
        final Inventory inventory;
        final List<String> available;
        Menu(PaperArt art, Player player, UUID match, boolean skills, List<String> available) {
            this.owner = player.getUniqueId();
            this.match = match;
            this.skills = skills;
            this.available = List.copyOf(available);
            this.inventory = Bukkit.createInventory(this, 27,
                    art.title(player,skills ? PaperArt.Screen.SLOTS : PaperArt.Screen.MENU,skills ? "증강 스킬 · 빠른 설정" : "증강 야생 · 빠른 메뉴"));
        }
        @Override public boolean availableWhileRestricted() { return true; }
        @Override public Inventory getInventory() { return inventory; }
    }

    private static final class ConfirmStop implements PaperMenu {
        final UUID owner;
        final UUID match;
        final Inventory inventory;
        ConfirmStop(PaperArt art, Player player, UUID match) {
            this.owner = player.getUniqueId();
            this.match = match;
            this.inventory = Bukkit.createInventory(this, 9, art.title(player,PaperArt.Screen.WARNING,"1인 전투 실험 종료 확인"));
            inventory.setItem(3, PaperIntel.item(PaperIntel.card(Material.BARRIER, "실험 종료 확인")));
            inventory.setItem(5, PaperIntel.item(PaperIntel.card(Material.LIME_DYE, "취소 · 메뉴로 돌아가기")));
            art.button(player,inventory,3,"warning");
            art.button(player,inventory,5,"previous");
        }
        @Override public boolean availableWhileRestricted() { return true; }
        @Override public Inventory getInventory() { return inventory; }
    }

    private enum ConfirmKind { LEAVE, START }

    private static final class ConfirmDecision implements PaperMenu {
        final UUID owner;
        final UUID match;
        final ConfirmKind kind;
        final Inventory inventory;
        ConfirmDecision(PaperArt art, Player player, UUID match, ConfirmKind kind) {
            owner = player.getUniqueId();
            this.match = match;
            this.kind = kind;
            inventory = Bukkit.createInventory(this, 9, art.title(player,PaperArt.Screen.WARNING,
                    kind == ConfirmKind.LEAVE ? "경기 탈퇴 확인" : "관리자 경기 시작 확인"));
            inventory.setItem(3, PaperIntel.item(PaperIntel.card(Material.RED_WOOL,
                    kind == ConfirmKind.LEAVE ? "확인 · 경기 나가기" : "확인 · 접속자 대상으로 시작")));
            inventory.setItem(5, PaperIntel.item(PaperIntel.card(Material.LIME_WOOL, "취소 · 메뉴로 돌아가기")));
            art.button(player,inventory,3,"warning");
            art.button(player,inventory,5,"previous");
        }
        @Override public boolean availableWhileRestricted() { return true; }
        @Override public Inventory getInventory() { return inventory; }
    }

    public PaperQuickMenu(JavaPlugin plugin, PaperMatchService matches) {
        this.plugin = plugin;
        this.matches = matches;
    }

    private static void item(Menu menu, int slot, Material material, String name, String... details) {
        menu.inventory.setItem(slot, PaperIntel.item(PaperIntel.card(material, name, details)));
    }

    public String open(Player player) {
        if (!player.hasPermission("augmentwild.play")) return "플레이어 권한이 없습니다.";
        if (matches.picking(player)) return matches.openAugments(player);
        Menu menu = new Menu(matches.art(),player, matches.menuSession(), false, List.of());
        item(menu,4,Material.NETHER_STAR,matches.isLobby() ? "경기 대기실" : "진행 중인 경기",
                matches.inMatch(player) ? "참가 상태 · 참가 중" : "참가 상태 · 미참가",
                "웅크리기 + 빈손 우클릭 · 이 화면 열기");
        item(menu, 10, matches.inMatch(player) ? Material.RED_DYE : Material.LIME_DYE,
                matches.inMatch(player) ? "경기 나가기 · 확인 필요" : "경기 참가",
                matches.inMatch(player) ? "실수 방지를 위해 종료 여부를 다시 확인합니다" : "자동 참가보다 우선 배정됩니다");
        item(menu, 11, Material.CLOCK, "경기 현황", matches.status(player).split(" [|] "));
        item(menu,12,matches.inMatch(player) ? Material.ENCHANTED_BOOK : Material.BARRIER,"증강 선택",
                matches.inMatch(player) ? "현재 선택 또는 교환 주문 확인" : "선택 불가 · 경기 참가 필요");
        item(menu,18,Material.BOOK,"증강 도감 · 84종","희귀도·능력·보유 상태별 필터");
        item(menu, 13, Material.COMPARATOR, "화면 설정 · 프리셋 지원", "전체 표시 / 전투 집중 / 최소 표시");
        item(menu, 14, Material.NETHER_STAR, "증강 스킬 / 퀵슬롯", "1~3번 슬롯을 클릭으로 선택하고 지정합니다");
        item(menu, 15, Material.DIAMOND, "가까운 적 추적 · 다이아몬드 소비", "평소에는 다이아몬드를 들고 우클릭");
        item(menu, 16, Material.COMPASS, "보급 길잡이", "나침반을 들고 공중 우클릭으로도 확인 가능");
        item(menu, 21, Material.WRITABLE_BOOK, "전투 기록");
        item(menu, 22, Material.PAPER, "최근 경기 결과");
        PaperArt art = matches.art();
        for (var control : java.util.Map.of(4,"menu",12,matches.inMatch(player) ? "choice" : "disabled",13,"settings",16,"supply",18,"codex",21,"report",22,"result").entrySet()) {
            int slot = control.getKey();
            menu.inventory.setItem(slot,art.icon(player,menu.inventory.getItem(slot),"gui/" + control.getValue()));
        }
        item(menu,19,Material.PAINTING,"리소스팩",art.packStatus(player),"클릭 · 상태, 재요청, 안전 모드");
        art.button(player,menu.inventory,10,matches.inMatch(player) ? "leave" : "join");
        art.button(player,menu.inventory,11,"status");
        art.button(player,menu.inventory,15,"track");
        art.button(player,menu.inventory,19,"pack");
        item(menu, 26, Material.ARROW, "메뉴 새로고침");
        art.button(player,menu.inventory,14,"slots");
        art.button(player,menu.inventory,26,"reroll");
        if (player.hasPermission("augmentwild.admin") && player.hasPermission("augmentwild.admin.test")) {
            if (matches.soloInProgress(player)) {
                item(menu,20,Material.SPYGLASS,"전투 실험실");
                item(menu,17,Material.TARGET,"고체력 연습 표적 설치");
                art.button(player,menu.inventory,20,"lab");
            }
            if (matches.soloInProgress(player)) item(menu, 24, Material.BARRIER, "1인 전투 실험 종료");
            else if (matches.isLobby()) item(menu, 23, Material.ENDER_EYE, "1인 전투 실험 시작 · 관리자 전용");
        }
        if (player.hasPermission("augmentwild.admin") && matches.isLobby())
            item(menu, 25, Material.EMERALD, "일반 경기 시작 · 관리자", "최소 참가자 충족 시 시작", "실수 방지를 위해 확인 화면이 열립니다");
        art.button(player,menu.inventory,17,"target");
        art.button(player,menu.inventory,23,"solo");
        art.button(player,menu.inventory,24,"leave");
        art.button(player,menu.inventory,25,"start");
        var controls = menu.inventory.getContents();
        for (int slot = 9; slot < menu.inventory.getSize(); slot++)
            menu.inventory.setItem(slot,MAIN_ACTIONS.containsKey(slot) ? controls[MAIN_ACTIONS.get(slot)] : null);
        item(menu,1,Material.COMPASS,"경기 정보","참가 · 경기 현황 · 보급");
        item(menu,7,Material.ENCHANTED_BOOK,"증강 관리","선택 · 도감 · 스킬");
        art.button(player,menu.inventory,1,"status");
        art.button(player,menu.inventory,7,"codex");
        art.button(player,menu.inventory,4,"atlas");
        art.inlay(player,menu.inventory,"rail_left",0,2,3);
        art.inlay(player,menu.inventory,"rail_right",5,6,8);
        art.inlay(player,menu.inventory,"bezel",13);
        player.openInventory(menu.inventory);
        return "빠른 메뉴 · 웅크리기 + 빈손 우클릭(공중)으로 다시 열 수 있습니다.";
    }

    public String openSkills(Player player) {
        if (matches.picking(player)) return matches.openAugments(player);
        Menu menu = new Menu(matches.art(),player, matches.menuSession(), true, matches.availableSkills(player));
        item(menu,4,Material.CLOCK,"스킬 전환 입력",PlayerUi.skillInput(player).label,
                "화면 설정 또는 /aw skill input 에서 변경");
        item(menu, 10, Material.IRON_SWORD, "1번 스킬 슬롯 선택");
        item(menu, 11, Material.IRON_SWORD, "2번 스킬 슬롯 선택");
        item(menu, 12, Material.IRON_SWORD, "3번 스킬 슬롯 선택");
        item(menu, 13, Material.ARROW, "다음 슬롯");
        item(menu, 14, Material.BLAZE_POWDER, "선택된 스킬 사용");
        item(menu, 15, Material.BARRIER, "선택 슬롯 해제");
        int index = 0;
        for (String id : menu.available) {
            if (index >= 9) break;
            item(menu, 18 + index++, Material.ENCHANTED_BOOK, "지정 · " + matches.art().label("augment/" + id),id);
        }
        item(menu, 26, Material.ARROW, "빠른 메뉴로 돌아가기");
        String status = matches.skill(player, new String[]{"skill", "status"});
        String[] slots = status.split(" [|] ");
        for (int i = 0; i < 3 && i < slots.length; i++) {
            item(menu,10+i,slots[i].startsWith("▶") ? Material.GOLDEN_SWORD : Material.IRON_SWORD,
                    slots[i],"클릭해서 이 슬롯 선택");
        }
        for (int i = 0; i < Math.min(menu.available.size(),8); i++) {
            int slot = 18 + i;
            menu.inventory.setItem(slot,matches.art().icon(player,menu.inventory.getItem(slot),"augment/" + menu.available.get(i)));
        }
        matches.art().button(player,menu.inventory,13,"next");
        matches.art().button(player,menu.inventory,15,"disabled");
        matches.art().button(player,menu.inventory,26,"previous");
        player.openInventory(menu.inventory);
        return "스킬 슬롯을 고른 뒤 아래 증강을 클릭해 지정하세요.";
    }

    /** First-time players are shown the controls once instead of needing a help command. */
    @EventHandler
    public void firstJoinHint(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("augmentwild.play")) return;
        NamespacedKey key = new NamespacedKey(plugin, "quick_menu_hint_seen");
        if (player.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) return;
        player.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.sendMessage("§b[AugmentWild] §f웅크리기 + 빈손 우클릭(공중) = 게임 메뉴");
            player.sendMessage("§7다이아 우클릭: 적 추적 · 나침반 공중 우클릭: 보급 안내 · 스킬: 웅크리기 + F");
        }, 20L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void gesture(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_AIR
                || !event.getPlayer().hasPermission("augmentwild.play")) return;
        Material held = event.getItem() == null ? Material.AIR : event.getItem().getType();
        Player player = event.getPlayer();
        if (held == Material.COMPASS && matches.inMatch(player)) {
            event.setCancelled(true);
            player.sendMessage(matches.guide(player));
        } else if (held == Material.AIR && player.isSneaking()) {
            event.setCancelled(true);
            player.sendMessage(open(player));
        }
    }

    private String confirmDecision(Player player, ConfirmKind kind) {
        if (kind == ConfirmKind.LEAVE && !matches.inMatch(player))
            return "현재 참가한 경기가 없습니다.";
        if (kind == ConfirmKind.START && (!player.hasPermission("augmentwild.admin") || !matches.isLobby()))
            return "관리자 경기 시작을 사용할 수 없습니다.";
        player.openInventory(new ConfirmDecision(matches.art(),player, matches.menuSession(), kind).inventory);
        return kind == ConfirmKind.LEAVE ? "경기에서 나갈지 확인하세요." : "일반 경기를 시작할지 확인하세요.";
    }

    private String confirmStop(Player player) {
        if (!player.hasPermission("augmentwild.admin") || !player.hasPermission("augmentwild.admin.test")
                || !matches.soloInProgress(player)) return "진행 중인 본인 1인 실험이 없습니다.";
        player.openInventory(new ConfirmStop(matches.art(),player, matches.menuSession()).inventory);
        return "실험 종료 여부를 확인하세요.";
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ConfirmDecision confirm) {
            boolean blocked = event.isCancelled();
            int selection = PaperMenu.lock(event, false);
            if (blocked || selection < 0 || !(event.getWhoClicked() instanceof Player p)
                    || !p.getUniqueId().equals(confirm.owner)) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!p.isOnline() || !confirm.match.equals(matches.menuSession())
                        || p.getOpenInventory().getTopInventory().getHolder() != confirm) return;
                p.closeInventory();
                if (selection == 3) {
                    if (confirm.kind == ConfirmKind.LEAVE && matches.inMatch(p)) {
                        p.sendMessage(matches.leave(p));
                    } else if (confirm.kind == ConfirmKind.START && p.hasPermission("augmentwild.admin")
                            && matches.isLobby()) p.sendMessage(matches.start(p));
                } else if (selection == 5) p.sendMessage(open(p));
            });
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof ConfirmStop confirm) {
            boolean blocked = event.isCancelled();
            int selection = PaperMenu.lock(event, false);
            if (blocked || selection < 0 || !(event.getWhoClicked() instanceof Player p)
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
        if (blocked || slot < 0 || menu.inventory.getItem(slot) == null || !(event.getWhoClicked() instanceof Player player)
                || !player.getUniqueId().equals(menu.owner)) return;
        if (!menu.skills && !MAIN_ACTIONS.containsKey(slot)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !menu.match.equals(matches.menuSession())) return;
            matches.art().sound(player,"ui",org.bukkit.Sound.UI_BUTTON_CLICK);
            if (player.getOpenInventory().getTopInventory().getHolder() != menu) return;
            player.closeInventory();
            String response = menu.skills ? skillClick(player, menu, slot) : mainClick(player, MAIN_ACTIONS.get(slot));
            if (response != null && !response.isBlank()) player.sendMessage(response);
        });
    }

    private String mainClick(Player player, int slot) {
        return switch (slot) {
            case 10 -> matches.inMatch(player) ? confirmDecision(player, ConfirmKind.LEAVE) : matches.join(player);
            case 11 -> matches.status(player);
            case 12 -> matches.openAugments(player);
            case 13 -> matches.ui(player);
            case 14 -> openSkills(player);
            case 15 -> player.hasPermission("augmentwild.track")
                    ? matches.locate(player) : "위치 추적 권한이 없습니다.";
            case 16 -> matches.guide(player);
            case 17 -> matches.labCommand(player,new String[]{"dummy","high"});
            case 18 -> matches.openCodex(player);
            case 19 -> matches.artCommand(player,"menu",false);
            case 20 -> matches.labCommand(player,new String[]{"preview"});
            case 21 -> matches.combatLog(player);
            case 22 -> matches.result(player);
            case 23 -> player.hasPermission("augmentwild.admin") && player.hasPermission("augmentwild.admin.test")
                    ? matches.soloStart(player) : "관리자 권한이 없습니다.";
            case 24 -> confirmStop(player);
            case 25 -> player.hasPermission("augmentwild.admin")
                    ? confirmDecision(player, ConfirmKind.START) : "관리자 권한이 없습니다.";
            case 26 -> open(player);
            default -> "";
        };
    }

    private String skillClick(Player player, Menu menu, int slot) {
        String response = switch (slot) {
            case 10, 11, 12 -> matches.skill(player, new String[]{"skill", "select", Integer.toString(slot - 9)});
            case 13 -> matches.skill(player, new String[]{"skill", "next"});
            case 14 -> matches.skill(player, new String[]{"skill", "use"});
            case 15 -> matches.skill(player, new String[]{"skill", "unbind"});
            case 26 -> open(player);
            default -> slot >= 18 && slot < 18 + menu.available.size()
                    ? matches.skill(player, new String[]{"skill", "bind", menu.available.get(slot - 18)}) : "";
        };
        // Changes are applied using the same validated command path.
        if (slot != 14 && slot != 26 && slot >= 10 && slot <= 25) {
            openSkills(player);
        }
        return response;
    }

    @EventHandler public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu
                || event.getView().getTopInventory().getHolder() instanceof ConfirmStop
                || event.getView().getTopInventory().getHolder() instanceof ConfirmDecision) event.setCancelled(true);
    }
}
