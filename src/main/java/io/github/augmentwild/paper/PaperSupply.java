package io.github.augmentwild.paper;

import io.github.augmentwild.domain.SupplyPlan;
import io.github.augmentwild.domain.Timeline;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

final class PaperSupply implements Listener {
    private record Site(UUID world, int x, int y, int z) {
        static Site at(Location loc) {
            return new Site(loc.getWorld().getUID(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        }
    }
    private record ChunkSite(UUID world, int x, int z) {
        static ChunkSite at(Location loc) {
            return new ChunkSite(loc.getWorld().getUID(), loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        }
    }
    private static final class Delivery {
        final SupplyPlan.Drop plan;
        final SupplyPlan.Claim claim = new SupplyPlan.Claim();
        Location position;
        List<ItemStack> loot = List.of();
        boolean announced, published, attempted;

        Delivery(SupplyPlan.Drop plan) { this.plan = plan; }
    }
    private record Opening(Delivery delivery, Location origin, long deadline) { }

    private final JavaPlugin plugin;
    private final SupplySettings settings;
    private final Predicate<Player> active;
    private final Predicate<Location> safe;
    private final Consumer<String> notice;
    private final NamespacedKey tag;
    private final Map<Site, Delivery> spawned = new HashMap<>();
    private final Map<ChunkSite, Integer> tickets = new HashMap<>();
    private final Map<UUID, Opening> opening = new HashMap<>();
    private final List<Delivery> plans = new ArrayList<>();
    private List<Location> candidates = List.of();
    private Random random;
    private UUID match;
    private boolean paused;
    private boolean prepared;
    private long elapsed;

    PaperSupply(JavaPlugin plugin, SupplySettings settings, Predicate<Player> active, Predicate<Location> safe,
                Consumer<String> notice) {
        this.plugin = plugin;
        this.settings = settings;
        this.active = active;
        this.safe = safe;
        this.notice = notice;
        tag = new NamespacedKey(plugin, "supply_delivery");
    }

    List<Timeline.Event> start(UUID id, List<Location> positions) {
        match = id;
        random = new Random(id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 17));
        candidates = new ArrayList<>(positions);
        Collections.shuffle(candidates, random);
        var schedule = SupplyPlan.draw(settings.windows(), random);
        schedule.forEach(plan -> plans.add(new Delivery(plan)));
        return schedule.stream().map(plan -> new Timeline.Event(plan.id(), plan.second(), 1, false)).toList();
    }

    void tick(long gameNanos, boolean paused) {
        this.paused = paused;
        elapsed = gameNanos;
        if (match == null || paused) return;
        long second = gameNanos / 1_000_000_000L;
        if (!prepared) {
            prepared = true;
            for (var delivery : plans) delivery.position = reserve(delivery);
        }
        for (var delivery : plans) {
            if (delivery.attempted) continue;
            long opensAt = delivery.plan.second();
            if (!delivery.announced && second >= opensAt - settings.announceSeconds()) {
                delivery.announced = true;
                if (delivery.position == null) delivery.position = reserve(delivery);
                notice.accept(label(delivery) + " 예고 · 경기 시간 " + (opensAt / 60) + "분 " + (opensAt % 60) + "초");
            }
            if (!delivery.published && second >= opensAt - settings.coordinateSeconds()) {
                delivery.published = true;
                if (delivery.position != null && !validTerrain(delivery.position)) {
                    release(delivery.position);
                    delivery.position = null;
                }
                if (delivery.position == null) delivery.position = reserve(delivery);
                if (delivery.position == null) {
                    delivery.attempted = true;
                    notice.accept(label(delivery) + " · 안전한 위치를 확보하지 못해 취소됐습니다.");
                    continue;
                }
                var p = delivery.position;
                notice.accept(label(delivery) + " 위치 · " + p.getBlockX() + ", " + p.getBlockY() + ", " + p.getBlockZ());
            }
            if (second >= opensAt) spawn(delivery);
        }

        var pending = opening.entrySet().stream().sorted(Comparator
                .<Map.Entry<UUID, Opening>>comparingLong(entry -> entry.getValue().deadline()).thenComparing(Map.Entry::getKey)).toList();
        for (var entry : pending) {
            UUID id = entry.getKey();
            Opening work = entry.getValue();
            if (opening.get(id) != work) continue;
            Player player = plugin.getServer().getPlayer(id);
            if (!eligible(player, work.delivery()) || moved(work.origin(), player.getLocation())) {
                interrupt(id, "이동하거나 개봉 조건을 벗어났습니다.");
            } else if (work.deadline() <= gameNanos) {
                opening.remove(id);
                claim(player, work.delivery());
            }
        }
    }

    private Location reserve(Delivery subject) {
        for (Location location : candidates) {
            if (plans.stream().anyMatch(other -> other != subject && other.position != null
                    && horizontalSquared(other.position, location) < square(settings.separation()))) continue;
            if (!validTerrain(location)) continue;
            var chunk = ChunkSite.at(location);
            if (!tickets.containsKey(chunk) && !location.getWorld().addPluginChunkTicket(chunk.x(), chunk.z(), plugin)) continue;
            tickets.merge(chunk, 1, Integer::sum);
            return location.clone();
        }
        return null;
    }

    private boolean validTerrain(Location location) {
        if (location.getBlockY() < settings.minHeight() || location.getBlockY() > settings.maxHeight()
                || !safe.test(location) || !SafeArenaSurface.valid(location)) return false;
        return !nearContainer(location.getBlock());
    }

    private boolean nearContainer(Block block) {
        for (var face : List.of(org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
                org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST)) {
            Block adjacent = block.getRelative(face);
            if (adjacent.getType() == Material.CHEST || adjacent.getType() == Material.TRAPPED_CHEST) return true;
        }
        return false;
    }

    private void spawn(Delivery delivery) {
        delivery.attempted = true;
        Location location = delivery.position;
        if (location == null || !validTerrain(location) || !location.getBlock().isEmpty()) {
            release(delivery.position);
            notice.accept(label(delivery) + " · 공개 위치가 변경되어 취소됐습니다.");
            return;
        }
        var loot = delivery.plan.premium() ? settings.premium() : settings.normal();
        delivery.loot = loot.get(random.nextInt(loot.size())).stream().map(SupplySettings.Reward::create).toList();
        Block block = location.getBlock();
        block.setType(Material.CHEST, false);
        var chest = (Chest) block.getState();
        chest.getPersistentDataContainer().set(tag, PersistentDataType.STRING, marker(delivery));
        if (!chest.update(false, false)) {
            block.setType(Material.AIR, false);
            release(location);
            notice.accept(label(delivery) + " · 상자를 생성하지 못했습니다.");
            return;
        }
        spawned.put(Site.at(location), delivery);
        notice.accept(label(delivery) + "이 도착했습니다.");
        location.getWorld().playSound(location, Sound.BLOCK_CHEST_OPEN, 1, .8f);
        location.getWorld().spawnParticle(Particle.END_ROD, location.clone().add(0, 1, 0), 15, .4, .3, .4, .02);
    }

    private boolean eligible(Player player, Delivery delivery) {
        if (player == null || !player.isOnline() || player.isDead() || player.getGameMode() == GameMode.SPECTATOR
                || !active.test(player) || spawned.get(Site.at(delivery.position)) != delivery || !isMarked(delivery)) return false;
        Location target = delivery.position.clone().add(0, .5, 0);
        return player.getWorld().equals(target.getWorld()) && safe.test(target)
                && player.getEyeLocation().distanceSquared(target) <= square(settings.range())
                && canSeeChest(player, target);
    }

    private boolean canSeeChest(Player player, Location target) {
        Location eye = player.getEyeLocation();
        var direction = target.toVector().subtract(eye.toVector());
        if (direction.lengthSquared() == 0) return true;
        double length = direction.length();
        var hit = player.getWorld().rayTraceBlocks(eye, direction.normalize(), length, FluidCollisionMode.NEVER, true);
        return hit == null || hit.getHitBlock().equals(target.getBlock());
    }

    private void claim(Player player, Delivery delivery) {
        UUID id = player.getUniqueId();
        if (!delivery.claim.reserve(id)) return;
        boolean delivered = false;
        try {
            var inventory = player.getInventory();
            ItemStack[] result = SupplyInventory.fit(inventory.getStorageContents(), inventory.getMaxStackSize(), delivery.loot);
            if (result == null) {
                player.sendMessage("보급을 받을 인벤토리 공간이 필요합니다.");
                return;
            }
            inventory.setStorageContents(result);
            delivered = true;
        } finally {
            delivery.claim.finish(id, delivered);
        }
        remove(delivery);
        notice.accept(player.getName() + " 님이 " + label(delivery) + "을 획득했습니다.");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1.2f);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, delivery.position.clone().add(0, .8, 0), 12, .4, .3, .4, .02);
    }

