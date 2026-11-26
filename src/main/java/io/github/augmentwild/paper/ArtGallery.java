package io.github.augmentwild.paper;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Private, cosmetic views. No inventory contents are ever copied into player storage. */
final class ArtGallery implements PaperMenu {
    private final UUID owner;
    private final Inventory inventory;
    private final PaperArt art;
    private final BooleanSupplier allowed;
    private final List<String> entries;
    private static final java.util.Map<Integer,String> SECTIONS = java.util.Map.of(
            46,"augment/",47,"model/",48,"cinema/",50,"rarity/",51,"sound/",52,"screen/");
    private Runnable back, primary;
    private int page;
    private Mode mode = Mode.PREVIEW;
    private enum Mode { PREVIEW, GRANT, REMOVE, CAST }
    private static final int PAGE_SIZE = 41;
    private static final int[] DISTANCES = {3,8,16,24};
    private boolean simple;
    private int distanceIndex;
    private boolean repeat;
    private static final List<ArtPacing> FILM_SPEEDS = List.of(
            ArtPacing.BALANCED,ArtPacing.CINEMATIC,ArtPacing.SHOWCASE,ArtPacing.LEGACY);
    private int speed;
    private boolean liveGuide, liveStatus;
    private List<String> displayedRows = List.of();

    private ArtGallery(Player p, PaperArt art, PaperArt.Screen screen, String title, int rows,
                       List<String> entries, BooleanSupplier allowed) {
        this.owner = p.getUniqueId();
        this.art = art;
        this.entries = entries;
        this.allowed = allowed;
        this.inventory = Bukkit.createInventory(this,rows * 9,art.title(p,screen,title));
    }

    @Override public Inventory getInventory() { return inventory; }
    @Override public boolean availableWhileRestricted() { return true; }

    static void browse(Player p, PaperArt art, BooleanSupplier allowed, String filter) {
        var keys = new java.util.ArrayList<>(art.previewKeys());
        for (String action : List.of("stationary","moving","high","shield","report","clear")) keys.add("dummy/" + action);
        for (String preset : io.github.augmentwild.augment.AugmentCombinations.PRESETS.keySet()) keys.add("preset/" + preset);
        for (String control : List.of("gear","heal","shield","advance","cooldowns")) keys.add("control/" + control);
        for (var evolution : io.github.augmentwild.augment.AugmentEvolution.values()) keys.add("evolve/" + evolution.augment);
        keys.add("control/stopfx");
        for (String demo : List.of("compare","simultaneous","lifetime","repeat","metrics","supply_normal_open","supply_premium_open","supply_normal_cancel","supply_premium_cancel")) keys.add("demo/" + demo);
        if (!filter.isBlank()) keys.removeIf(key -> !key.contains(filter) && !art.label(key).contains(filter));
        if (keys.isEmpty()) { p.sendMessage("일치하는 실험 항목이 없습니다."); return; }
        var view = new ArtGallery(p,art,PaperArt.Screen.LAB,"전투 실험실",6,keys,allowed);
        view.paint(p);
        p.openInventory(view.inventory);
    }

    static void read(Player p, PaperArt art, PaperArt.Screen screen, String title, List<String> rows) {
        boolean supply = screen == PaperArt.Screen.SUPPLY;
        var view = new ArtGallery(p,art,screen,title,supply ? 3 : 6,List.of(),() -> false);
        view.liveGuide = supply;
        view.liveStatus = screen == PaperArt.Screen.STATUS;
        if (supply) {
            view.put(p,4,Material.CHEST,"gui/supply","보급 현황","공개된 정보만 표시합니다");
            view.updateGuide(p,rows);
        } else {
            String symbol = switch (screen) {
                case ADMIN -> "diagnostics";
                case STATUS -> "status";
                default -> "journal";
            };
            view.put(p,4,Material.WRITABLE_BOOK,"gui/" + symbol,title);
            view.updateRows(p,symbol,rows);
        }
        view.returnTo(p,() -> p.performCommand("aw menu"));
        p.openInventory(view.inventory);
    }

    private final java.util.Map<Integer,Runnable> navigation = new java.util.HashMap<>();

