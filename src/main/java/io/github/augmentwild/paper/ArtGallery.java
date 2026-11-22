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
            46,"augment/",47,"model/",48,"growth/",50,"rarity/",51,"sound/",52,"screen/");
    private Runnable back;
    private int page;
    private Mode mode = Mode.PREVIEW;
    private enum Mode { PREVIEW, GRANT, REMOVE, CAST }
    private static final int PAGE_SIZE = 44;
    private boolean simple;

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
        keys.add("control/cooldowns");
        keys.add("control/stopfx");
        if (!filter.isBlank()) keys.removeIf(key -> !key.contains(filter) && !art.label(key).contains(filter));
        if (keys.isEmpty()) { p.sendMessage("일치하는 실험 항목이 없습니다."); return; }
        var view = new ArtGallery(p,art,PaperArt.Screen.LAB,"전투 실험실",6,keys,allowed);
        view.paint(p);
        p.openInventory(view.inventory);
    }

    static void read(Player p, PaperArt art, PaperArt.Screen screen, String title, List<String> rows) {
        boolean journal = screen == PaperArt.Screen.JOURNAL;
        var view = new ArtGallery(p,art,screen,title,journal ? 5 : 3,List.of(),() -> false);
        int index = 0;
        for (String row : rows) {
            int slot = journal ? index : 10 + index * 3;
            if (slot >= view.inventory.getSize()) break;
            view.put(p,slot,Material.PAPER,journal ? "gui/journal" : "gui/supply",row);
            index++;
        }
        view.returnTo(p,() -> p.performCommand("aw menu"));
        p.openInventory(view.inventory);
    }

    static void detail(Player p, PaperArt art, String title, ItemStack card, Runnable back) {
        var view = new ArtGallery(p,art,PaperArt.Screen.DETAIL,title,3,List.of(),() -> false);
        view.inventory.setItem(4,card);
        var lore = card.getItemMeta().lore();
        if (lore != null) for (int i = 0; i < Math.min(lore.size(),16); i++) {
            var note = new ItemStack(Material.PAPER);
            final Component text = lore.get(i);
            note.editMeta(meta -> meta.displayName(text));
            view.inventory.setItem(9+i,art.icon(p,note,i == 3 ? "gui/gauge" : "gui/detail"));
        }
        view.returnTo(p,back);
        p.openInventory(view.inventory);
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
                case "model" -> key.replace("model/","fx/");
                case "growth" -> "gui/result";
                case "screen" -> "gui/menu";
                default -> "gui/choice";
            };
            put(p,i,Material.AMETHYST_SHARD,model,art.label(key),key,key.startsWith("augment/") ? "현재 동작 · " + mode : "클릭 · " + key);
        }
        put(p,44,Material.LEVER,"gui/settings","증강 클릭 동작 · " + mode,
                "클릭 · 미리보기 / 실제 지급 / 제거 / 보유 스킬 발동");
        SECTIONS.forEach((slot,type) -> put(p,slot,Material.BOOK,"gui/codex",
                switch (type) { case "augment/" -> "증강"; case "model/" -> "모델"; case "growth/" -> "성장";
                    case "rarity/" -> "선택 등급"; case "sound/" -> "효과음"; default -> "화면"; }));
        if (page > 0) put(p,45,Material.ARROW,"gui/previous","이전 페이지");
        put(p,49,Material.COMPARATOR,"gui/settings",simple ? "간소 연출" : "리소스팩 연출",art.packStatus(p),"클릭 · 품질 전환");
        if ((page + 1) * PAGE_SIZE < entries.size()) put(p,53,Material.ARROW,"gui/next","다음 페이지");
    }

    private void activate(Player p, int slot) {
        if (entries.isEmpty()) {
            if (back != null && slot == inventory.getSize()-1) back.run();
            return;
        }
        if (!allowed.getAsBoolean()) return;
        if (slot == 44) mode = Mode.values()[(mode.ordinal()+1) % Mode.values().length];
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
            if (parts[0].equals("dummy") || parts[0].equals("preset") || parts[0].equals("control")
                    || parts[0].equals("augment") && mode != Mode.PREVIEW) {
                if (key.equals("control/stopfx")) art.stopPreview(p);
                else {
                    String command = switch (parts[0]) {
                        case "dummy" -> "dummy " + parts[1];
                        case "control" -> "cooldowns " + p.getName();
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
        int rows = switch (screen) { case WARNING -> 1; case LAB,CODEX -> 6; case JOURNAL,SCOUT -> 5; default -> 3; };
        var sample = new ArtGallery(p,art,screen,"미리보기 · " + parts[1],rows,List.of(),() -> false);
        var slots = art.sampleCards(screen);
        if (rarity) {
            String[] cards = art.rarityCards(parts[1]);
            for (int i = 0; i < cards.length; i++) slots.put(10 + i * 3,cards[i]);
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
