package io.github.augmentwild.paper;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.Inventory;

/** Plugin inventory items are display-only; clicks may invoke an owner-validated control. */
public interface PaperMenu extends InventoryHolder {
    default boolean availableWhileRestricted() { return false; }

    static boolean protectedInventory(Inventory inventory) { return inventory.getHolder() instanceof PaperMenu; }

    static int lock(InventoryClickEvent event, boolean rightClick) {
        event.setCancelled(true);
        return controlSlot(event.getClick(), event.getRawSlot(), event.getView().getTopInventory().getSize(), rightClick);
    }

    static int controlSlot(ClickType click, int rawSlot, int upperSize, boolean rightClick) {
        if (rawSlot < 0 || rawSlot >= upperSize) return -1;
        return click == ClickType.LEFT || rightClick && click == ClickType.RIGHT ? rawSlot : -1;
    }
}
