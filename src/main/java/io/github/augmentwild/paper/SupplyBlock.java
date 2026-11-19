package io.github.augmentwild.paper;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.persistence.PersistentDataType;

/** A physical chest marker. Reward items live in the match, outside the block inventory. */
public final class SupplyBlock {
    private final Block block;
    private final BlockData previous;
    private final NamespacedKey key;
    private final String token;

    private SupplyBlock(Block block, BlockData previous, NamespacedKey key, String token) {
        this.block = block;
        this.previous = previous;
        this.key = key;
        this.token = token;
    }

    public static SupplyBlock place(Block block, NamespacedKey key, String token) {
        if (!block.getType().isAir()) return null;
        BlockData previous = block.getBlockData();
        block.setType(Material.CHEST, false);
        var chest = (Chest) block.getState();
        chest.getPersistentDataContainer().set(key, PersistentDataType.STRING, token);
        if (!chest.update(false, false)) {
            block.setBlockData(previous, false);
            return null;
        }
        return new SupplyBlock(block, previous, key, token);
    }

    public boolean present() {
        return block.getState() instanceof Chest chest
                && token.equals(chest.getPersistentDataContainer().get(key, PersistentDataType.STRING));
    }

    public void remove() {
        if (present()) block.setBlockData(previous, false);
    }
}
