package io.github.augmentwild.paper;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Preflight a reward in copied storage, committing neither partial items nor dropped leftovers. */
public final class MatchInventory {
    private MatchInventory() { }

    public record Debit(ItemStack[] storage, ItemStack offhand) { }

    /** Null means insufficient funds. An unchanged offhand is represented by null in a successful result. */
    public static Debit remove(ItemStack[] storage, ItemStack offhand, Material kind, int amount) {
        if (amount <= 0) throw new IllegalArgumentException("Invalid item debit");
        var result = storage.clone();
        for (int i = 0; i < result.length && amount > 0; i++) {
            var item = result[i];
            if (item == null || item.getType() != kind) continue;
            int taken = Math.min(item.getAmount(), amount);
            amount -= taken;
            if (taken == item.getAmount()) result[i] = null;
            else {
                result[i] = item.clone();
                result[i].setAmount(item.getAmount() - taken);
            }
        }
        if (amount == 0) return new Debit(result, null);
        if (offhand.getType() != kind || offhand.getAmount() < amount) return null;
        var remaining = offhand.clone();
        remaining.setAmount(offhand.getAmount() - amount);
        return new Debit(result, remaining);
    }

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