    static void history(Player p,PaperArt art,List<String> lines,List<Runnable> details,Runnable previous,Runnable next) {
        var view = new ArtGallery(p,art,PaperArt.Screen.JOURNAL,"나의 경기 기록",6,List.of(),() -> false);
        view.put(p,4,Material.WRITABLE_BOOK,"gui/journal","나의 경기 기록");
        view.updateRows(p,"journal",lines.isEmpty() ? List.of("조회 가능한 경기 기록 없음") : lines);
        for (int i=0;i<details.size();i++) view.navigation.put(9+i,details.get(i));
        if (previous != null) {
            view.put(p,45,Material.ARROW,"gui/previous","이전 페이지");
            view.navigation.put(45,previous);
        }
        if (next != null) {
            view.put(p,51,Material.ARROW,"gui/next","다음 페이지");
            view.navigation.put(51,next);
        }
        view.returnTo(p,() -> p.performCommand("aw menu"));
        p.openInventory(view.inventory);
    }

    boolean isStatusFor(Player viewer) { return liveStatus && owner.equals(viewer.getUniqueId()); }

    void updateRows(Player p,String symbol,List<String> rows) {
        if (rows.equals(displayedRows)) return;
        for (int i = rows.size(); i < Math.min(36,displayedRows.size()); i++) inventory.clear(9+i);
        displayedRows = List.copyOf(rows);
        for (int i = 0; i < Math.min(36,rows.size()); i++) {
            String row = rows.get(i);
            String[] columns = row.split(" · ",2);
            String name = columns[0];
            var lines = new java.util.ArrayList<String>();
            if (name.length() > 22) {
                int split = name.lastIndexOf(' ',22);
                if (split <= 0) split = 22;
                lines.addAll(InterfaceTheme.wrap(name.substring(split).strip()));
                name = name.substring(0,split);
            }
            if (columns.length > 1) lines.addAll(InterfaceTheme.wrap(columns[1]));
            put(p,9+i,Material.PAPER,"gui/" + symbol,name,lines.toArray(String[]::new));
        }
    }

    static void cards(Player p, PaperArt art, String title,String model,List<PaperIntel.Card> rows) {
        var view = new ArtGallery(p,art,PaperArt.Screen.CODEX,title,4,List.of(),() -> false);
        view.put(p,4,Material.BOOK,"gui/" + model,title);
        for (int i = 0; i < Math.min(14,rows.size()); i++)
            view.inventory.setItem(10+i/7*9+i%7,art.icon(p,PaperIntel.item(rows.get(i)),"gui/" + model));
        view.returnTo(p,() -> p.performCommand("aw menu"));
        p.openInventory(view.inventory);
    }

    static void track(Player p, PaperArt art,List<String> lines,Runnable track) {
        var view = new ArtGallery(p,art,PaperArt.Screen.TRACK,"적 탐지",3,List.of(),() -> false);
        view.put(p,4,Material.COMPASS,"gui/track","탐지 규칙",lines.toArray(String[]::new));
        view.put(p,13,Material.DIAMOND,"gui/start","탐지 실행","대상이 확인되면 비용을 소비합니다");
        view.primary = track;
        view.returnTo(p,() -> p.performCommand("aw menu"));
        p.openInventory(view.inventory);
    }

    boolean isGuideFor(Player player) { return liveGuide && owner.equals(player.getUniqueId()); }
    void updateGuide(Player player, List<String> lines) {
        for (int i = 0; i < 3; i++) {
            int slot = 10 + i * 3;
            if (i < lines.size()) put(player,slot,Material.COMPASS,"gui/supply",lines.get(i));
            else inventory.clear(slot);
        }
    }

    static void detail(Player p,PaperArt art,String id,String title,ItemStack card,List<Component> effect,List<Component> growth,List<Component> relations,Runnable back) {
        var view = new ArtGallery(p,art,PaperArt.Screen.DETAIL,title,3,List.of(),() -> false);
        view.inventory.setItem(4,card);
        art.inlay(p,view.inventory,"rail_left",0,1,2,3);
        art.inlay(p,view.inventory,"rail_right",5,8);
        view.put(p,6,Material.AMETHYST_SHARD,"proc/" + id,"외형 미리보기",art.packStatus(p));
        view.put(p,7,Material.BLAZE_POWDER,"gui/showcase","발동 연출 미리보기","경기 참가 중 사용 가능 · 본인에게만 표시");
        view.detailPreview = id;
        view.detailPanel(p,10,Material.WRITTEN_BOOK,"detail","효과와 발동 조건",effect);
        view.detailPanel(p,13,Material.EXPERIENCE_BOTTLE,"evolution","성장과 진화",growth);
        view.detailPanel(p,16,Material.END_CRYSTAL,"synergy","조합과 공명",relations);
        view.returnTo(p,back);
        p.openInventory(view.inventory);
    }

    private void detailPanel(Player p,int slot,Material material,String model,String title,List<Component> lines) {
        var stack = InterfaceTheme.card(material,title,InterfaceTheme.Tone.SECTION,List.of());
        stack.editMeta(meta -> meta.lore(lines.isEmpty() ? List.of(Component.text("해당 없음")) : lines));
        inventory.setItem(slot,art.icon(p,stack,"gui/" + model));
    }

