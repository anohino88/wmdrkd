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
    private int page;
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

    static void browse(Player p, PaperArt art, BooleanSupplier allowed) {
        var view = new ArtGallery(p,art,PaperArt.Screen.LAB,"연출 실험실",6,art.previewKeys(),allowed);
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
        p.openInventory(view.inventory);
    }

    static void detail(Player p, PaperArt art, String title, ItemStack card) {
        var view = new ArtGallery(p,art,PaperArt.Screen.DETAIL,title,3,List.of(),() -> false);
        view.inventory.setItem(4,card);
        var lore = card.getItemMeta().lore();
        if (lore != null) for (int i = 0; i < Math.min(lore.size(),16); i++) {
            var note = new ItemStack(Material.PAPER);
            final Component text = lore.get(i);
            note.editMeta(meta -> meta.displayName(text));
            view.inventory.setItem(9+i,art.icon(p,note,i == 3 ? "gui/gauge" : "gui/detail"));
        }
        p.openInventory(view.inventory);
    }

    private void put(Player p, int slot, Material material, String model, String title, String... lore) {
        var item = PaperIntel.item(PaperIntel.card(material,title,lore));
        inventory.setItem(slot,art.icon(p,item,model));
    }

    private void paint(Player p) {
        inventory.clear();
        for (int i = 0; i < 45 && page * 45 + i < entries.size(); i++) {
            String key = entries.get(page * 45 + i);
            String model = switch (key.split("/")[0]) {
                case "augment" -> key;
                case "model" -> key.replace("model/","fx/");
                case "growth" -> "gui/result";
                case "screen" -> "gui/menu";
                default -> "gui/choice";
            };
            put(p,i,Material.AMETHYST_SHARD,model,art.label(key),key,"클릭 · 나에게만 미리보기");
        }
        SECTIONS.forEach((slot,type) -> put(p,slot,Material.BOOK,"gui/codex",
                switch (type) { case "augment/" -> "증강"; case "model/" -> "모델"; case "growth/" -> "성장";
                    case "rarity/" -> "선택 등급"; case "sound/" -> "효과음"; default -> "화면"; }));
        if (page > 0) put(p,45,Material.ARROW,"gui/previous","이전 페이지");
        put(p,49,Material.COMPARATOR,"gui/settings",simple ? "간소 연출" : "리소스팩 연출",art.packStatus(p),"클릭 · 품질 전환");
        if ((page + 1) * 45 < entries.size()) put(p,53,Material.ARROW,"gui/next","다음 페이지");
    }

    private void activate(Player p, int slot) {
        if (entries.isEmpty() || !allowed.getAsBoolean()) return;
        if (slot == 49) simple = !simple;
        else if (SECTIONS.containsKey(slot)) {
            String type = SECTIONS.get(slot);
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).startsWith(type)) { page = i / 45; break; }
            }
        }
        else if (slot == 45 && page > 0) page--;
        else if (slot == 53 && (page + 1) * 45 < entries.size()) page++;
        else if (slot < 45 && page * 45 + slot < entries.size()) {
            String key = entries.get(page * 45 + slot);
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
        String[] models = switch (screen) {
            case CHOICE -> switch (parts[1]) {
                case "gold" -> new String[]{"augment/steel_heart","augment/chain_impact","augment/xp_gold"};
                case "prismatic" -> new String[]{"augment/time_rewind","augment/void_lord","augment/xp_prism"};
                default -> new String[]{"augment/stack_dinosaur","augment/miner","augment/xp_silver"};
            };
            case WARNING -> new String[]{"gui/warning","gui/previous"};
            case SLOTS -> new String[]{"augment/shadow_dash","gui/slots","gui/disabled"};
            default -> new String[]{"gui/" + switch (screen) {
                case DETAIL -> "detail"; case CODEX -> "codex"; case JOURNAL -> "journal";
                case SETTINGS -> "settings"; case SUPPLY -> "supply"; case SCOUT -> "scout";
                case RESULT -> "result"; case LAB -> "lab"; default -> "menu";
            }};
        };
        for (int i = 0; i < models.length; i++) {
            int slot = rows == 1 ? 3 + i * 2 : 10 + i * 3;
            sample.put(p,slot,Material.PAPER,models[i],models[i],"미리보기");
        }
        if (rarity && !simple) art.choice(p,models[0].substring("augment/".length()),parts[1].toUpperCase(Locale.ROOT));
        p.openInventory(sample.inventory);
    }

    static final class Input implements Listener {
        private final JavaPlugin plugin;
        Input(JavaPlugin plugin) { this.plugin = plugin; }

        @EventHandler public void click(InventoryClickEvent event) {
            if (!(event.getView().getTopInventory().getHolder() instanceof ArtGallery view)) return;
            boolean blocked = event.isCancelled();
            int slot = PaperMenu.lock(event,false);
            if (blocked || slot < 0 || view.entries.isEmpty() || !(event.getWhoClicked() instanceof Player p)
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
