package io.github.augmentwild.paper;

import java.util.List;
import org.bukkit.inventory.ItemStack;

/** Preflight a reward in copied storage, committing neither partial items nor dropped leftovers. */
public final class SupplyInventory {
    private SupplyInventory() { }

    public static ItemStack[] fit(ItemStack[] storage, int inventoryLimit, List<ItemStack> reward) {
        var result = storage.clone();
        for (int slot = 0; slot < result.length; slot++) {
            if (result[slot] != null) result[slot] = result[slot].clone();
        }
        for (ItemStack item : reward) {
            int left = item.getAmount();
            int limit = Math.min(inventoryLimit, item.getMaxStackSize());
            for (var existing : result) {
                if (left == 0) break;
                if (existing != null && existing.isSimilar(item) && existing.getAmount() < limit) {
                    int added = Math.min(left, limit - existing.getAmount());
                    existing.setAmount(existing.getAmount() + added);
                    left -= added;
                }
            }
            for (int slot = 0; slot < result.length && left > 0; slot++) {
                if (result[slot] != null && !result[slot].getType().isAir()) continue;
                int added = Math.min(left, limit);
                result[slot] = item.clone();
                result[slot].setAmount(added);
                left -= added;
            }
            if (left != 0) return null;
        }
        return result;
    }
}
