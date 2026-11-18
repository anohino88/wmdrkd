package io.github.augmentwild.paper;

import java.util.Optional;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

final class PaperRespawnRestrictions implements Listener {
    private final Predicate<Player> restricted;

    PaperRespawnRestrictions(Predicate<Player> restricted) {
        this.restricted = restricted;
    }

    /** An opt-in same-world test point, checked without loading chunks. */
    static Optional<Location> safeTestSpawn(FileConfiguration config, Location vanilla) {
        if (!config.getBoolean("respawn.test-return.enabled")) return Optional.empty();
        World world = vanilla.getWorld();
        if (world == null || !world.getName().equals(config.getString("respawn.test-return.world"))) return Optional.empty();
        int x = config.getInt("respawn.test-return.x");
        int y = config.getInt("respawn.test-return.y");
        int z = config.getInt("respawn.test-return.z");
        if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 1 || !world.isChunkLoaded(x >> 4, z >> 4)) {
            return Optional.empty();
        }
        var floor = world.getBlockAt(x, y - 1, z);
        if (!safeFloor(floor.getType()) || !world.getBlockAt(x, y, z).getType().isAir()
                || !world.getBlockAt(x, y + 1, z).getType().isAir()) return Optional.empty();
        var point = new Location(world, x + 0.5, y, z + 0.5, vanilla.getYaw(), vanilla.getPitch());
        return world.getWorldBorder().isInside(point) ? Optional.of(point) : Optional.empty();
    }

    private static boolean safeFloor(org.bukkit.Material material) {
        return switch (material) {
            case GRASS_BLOCK, DIRT, STONE, COBBLESTONE, SMOOTH_STONE, STONE_BRICKS -> true;
            default -> false;
        };
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void consume(PlayerItemConsumeEvent event) {
        if (restricted.test(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void launch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player player && restricted.test(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void move(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to != null && (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ())
                && restricted.test(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent event) {
        if (restricted.test(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent event) {
        if (restricted.test(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void interactEntity(PlayerInteractEntityEvent event) {
        if (restricted.test(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void damage(EntityDamageEvent event) {
        if ((event.getEntity() instanceof Player victim && restricted.test(victim))
                || (event.getDamageSource().getCausingEntity() instanceof Player attacker && restricted.test(attacker))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void breakBlock(BlockBreakEvent event) {
        if (restricted.test(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void placeBlock(BlockPlaceEvent event) {
        if (restricted.test(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void emptyBucket(PlayerBucketEmptyEvent event) {
        if (restricted.test(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fillBucket(PlayerBucketFillEvent event) {
        if (restricted.test(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void pickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && restricted.test(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void drop(PlayerDropItemEvent event) {
        if (restricted.test(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void swap(PlayerSwapHandItemsEvent event) {
        if (restricted.test(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void open(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && restricted.test(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void click(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && restricted.test(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void drag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && restricted.test(player)) event.setCancelled(true);
    }
}