    private void returnTo(Player p, Runnable action) {
        back = action;
        put(p,inventory.getSize()-1,Material.ARROW,"gui/previous","이전 화면");
    }

    private void put(Player p, int slot, Material material, String model, String title, String... lore) {
        var item = PaperIntel.item(PaperIntel.card(material,title,lore));
        inventory.setItem(slot,art.icon(p,item,model));
    }

    private void paint(Player p) {
        inventory.clear();
        for (int i = 0; i < PAGE_SIZE && page * PAGE_SIZE + i < entries.size(); i++) {
            String key = entries.get(page * PAGE_SIZE + i);
            String model = switch (key.split("/")[0]) {
                case "augment" -> key;
                case "cinema" -> "gui/showcase";
                case "model" -> key.replace("model/","fx/");
                case "growth" -> "gui/result";
                case "screen" -> "gui/menu";
                default -> "gui/choice";
            };
            put(p,i,Material.AMETHYST_SHARD,model,art.label(key),key,key.startsWith("augment/") ? "현재 동작 · " + mode : "클릭 · " + key);
        }
        art.inlay(p,inventory,"bezel",45,53);
        put(p,41,Material.CLOCK,"gui/gauge","촬영 속도 · " + FILM_SPEEDS.get(speed).label,
                "정상 → 0.75× → 0.5× → 8.0 원본", "시그니처와 파트에 적용 · 공개 예고는 고정");
        put(p,42,Material.SPYGLASS,"gui/showcase","촬영 거리 · " + DISTANCES[distanceIndex] + "블록",
                "시선 앞에 연출 배치 · 클릭하여 3 / 8 / 16 / 24블록 전환");
        put(p,43,Material.REPEATER,"gui/reroll","반복 · " + (repeat ? "3회" : "1회"),
                "클릭 · 촬영 연출의 반복 횟수 전환");
        put(p,44,Material.LEVER,"gui/settings","증강 클릭 동작 · " + mode,
                "클릭 · 미리보기 / 실제 지급 / 제거 / 보유 스킬 발동");
        SECTIONS.forEach((slot,type) -> put(p,slot,Material.BOOK,"gui/" + switch (type) {
                    case "augment/" -> "codex"; case "model/" -> "owned"; case "cinema/" -> "showcase";
                    case "rarity/" -> "tier_prismatic"; case "sound/" -> "resonance"; default -> "settings";
                },
                switch (type) { case "augment/" -> "증강"; case "model/" -> "모델"; case "cinema/" -> "촬영 연출";
                    case "rarity/" -> "선택 등급"; case "sound/" -> "효과음"; default -> "화면"; }));
        if (page > 0) put(p,45,Material.ARROW,"gui/previous","이전 페이지");
        put(p,49,Material.COMPARATOR,"gui/settings",simple ? "간소 연출" : "리소스팩 연출",art.packStatus(p),"클릭 · 간소 표현 비교", "LOW~ULTRA · /aw ui 에서 지정");
        if ((page + 1) * PAGE_SIZE < entries.size()) put(p,53,Material.ARROW,"gui/next","다음 페이지");
    }

    private String detailPreview;

