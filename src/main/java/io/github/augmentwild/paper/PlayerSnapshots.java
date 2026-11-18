package io.github.augmentwild.paper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.BiPredicate;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

/** The snapshot is written before moving or clearing any inventory. */
final class PlayerSnapshots {
    private final JavaPlugin plugin;
    private final Path directory;

    PlayerSnapshots(JavaPlugin plugin) {
        this.plugin = plugin;
        directory = plugin.getDataFolder().toPath().resolve("recovery");
    }

    private Path path(UUID id) { return directory.resolve(id + ".yml"); }

    boolean pending(UUID id) { return Files.exists(path(id)); }

    void capture(Player player) throws IOException {
        Path path = path(player.getUniqueId());
        if (Files.exists(path)) throw new IOException("Previous snapshot needs recovery: " + player.getUniqueId());
        var yaml = new YamlConfiguration();
        Location location = player.getLocation();
        yaml.set("world-id", location.getWorld().getUID().toString());
        yaml.set("x", location.getX());
        yaml.set("y", location.getY());
        yaml.set("z", location.getZ());
        yaml.set("yaw", location.getYaw());
        yaml.set("pitch", location.getPitch());
        yaml.set("mode", player.getGameMode().name());
        yaml.set("inventory", Arrays.asList(player.getInventory().getContents()));
        yaml.set("held-slot", player.getInventory().getHeldItemSlot());
        yaml.set("level", player.getLevel());
        yaml.set("exp", player.getExp());
        yaml.set("total-exp", player.getTotalExperience());
        yaml.set("health", player.getHealth());
        yaml.set("hunger", player.getFoodLevel());
        yaml.set("saturation", player.getSaturation());
        yaml.set("exhaustion", player.getExhaustion());
        yaml.set("flight", player.getAllowFlight());
        yaml.set("flying", player.isFlying());
        yaml.set("effects", player.getActivePotionEffects().stream().toList());
        MatchFiles.save(path, yaml);
    }

    void enterMatch(Player player) {
        player.closeInventory();
        player.getInventory().setContents(new ItemStack[player.getInventory().getContents().length]);
        player.setLevel(0);
        player.setExp(0);
        player.setTotalExperience(0);
        player.setGameMode(GameMode.SURVIVAL);
        player.setFlying(false);
        player.setAllowFlight(false);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
    }

    boolean restore(Player player, BiPredicate<Player, Location> move) {
        Path path = path(player.getUniqueId());
        if (!Files.exists(path)) return true;
        if (player.isDead() || !player.isOnline()) return false;
        try {
            var yaml = MatchFiles.load(path);
            var world = plugin.getServer().getWorld(UUID.fromString(yaml.getString("world-id", "")));
            if (world == null) return false;
            var inventory = yaml.getList("inventory");
            var effects = yaml.getList("effects");
            if (inventory == null || inventory.size() != player.getInventory().getContents().length || effects == null) {
                throw new IllegalArgumentException("Invalid recovery inventory or effects");
            }
            var items = inventory.stream().map(value -> (ItemStack) value).toArray(ItemStack[]::new);
            var savedEffects = effects.stream().map(PotionEffect.class::cast).toList();
            var mode = GameMode.valueOf(yaml.getString("mode", ""));
            var original = new Location(world, yaml.getDouble("x"), yaml.getDouble("y"), yaml.getDouble("z"),
                    (float) yaml.getDouble("yaw"), (float) yaml.getDouble("pitch"));
            if (!move.test(player, original)) return false;
            player.closeInventory();
            player.setGameMode(mode);
            player.getInventory().setContents(items);
            player.getInventory().setHeldItemSlot(yaml.getInt("held-slot"));
            player.setLevel(yaml.getInt("level"));
            player.setExp((float) yaml.getDouble("exp"));
            player.setTotalExperience(yaml.getInt("total-exp"));
            player.setFoodLevel(yaml.getInt("hunger"));
            player.setSaturation((float) yaml.getDouble("saturation"));
            player.setExhaustion((float) yaml.getDouble("exhaustion"));
            player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
            savedEffects.forEach(player::addPotionEffect);
            var maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            if (maxHealth != null) player.setHealth(Math.min(maxHealth.getValue(), yaml.getDouble("health")));
            player.setAllowFlight(yaml.getBoolean("flight"));
            player.setFlying(yaml.getBoolean("flight") && yaml.getBoolean("flying"));
            player.saveData();
            Files.delete(path);
            return true;
        } catch (IOException | InvalidConfigurationException | RuntimeException failure) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Recovery failed for " + player.getUniqueId(), failure);
            return false;
        }
    }
}