    private void remove(Delivery delivery) {
        var location = delivery.position;
        spawned.remove(Site.at(location));
        if (isMarked(delivery)) {
            location.getBlock().setType(Material.AIR, false);
        }
        release(location);
        opening.values().removeIf(value -> value.delivery() == delivery);
        delivery.loot = List.of();
    }

    private boolean isMarked(Delivery delivery) {
        return delivery.position.getBlock().getState() instanceof Chest chest
                && marker(delivery).equals(chest.getPersistentDataContainer().get(tag, PersistentDataType.STRING));
    }

    private String marker(Delivery delivery) { return match + ":" + delivery.plan.id(); }

    private void release(Location location) {
        if (location == null) return;
        var key = ChunkSite.at(location);
        Integer refs = tickets.get(key);
        if (refs == null) return;
        if (refs > 1) tickets.put(key, refs - 1);
        else {
            tickets.remove(key);
            location.getWorld().removePluginChunkTicket(key.x(), key.z(), plugin);
        }
    }

    void clear() {
        for (var delivery : List.copyOf(spawned.values())) remove(delivery);
        for (var chunk : List.copyOf(tickets.keySet())) {
            var world = plugin.getServer().getWorld(chunk.world());
            if (world != null) world.removePluginChunkTicket(chunk.x(), chunk.z(), plugin);
        }
        tickets.clear();
        opening.clear();
        plans.clear();
        candidates = List.of();
        elapsed = 0;
        paused = false;
        prepared = false;
        match = null;
    }