    private void activate(Player p, int slot) {
        if (entries.isEmpty()) {
            var navigate = navigation.get(slot);
            if (navigate != null) { navigate.run(); return; }
            if (slot == 13 && primary != null) { primary.run(); return; }
            if (slot == 7 && detailPreview != null) {
                if (!art.canPreview(p)) p.sendMessage("활성 경기에서 발동 연출을 미리 볼 수 있습니다.");
                else {
                    p.closeInventory();
                    art.preview(p,"augment/" + detailPreview,false);
                }
                return;
            }
            if (back != null && slot == inventory.getSize()-1) back.run();
            return;
        }
        if (!allowed.getAsBoolean()) return;
        if (slot == 41) speed = (speed + 1) % FILM_SPEEDS.size();
        else if (slot == 42) distanceIndex = (distanceIndex + 1) % DISTANCES.length;
        else if (slot == 43) repeat = !repeat;
        else if (slot == 44) mode = Mode.values()[(mode.ordinal()+1) % Mode.values().length];
        else if (slot == 49) simple = !simple;
        else if (SECTIONS.containsKey(slot)) {
            String type = SECTIONS.get(slot);
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).startsWith(type)) { page = i / PAGE_SIZE; break; }
            }
        }
        else if (slot == 45 && page > 0) page--;
        else if (slot == 53 && (page + 1) * PAGE_SIZE < entries.size()) page++;
        else if (slot < PAGE_SIZE && page * PAGE_SIZE + slot < entries.size()) {
            String key = entries.get(page * PAGE_SIZE + slot);
            var parts = key.split("/",2);
            if (parts[0].equals("cinema")) {
                p.closeInventory();
                if (!art.cinema(p,parts[1],allowed,DISTANCES[distanceIndex],repeat ? 3 : 1,simple,FILM_SPEEDS.get(speed)))
                    p.sendMessage("시야와 청크가 확보된 지점을 조준하세요.");
                else p.sendMessage("피해나 보상이 없는 촬영 연출 · " + art.label(key));
                p.sendMessage(Component.text("↩ 연출 실험실로 돌아가기").clickEvent(
                        net.kyori.adventure.text.event.ClickEvent.runCommand("/aw admin test preview")));
                return;
            }
            if (parts[0].equals("demo")) {
                if (parts[1].equals("metrics")) p.sendMessage(art.metrics());
                else {
                    p.closeInventory();
                    p.sendMessage("전투 피해가 없는 연출 미리보기 · " + parts[1]);
                    art.demo(p,parts[1],allowed);
                }
                return;
            }
            if (parts[0].equals("evolve") || parts[0].equals("dummy") || parts[0].equals("preset") || parts[0].equals("control")
                    || parts[0].equals("augment") && mode != Mode.PREVIEW) {
                if (key.equals("control/stopfx")) art.stopPreview(p);
                else {
                    String command = switch (parts[0]) {
                        case "dummy" -> "dummy " + parts[1];
                        case "control" -> parts[1].equals("advance") ? "advance" : parts[1] + " " + p.getName();
                        case "evolve" -> "progress " + p.getName() + " " + parts[1] + " " + Integer.MAX_VALUE;
                        case "preset" -> "preset " + p.getName() + " " + parts[1];
                        default -> mode.name().toLowerCase(Locale.ROOT) + " " + p.getName() + " " + parts[1];
                    };
                    p.performCommand("aw admin test " + command);
                }
                return;
            }
            if (key.startsWith("screen/") || key.startsWith("rarity/")) sample(p,key);
            else {
                p.closeInventory();
                art.preview(p,key,simple);
                p.sendMessage(Component.text("↩ 연출 실험실로 돌아가기").clickEvent(
                        net.kyori.adventure.text.event.ClickEvent.runCommand("/aw admin test preview")));
            }
            return;
        } else return;
        paint(p);
    }

    private void sample(Player p, String key) {
        var parts = key.split("/");
        boolean rarity = parts[0].equals("rarity");
        var screen = rarity ? PaperArt.Screen.CHOICE : PaperArt.Screen.valueOf(parts[1].toUpperCase(Locale.ROOT));
        int rows = screen.rows;
        var sample = new ArtGallery(p,art,screen,"미리보기 · " + parts[1],rows,List.of(),() -> false);
        var slots = art.sampleCards(screen);
        if (rarity) {
            String[] cards = art.rarityCards(parts[1]);
            for (int i = 0; i < cards.length; i++) slots.put(11 + i * 2,cards[i]);
            if (!simple && cards.length > 0) art.choice(p,cards[0].substring("augment/".length()),parts[1].toUpperCase(Locale.ROOT));
        }
        slots.forEach((slot,model) -> sample.put(p,slot,Material.PAPER,model,art.label(model),"예시 데이터 · 미리보기"));
        sample.returnTo(p,() -> { if (allowed.getAsBoolean()) p.openInventory(inventory); });
        p.openInventory(sample.inventory);
    }

    static final class Input implements Listener {
        private final JavaPlugin plugin;
        Input(JavaPlugin plugin) { this.plugin = plugin; }

        @EventHandler public void click(InventoryClickEvent event) {
            if (!(event.getView().getTopInventory().getHolder() instanceof ArtGallery view)) return;
            boolean blocked = event.isCancelled();
            int slot = PaperMenu.lock(event,false);
            if (blocked || slot < 0 || view.entries.isEmpty() && view.back == null || !(event.getWhoClicked() instanceof Player p)
                    || !p.getUniqueId().equals(view.owner)) return;
            Bukkit.getScheduler().runTask(plugin,() -> {
                if (p.isOnline() && p.getOpenInventory().getTopInventory().getHolder() == view) view.activate(p,slot);
            });
        }

        @EventHandler public void drag(InventoryDragEvent event) {
            if (event.getView().getTopInventory().getHolder() instanceof ArtGallery) event.setCancelled(true);
        }
    }
}
