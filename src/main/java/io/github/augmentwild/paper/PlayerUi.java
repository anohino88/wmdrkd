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
        GROWTH("진화와 시너지"), KILL_FX("처치 연출"), AUTO("자동 발동 상태"),
        HOTBAR("스킬 전환 입력"), AUTO_BIND("빈 슬롯에 스킬 자동 등록");
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
    enum SkillInput {
        HOTBAR("웅크리기 + 핫바 1~3"),
        CYCLE("웅크리기 + F로 순환 · /aw skill use로 발동"),
        MENU("메뉴 또는 명령어로 전환");
        final String label;
        SkillInput(String label) { this.label = label; }
    }
    private static final NamespacedKey SKILL_INPUT = new NamespacedKey("augmentwild","skill_input");
    static SkillInput skillInput(Player player) {
        var name = player.getPersistentDataContainer().get(SKILL_INPUT,PersistentDataType.STRING);
        if (name != null) {
            try { return SkillInput.valueOf(name); }
            catch (IllegalArgumentException ignored) { /* Use the legacy preference below. */ }
        }
        return enabled(player,Option.HOTBAR) ? SkillInput.HOTBAR : SkillInput.MENU;
    }
    static void skillInput(Player player,SkillInput input) {
        player.getPersistentDataContainer().set(SKILL_INPUT,PersistentDataType.STRING,input.name());
    }

    enum Quality {
        LOW(4), STANDARD(8), HIGH(12), ULTRA(16);
        final int samples;
        Quality(int samples) { this.samples = samples; }
    }
    private static final NamespacedKey HIDE_AURAS = new NamespacedKey("augmentwild","hide_auras");
    private static final NamespacedKey REDUCE_FLASH = new NamespacedKey("augmentwild","reduce_flash");
    static boolean auras(Player p) { return !p.getPersistentDataContainer().has(HIDE_AURAS); }
    static boolean reducedFlash(Player p) { return p.getPersistentDataContainer().has(REDUCE_FLASH); }
    private static final NamespacedKey QUALITY = new NamespacedKey("augmentwild","art_quality");
    static Quality quality(Player p) {
        String value = p.getPersistentDataContainer().getOrDefault(QUALITY, PersistentDataType.STRING, "STANDARD");
        try { return Quality.valueOf(value); }
        catch (IllegalArgumentException ignored) { return Quality.STANDARD; }
    }
    private static final List<ArtPacing> LIVE_PACING = List.of(ArtPacing.FAST,ArtPacing.BALANCED,ArtPacing.CINEMATIC);
    private static final NamespacedKey PACING = new NamespacedKey("augmentwild","art_pacing");
    static ArtPacing pacing(Player player) {
        String saved = player.getPersistentDataContainer().getOrDefault(PACING,PersistentDataType.STRING,"BALANCED");
        return switch (saved) {
            case "FAST" -> ArtPacing.FAST;
            case "CINEMATIC" -> ArtPacing.CINEMATIC;
            default -> ArtPacing.BALANCED;
        };
    }

    private static void qualityButton(Player p, Menu menu) {
        menu.inventory.setItem(19,PaperIntel.item(PaperIntel.card(Material.ENDER_EYE,"지속 오라 · " + (auras(p) ? "ON" : "OFF"))));
        menu.inventory.setItem(21,PaperIntel.item(PaperIntel.card(Material.GLOWSTONE_DUST,"발광 최소화 · " + (reducedFlash(p) ? "ON" : "OFF"))));
        if (menu.art != null) menu.inventory.setItem(23,PaperIntel.item(PaperIntel.card(Material.SHIELD,
                "그래픽 안전 모드 · " + (PaperArt.safe(p) ? "켜짐" : "꺼짐"),"기본 글꼴·아이템·전투 연출")));
        menu.inventory.setItem(25,PaperIntel.item(PaperIntel.card(Material.CLOCK,"연출 속도 · " + pacing(p),
                "본인 장식과 잔여 연출 · " + pacing(p).label, "공개 타격·위험 예고는 서버 공통")));
        menu.inventory.setItem(24,PaperIntel.item(PaperIntel.card(Material.AMETHYST_SHARD,
                "연출 품질 · " + quality(p), "클릭 · 다음 단계")));
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
        final PaperArt art;
        Menu(Player player, boolean settings, PaperArt art) {
            owner = player.getUniqueId();
            this.settings = settings;
            this.art = art;
            inventory = Bukkit.createInventory(this, settings ? Math.max(27, ((Option.values().length + 8) / 9) * 9) : 27, art == null ? Component.text(settings ? "화면 설정" : "경기 결과") : art.title(player,settings ? PaperArt.Screen.SETTINGS : PaperArt.Screen.RESULT,settings ? "화면 설정" : "경기 결과"));
        }
        public Inventory getInventory() { return inventory; }
        public boolean availableWhileRestricted() { return true; }
    }
    public static String open(Player p) { return open(p,null); }

    static String open(Player p, PaperArt art) {
        var menu = new Menu(p, true,art);
        for (var option : Option.values()) paint(p, menu, option);
        menu.inventory.setItem(18, PaperIntel.item(PaperIntel.card(Material.BEACON, "기본 / 상세 프리셋",
                "클릭으로 STANDARD와 DETAILED 전환", "최근 프리셋 · " +
                p.getPersistentDataContainer().getOrDefault(HUD_PRESET,PersistentDataType.STRING,"STANDARD"))));
        menu.inventory.setItem(20, PaperIntel.item(PaperIntel.card(Material.IRON_SWORD, "전투 집중", "전투 HUD 중심 · 파티클 및 효과음 최소화")));
        menu.inventory.setItem(22, PaperIntel.item(PaperIntel.card(Material.GLASS_PANE, "최소 표시", "체력·피해·스킬·위험 경고만 표시")));
        if (art != null) menu.inventory.setItem(18,art.icon(p,menu.inventory.getItem(18),"gui/settings"));
        qualityButton(p,menu);
        menu.inventory.setItem(26, PaperIntel.item(PaperIntel.card(Material.ARROW, "빠른 메뉴로 돌아가기")));
        if (art != null) art.button(p,menu.inventory,26,"previous");
        p.openInventory(menu.inventory);
        return "화면 설정";
    }
    public static void toggle(Player p, Option option) {
        if (enabled(p, option)) p.getPersistentDataContainer().set(option.key, PersistentDataType.BYTE, (byte)1);
        else p.getPersistentDataContainer().remove(option.key);
    }

    /** Player-local, persistent presets. Neither inventory contents nor gameplay stats are changed. */
    enum Preset { ALL, STANDARD, COMBAT, MINIMAL, DETAILED }
    private static final NamespacedKey HUD_PRESET = new NamespacedKey("augmentwild","hud_preset");
    static void applyPreset(Player p, Preset preset) {
        var essentials = EnumSet.of(Option.HEALTH, Option.DAMAGE, Option.SKILL,
                Option.INCOMING, Option.CRISIS, Option.BORDER);
        var combat = EnumSet.of(Option.HEALTH, Option.DAMAGE, Option.SIDEBAR, Option.SKILL,
                Option.PROC, Option.INCOMING, Option.STATUS, Option.BORDER, Option.CRISIS,
                Option.GROWTH, Option.AUTO, Option.FEED);
        for (var option : Option.values()) {
            if (option == Option.HOTBAR || option == Option.AUTO_BIND) continue;
            boolean show = switch (preset) {
                case ALL, DETAILED -> true;
                case STANDARD -> option != Option.AUTO;
                case COMBAT -> combat.contains(option);
                case MINIMAL -> essentials.contains(option);
            };
            if (show) p.getPersistentDataContainer().remove(option.key);
            else p.getPersistentDataContainer().set(option.key, PersistentDataType.BYTE, (byte)1);
        }
    }
    private static void paint(Player p, Menu menu, Option option) {
        if (option == Option.HOTBAR) {
            menu.inventory.setItem(option.ordinal(),PaperIntel.item(PaperIntel.card(Material.COMPASS,
                    option.title + " · " + skillInput(p).label, "클릭 · 다음 입력 방식")));
            if (menu.art != null) menu.art.button(p,menu.inventory,option.ordinal(),"slots");
            return;
        }
        boolean on = enabled(p, option);
        menu.inventory.setItem(option.ordinal(), PaperIntel.item(PaperIntel.card(on ? Material.LIME_DYE : Material.GRAY_DYE,
                option.title + (on ? " ON" : " OFF"))));
        if (menu.art != null) menu.art.button(p,menu.inventory,option.ordinal(),on ? "enabled" : "disabled");
    }
    public static void result(Player player, List<String> lines) { result(player,lines,null); }

    static void result(Player player, List<String> lines, PaperArt art) {
        var menu = new Menu(player, false,art);
        if (!lines.isEmpty()) {
            menu.inventory.setItem(4,PaperIntel.item(PaperIntel.card(Material.NETHER_STAR,lines.get(0))));
            if (art != null) art.button(player,menu.inventory,4,"result");
        }
        for (int i = 1; i < Math.min(lines.size(),19); i++) {
            int slot = 8 + i;
            String line = lines.get(i);
            menu.inventory.setItem(slot,PaperIntel.item(PaperIntel.card(Material.PAPER,line)));
            if (art != null) art.button(player,menu.inventory,slot,line.startsWith("획득") ? "codex"
                    : line.startsWith("생존") ? "gauge" : line.startsWith("스킬") ? "slots" : "report");
        }
        player.openInventory(menu.inventory);
    }
    @EventHandler public void click(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        boolean cancelled = e.isCancelled();
        int slot = PaperMenu.lock(e, false);
        if (cancelled) return;
        if (!menu.settings || slot < 0 || !(e.getWhoClicked() instanceof Player p)
                || !p.getUniqueId().equals(menu.owner)) return;
        if (slot == 19 || slot == 21) {
            var key = slot == 19 ? HIDE_AURAS : REDUCE_FLASH;
            if (p.getPersistentDataContainer().has(key)) p.getPersistentDataContainer().remove(key);
            else p.getPersistentDataContainer().set(key,PersistentDataType.BYTE,(byte)1);
            qualityButton(p,menu);
            return;
        }
        if (slot == 23 && menu.art != null) {
            p.sendMessage(menu.art.toggleSafe(p));
            qualityButton(p,menu);
            return;
        }
        if (slot == 24) {
            var levels = Quality.values();
            var next = levels[(quality(p).ordinal() + 1) % levels.length];
            p.getPersistentDataContainer().set(QUALITY,PersistentDataType.STRING,next.name());
            qualityButton(p,menu);
            return;
        }
        if (slot == 25) {
            var next = LIVE_PACING.get((LIVE_PACING.indexOf(pacing(p)) + 1) % LIVE_PACING.size());
            p.getPersistentDataContainer().set(PACING,PersistentDataType.STRING,next.name());
            qualityButton(p,menu);
            return;
        }
        if (slot == 26) {
            var plugin = Bukkit.getPluginManager().getPlugin("AugmentWild");
            if (plugin != null) Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline()) p.performCommand("aw menu");
            });
            return;
        }
        Preset preset = switch (slot) {
            case 18 -> p.getPersistentDataContainer().getOrDefault(HUD_PRESET,PersistentDataType.STRING,"STANDARD")
                    .equals("DETAILED") ? Preset.STANDARD : Preset.DETAILED;
            case 20 -> Preset.COMBAT;
            case 22 -> Preset.MINIMAL;
            default -> null;
        };
        if (preset != null) {
            applyPreset(p, preset);
            p.getPersistentDataContainer().set(HUD_PRESET,PersistentDataType.STRING,preset.name());
            var tag = PaperIntel.item(PaperIntel.card(Material.BEACON,"기본 / 상세 프리셋","최근 프리셋 · " + preset));
            menu.inventory.setItem(18,menu.art == null ? tag : menu.art.icon(p,tag,"gui/settings"));
            for (var option : Option.values()) paint(p, menu, option);
            p.sendMessage("화면 설정을 변경했습니다 · " + switch (preset) {
                case ALL, DETAILED -> "상세 표시";
                case STANDARD -> "기본 표시";
                case COMBAT -> "전투 집중";
                case MINIMAL -> "최소 표시";
            });
        } else if (slot < Option.values().length) {
            var option = Option.values()[slot];
            if (option == Option.HOTBAR) {
                var inputs = SkillInput.values();
                skillInput(p,inputs[(skillInput(p).ordinal()+1) % inputs.length]);
            } else toggle(p, option);
            paint(p, menu, option);
        }
    }
    @EventHandler public void drag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }
}
