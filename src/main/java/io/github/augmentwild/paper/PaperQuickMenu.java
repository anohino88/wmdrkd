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

    private static final class Menu implements PaperMenu {
        final UUID owner;
        final UUID match;
        final boolean skills;
        final Inventory inventory;
        final List<String> available;
        final java.util.Map<Integer,java.util.function.Supplier<String>> actions = new java.util.HashMap<>();
        Menu(PaperArt art, Player player, UUID match, boolean skills, List<String> available) {
            this.owner = player.getUniqueId();
            this.match = match;
            this.skills = skills;
            this.available = List.copyOf(available);
            this.inventory = Bukkit.createInventory(this, skills ? 27 : 54,
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

    private void section(Player p, Menu m, int slot, Material icon, String key, String name) {
        InterfaceTheme.put(matches.art(),p,m.inventory,slot,icon,key,name,InterfaceTheme.Tone.SECTION);
    }

    private void control(Player p, Menu m, int slot, Material icon, String key, String name,
                         java.util.function.Supplier<String> action, String... lore) {
        boolean enabled = action != null;
        InterfaceTheme.put(matches.art(),p,m.inventory,slot,enabled ? icon : Material.GRAY_DYE,
                enabled ? key : "sealed",name,enabled ? InterfaceTheme.Tone.NORMAL : InterfaceTheme.Tone.LOCKED,lore);
        if (enabled) m.actions.put(slot,action);
    }

    public String open(Player p) {
        if (!p.hasPermission("augmentwild.play")) return "플레이어 권한이 없습니다.";
        if (matches.picking(p)) return matches.openAugments(p);
        Menu m = new Menu(matches.art(),p,matches.menuSession(),false,List.of());
        boolean joined = matches.inMatch(p), lobby = matches.isLobby();
        boolean canCancel = matches.canCancelFromMenu(p);
        section(p,m,0,Material.COMPASS,"sector_match","경기 관제");
        control(p,m,1,Material.CLOCK,"status","경기 현황",() -> matches.statusMenu(p));
        control(p,m,2,Material.LIME_DYE,"join","참가",lobby && !joined ? () -> matches.join(p) : null,
                joined ? "이미 참가 중" : lobby ? "대기실 참가자 등록" : "경기 진행 중");
        control(p,m,3,Material.RED_DYE,"leave",lobby && !joined ? "자동 참가 거부" : "참가 취소",
                canCancel ? () -> confirmDecision(p,ConfirmKind.LEAVE) : null,
                canCancel ? "확인 후 적용" : "취소할 참가 상태 없음");
        control(p,m,5,Material.ENCHANTED_BOOK,"choice","증강 선택",joined && !lobby ? () -> matches.openAugments(p) : null);
        control(p,m,6,Material.PAPER,"result","최근 경기 결과",() -> matches.result(p));
        control(p,m,7,Material.WRITABLE_BOOK,"journal","경기 안내",() -> matches.newcomerGuide(p));
        section(p,m,9,Material.ENCHANTED_BOOK,"sector_archive","증강 기록관");
        control(p,m,10,Material.CHEST,"owned","보유 증강",() -> matches.augmentSection(p,"owned"));
        control(p,m,11,Material.BOOK,"codex","증강 도감",() -> matches.openCodex(p),"희귀도 · 전투 계열 · 발동 방식");
        control(p,m,12,Material.EXPERIENCE_BOTTLE,"evolution","진화",() -> matches.augmentSection(p,"evolution"));
        control(p,m,13,Material.END_CRYSTAL,"synergy","시너지",() -> matches.augmentSection(p,"synergy"));
        control(p,m,14,Material.ECHO_SHARD,"resonance","계열 공명",() -> matches.augmentSection(p,"resonance"));
        section(p,m,18,Material.IRON_SWORD,"sector_combat","전투 장치");
        control(p,m,19,Material.NETHER_STAR,"slots","스킬 설정",() -> openSkills(p),"지정 · 선택 · 해제");
        control(p,m,20,Material.WRITABLE_BOOK,"report","전투 기록",() -> matches.combatLog(p));
        control(p,m,21,Material.COMPASS,"supply","보급",joined && !lobby ? () -> matches.guide(p) : null);
        control(p,m,22,Material.DIAMOND,"track","적 탐지",p.hasPermission("augmentwild.track") ? () -> matches.trackMenu(p) : null,
                "비용과 사용 조건 확인");
        control(p,m,23,Material.SKELETON_SKULL,"warning","사망 분석",() -> matches.deathReport(p));
        section(p,m,27,Material.COMPARATOR,"sector_settings","개인 설정");
        control(p,m,28,Material.BEACON,"settings","HUD 설정",() -> PlayerUi.open(p,matches.art(),PlayerUi.Tab.HUD));
        control(p,m,29,Material.AMETHYST_SHARD,"graphics","그래픽 설정",() -> PlayerUi.open(p,matches.art(),PlayerUi.Tab.GRAPHICS));
        control(p,m,30,Material.COMPASS,"slots","조작 설정",() -> PlayerUi.open(p,matches.art(),PlayerUi.Tab.CONTROLS));
        control(p,m,31,Material.PAINTING,"pack","리소스팩",() -> matches.artCommand(p,"menu",false),matches.art().packStatus(p));
        if (p.hasPermission("augmentwild.admin")) {
            section(p,m,36,Material.LECTERN,"sector_admin","운영");
            control(p,m,37,Material.EMERALD,"start","일반 경기 시작",lobby ? () -> confirmDecision(p,ConfirmKind.START) : null);
            control(p,m,38,Material.SPYGLASS,"diagnostics","서버 진단",() -> matches.adminMenu(p));
            if (p.hasPermission("augmentwild.admin.test")) {
                boolean solo = matches.soloInProgress(p);
                control(p,m,39,Material.ENDER_EYE,"solo","1인 전투 실험",lobby ? () -> matches.soloStart(p) : null);
                control(p,m,40,Material.SPYGLASS,"lab","실험실 미리보기",solo ? () -> matches.labCommand(p,new String[]{"preview"}) : null);
                control(p,m,41,Material.TARGET,"target","고체력 표적",solo ? () -> matches.labCommand(p,new String[]{"dummy","high"}) : null);
                control(p,m,42,Material.BARRIER,"warning","실험 종료",solo ? () -> confirmStop(p) : null);
            }
        }
        section(p,m,49,Material.NETHER_STAR,"atlas",lobby ? "대기실" : "진행 중인 경기");
        control(p,m,53,Material.ARROW,"reroll","메뉴 새로고침",() -> open(p));
        p.openInventory(m.inventory);
        return "";
    }

    public String openSkills(Player player) {
        if (!player.hasPermission("augmentwild.play")) return "플레이어 권한이 없습니다.";
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
        for (int i = 0; i < 3; i++) matches.art().button(player,menu.inventory,10+i,
                i < slots.length && slots[i].startsWith("▶") ? "selected" : "slots");
        matches.art().button(player,menu.inventory,4,"sector_combat");
        matches.art().button(player,menu.inventory,13,"next");
        matches.art().button(player,menu.inventory,14,"start");
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
        if (kind == ConfirmKind.LEAVE && !matches.canCancelFromMenu(player))
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
                    if (confirm.kind == ConfirmKind.LEAVE && matches.canCancelFromMenu(p)) {
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
        if (!menu.skills && !menu.actions.containsKey(slot)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !player.hasPermission("augmentwild.play") || !menu.match.equals(matches.menuSession())) return;
            matches.art().sound(player,"ui",org.bukkit.Sound.UI_BUTTON_CLICK);
            if (player.getOpenInventory().getTopInventory().getHolder() != menu) return;
            player.closeInventory();
            String response = menu.skills ? skillClick(player, menu, slot) : menu.actions.get(slot).get();
            if (response != null && !response.isBlank()) player.sendMessage(response);
        });
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
