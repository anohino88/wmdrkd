package io.github.augmentwild.paper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
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
    private final Set<UUID> pending = new HashSet<>();
    private final Set<UUID> restoring = new HashSet<>();

    PlayerSnapshots(JavaPlugin plugin) {
        this.plugin = plugin;
        directory = plugin.getDataFolder().toPath().resolve("recovery");
        if (Files.isDirectory(directory)) {
            try (var files = Files.newDirectoryStream(directory, "*.yml")) {
                for (var file : files) {
                    String name = file.getFileName().toString();
                    pending.add(UUID.fromString(name.substring(0, name.length() - 4)));
                }
            } catch (IOException | IllegalArgumentException failure) {
                throw new IllegalStateException("Could not index player recovery files", failure);
            }
        }
    }

    private Path path(UUID id) { return directory.resolve(id + ".yml"); }

    boolean pending(UUID id) { return pending.contains(id); }
    boolean restoring(UUID id) { return restoring.contains(id); }

    void capture(Player player) throws IOException {
        if (!player.getItemOnCursor().getType().isAir()) {
            throw new IOException("Participant must empty the inventory cursor: " + player.getUniqueId());
        }
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
        yaml.set("absorption", player.getAbsorptionAmount());
        yaml.set("hunger", player.getFoodLevel());
        yaml.set("saturation", player.getSaturation());
        yaml.set("exhaustion", player.getExhaustion());
        yaml.set("flight", player.getAllowFlight());
        yaml.set("flying", player.isFlying());
        yaml.set("effects", player.getActivePotionEffects().stream().toList());
        MatchFiles.save(path, yaml);
        pending.add(player.getUniqueId());
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
        player.setAbsorptionAmount(0);
    }

    boolean restore(Player player, BiPredicate<Player, Location> move) {
        Path path = path(player.getUniqueId());
        UUID id = player.getUniqueId();
        if (!pending(id)) return true;
        if (player.isDead() || !player.isOnline() || !restoring.add(id)) return false;
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
            var original = new Location(world, yaml.getDouble("x", Double.NaN), yaml.getDouble("y", Double.NaN),
                    yaml.getDouble("z", Double.NaN), (float) yaml.getDouble("yaw", Double.NaN),
                    (float) yaml.getDouble("pitch", Double.NaN));
            int slot = yaml.getInt("held-slot", -1);
            int level = yaml.getInt("level", -1);
            double exp = yaml.getDouble("exp", -1);
            double health = yaml.getDouble("health", -1);
            double absorption = yaml.getDouble("absorption", 0);
            int totalExp = yaml.getInt("total-exp", -1);
            int hunger = yaml.getInt("hunger", -1);
            float saturation = (float) yaml.getDouble("saturation", Double.NaN);
            float exhaustion = (float) yaml.getDouble("exhaustion", Double.NaN);
            boolean allowFlight = yaml.getBoolean("flight");
            boolean flying = allowFlight && yaml.getBoolean("flying");
            if (slot < 0 || slot > 8 || level < 0 || !Double.isFinite(exp) || exp < 0 || exp > 1
                    || !Double.isFinite(health) || health <= 0 || !Double.isFinite(absorption) || absorption < 0 || totalExp < 0 || hunger < 0
                    || !Float.isFinite(saturation) || saturation < 0 || !Float.isFinite(exhaustion) || exhaustion < 0
                    || !yaml.isBoolean("flight") || !yaml.isBoolean("flying")) {
                throw new IllegalArgumentException("Invalid recovery slot, XP, or health");
            }
            original.checkFinite();
            if (!move.test(player, original)) return false;
            player.closeInventory();
            if (player.getGameMode() != mode) player.setGameMode(mode);
            if (player.getGameMode() != mode) return false;
            // Overwrite the prior partial state on a retry; never add snapshot items.
            player.getInventory().setContents(items);
            player.getInventory().setHeldItemSlot(slot);
            player.setLevel(level);
            player.setExp((float) exp);
            player.setTotalExperience(totalExp);
            player.setFoodLevel(hunger);
            player.setSaturation(saturation);
            player.setExhaustion(exhaustion);
            player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
            for (var effect : savedEffects) {
                if (!player.addPotionEffect(effect)) return false;
            }
            var maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            double targetHealth = maxHealth == null ? health : Math.min(maxHealth.getValue(), health);
            player.setHealth(targetHealth);
            player.setAbsorptionAmount(absorption);
            player.setAllowFlight(allowFlight);
            player.setFlying(flying);
            if (player.getGameMode() != mode || !Arrays.equals(items, player.getInventory().getContents())
                    || player.getInventory().getHeldItemSlot() != slot || player.getLevel() != level
                    || player.getExp() != (float) exp || player.getTotalExperience() != totalExp
                    || player.getFoodLevel() != hunger || player.getSaturation() != saturation
                    || player.getExhaustion() != exhaustion || Math.abs(player.getHealth() - targetHealth) > 0.00001
                    || Math.abs(player.getAbsorptionAmount() - absorption) > 0.00001
                    || player.getAllowFlight() != allowFlight || player.isFlying() != flying
                    || !new HashSet<>(player.getActivePotionEffects()).equals(new HashSet<>(savedEffects))) {
                throw new IllegalStateException("Restored state did not match the recovery snapshot");
            }
            player.saveData();
            Files.delete(path);
            pending.remove(id);
            return true;
        } catch (IOException | InvalidConfigurationException | RuntimeException failure) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Recovery failed for " + player.getUniqueId(), failure);
            return false;
        } finally {
            restoring.remove(id);
        }
    }
}
