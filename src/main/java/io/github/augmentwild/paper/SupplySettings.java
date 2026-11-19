package io.github.augmentwild.paper;

import io.github.augmentwild.domain.SupplyPlan;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

record SupplySettings(List<SupplyPlan.Window> windows, long announceSeconds, long coordinateSeconds,
                      long openingNanos, double range, double movementTolerance, int separation,
                      int minHeight, int maxHeight, List<List<Reward>> normal, List<List<Reward>> premium) {
    record Reward(Material material, int amount, PotionType potion, Enchantment enchantment, int level) {
        ItemStack create() {
            var item = new ItemStack(material, amount);
            if (potion != null) {
                var meta = (PotionMeta) item.getItemMeta();
                meta.setBasePotionType(potion);
                item.setItemMeta(meta);
            }
            if (enchantment != null) {
                if (material == Material.ENCHANTED_BOOK) {
                    var meta = (EnchantmentStorageMeta) item.getItemMeta();
                    meta.addStoredEnchant(enchantment, level, false);
                    item.setItemMeta(meta);
                } else item.addEnchantment(enchantment, level);
            }
            return item;
        }
    }

    static SupplySettings load(FileConfiguration config, long gathering) {
        var windows = List.of("first-normal", "second-normal", "premium").stream().map(id -> {
            String path = "supply.windows." + id;
            return new SupplyPlan.Window(config.getInt(path + ".min-seconds"), config.getInt(path + ".max-seconds"));
        }).toList();
        long announce = config.getLong("supply.announce-seconds"), coordinates = config.getLong("supply.coordinates-seconds");
        double range = config.getDouble("supply.interaction-distance"), tolerance = config.getDouble("supply.movement-tolerance");
        int separation = config.getInt("supply.minimum-separation");
        int minHeight = config.getInt("supply.min-height"), maxHeight = config.getInt("supply.max-height");
        if (coordinates < 1 || announce <= coordinates || windows.getFirst().first() < announce
                || windows.get(0).last() >= windows.get(1).first() || windows.get(1).last() >= windows.getLast().first()
                || windows.getLast().last() > gathering || !(range > 0 && range <= 16)
                || !(tolerance >= 0 && tolerance <= .5) || separation < 1 || minHeight > maxHeight) {
            throw new IllegalArgumentException("Invalid supply settings");
        }
        return new SupplySettings(windows, announce, coordinates, IntelSettings.time(config, "supply.opening-seconds"),
                range, tolerance, separation, minHeight, maxHeight, rewards(config, "normal"), rewards(config, "premium"));
    }

    private static List<List<Reward>> rewards(FileConfiguration config, String key) {
        var rows = config.getList("supply.rewards." + key);
        if (rows == null || rows.isEmpty()) throw new IllegalArgumentException("Missing supply rewards " + key);
        var result = new ArrayList<List<Reward>>();
        for (Object row : rows) {
            if (!(row instanceof List<?> entries) || entries.isEmpty() || entries.size() > 12) {
                throw new IllegalArgumentException("Invalid supply reward row " + key);
            }
            var rewards = new ArrayList<Reward>();
            for (Object entry : entries) rewards.add(parse(String.valueOf(entry)));
            result.add(List.copyOf(rewards));
        }
        return List.copyOf(result);
    }

    private static Reward parse(String entry) {
        var fields = entry.split(":", -1);
        var base = fields[0].split("\\*", -1);
        String name = base[0];
        Material material = name.equals("BOOK") && fields.length == 3 ? Material.ENCHANTED_BOOK : Material.matchMaterial(name);
        int amount = base.length == 2 ? Integer.parseInt(base[1]) : 1;
        if (material == null || !material.isItem() || material.isAir() || base.length > 2
                || amount < 1 || amount > material.getMaxStackSize()) throw new IllegalArgumentException("Invalid supply item " + entry);
        PotionType potion = null;
        Enchantment enchantment = null;
        int level = 0;
        if (fields.length == 2 && material == Material.POTION) potion = PotionType.valueOf(fields[1]);
        else if (fields.length == 3) {
            enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(fields[1].toLowerCase(java.util.Locale.ROOT)));
            level = Integer.parseInt(fields[2]);
            if (enchantment == null || level < 1 || level > enchantment.getMaxLevel()
                    || material != Material.ENCHANTED_BOOK && !enchantment.canEnchantItem(new ItemStack(material))) {
                throw new IllegalArgumentException("Invalid supply enchantment " + entry);
            }
        } else if (fields.length != 1) throw new IllegalArgumentException("Invalid supply reward " + entry);
        return new Reward(material, amount, potion, enchantment, level);
    }
}
