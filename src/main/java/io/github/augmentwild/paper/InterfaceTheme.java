package io.github.augmentwild.paper;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Slot-sized controls with native item and text fallbacks. */
final class InterfaceTheme {
    enum Tone {
        SECTION(0xf2d6a6), NORMAL(0xe8edf6), ACTIVE(0x8decbd), WARNING(0xffb49e), LOCKED(0xaeb4c1);
        final TextColor text;
        Tone(int rgb) { text = TextColor.color(rgb); }
    }

    static ItemStack card(Material material, String name, Tone tone, List<String> lines) {
        var item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Component.text(name,tone.text).decoration(TextDecoration.ITALIC,false)
                    .decoration(TextDecoration.BOLD,tone == Tone.SECTION));
            meta.lore(lines.stream().map(line -> Component.text(line,TextColor.color(0xd5dfeb))
                    .decoration(TextDecoration.ITALIC,false)).toList());
        });
        return item;
    }

    static void put(PaperArt art, Player viewer, Inventory inventory, int slot, Material material, String model,
                    String label, Tone tone, String... lore) {
        var item = card(material,label,tone,List.of(lore));
        inventory.setItem(slot,art == null ? item : art.icon(viewer,item,"gui/" + model));
    }

    static List<String> wrap(String text) {
        var rows = new java.util.ArrayList<String>();
        int start = 0, width = 0, space = -1;
        for (int cursor = 0; cursor < text.length();) {
            int code = text.codePointAt(cursor);
            if (code == ' ') space = cursor;
            width += code >= 0x2e80 ? 2 : 1;
            if (width > 38) {
                int breakAt = space > start ? space : cursor;
                rows.add(text.substring(start,breakAt).strip());
                start = breakAt;
                cursor = breakAt;
                width = 0;
                space = -1;
            } else cursor += Character.charCount(code);
        }
        if (start < text.length()) rows.add(text.substring(start).strip());
        return List.copyOf(rows);
    }

    private InterfaceTheme() {}
}