    void disconnect(UUID id) { opening.remove(id); }

    private void interrupt(UUID id, String reason) {
        if (opening.remove(id) != null) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) player.sendMessage("보급 개봉 중단 · " + reason);
        }
    }

    private boolean moved(Location before, Location after) {
        return after == null || !before.getWorld().equals(after.getWorld())
                || before.distanceSquared(after) > square(settings.movementTolerance());
    }

    private boolean protectedAt(Location location) {
        return location != null && location.getWorld() != null && spawned.containsKey(Site.at(location));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent event) {
        var block = event.getClickedBlock();
        if (block == null || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        var delivery = spawned.get(Site.at(block.getLocation()));
        if (delivery == null) return;
        boolean denied = event.useInteractedBlock() == Event.Result.DENY;
        event.setCancelled(true);
        var player = event.getPlayer();
        if (denied || event.getHand() != EquipmentSlot.HAND || paused || !eligible(player, delivery)) return;
        var id = player.getUniqueId();
        if (opening.containsKey(id)) return;
        opening.put(id, new Opening(delivery, player.getLocation(), elapsed + settings.openingNanos()));
        player.sendActionBar(Component.text("보급 개봉 중 · 이동하거나 피격되면 중단됩니다."));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void move(PlayerMoveEvent event) {
        var work = opening.get(event.getPlayer().getUniqueId());
        if (work != null && moved(work.origin(), event.getTo())) interrupt(event.getPlayer().getUniqueId(), "이동했습니다.");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent event) {
        move(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void damage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && event.getDamage() > 0) interrupt(player.getUniqueId(), "피격됐습니다.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void open(InventoryOpenEvent event) {
        if (protectedAt(event.getInventory().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void transfer(InventoryMoveItemEvent event) {
        if (protectedAt(event.getSource().getLocation()) || protectedAt(event.getDestination().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void breakChest(BlockBreakEvent event) {
        if (protectedAt(event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void placeChest(BlockPlaceEvent event) {
        var block = event.getBlockPlaced();
        if (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST) return;
        for (var face : List.of(org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
                org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST)) {
            if (protectedAt(block.getRelative(face).getLocation())) event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void explode(BlockExplodeEvent event) { event.blockList().removeIf(block -> protectedAt(block.getLocation())); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void explode(EntityExplodeEvent event) { event.blockList().removeIf(block -> protectedAt(block.getLocation())); }

    private static String label(Delivery delivery) { return delivery.plan.premium() ? "고급 보급" : "일반 보급"; }
    private static double square(double value) { return value * value; }
    private static double horizontalSquared(Location first, Location second) {
        return square(first.getX() - second.getX()) + square(first.getZ() - second.getZ());
    }
}
