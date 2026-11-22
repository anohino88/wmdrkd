package io.github.augmentwild.paper;

import io.github.augmentwild.domain.SupplyPlan;
import io.github.augmentwild.domain.MatchTelemetry;
import io.github.augmentwild.domain.MatchTelemetry.Counter;
import io.github.augmentwild.domain.Timeline;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.bukkit.*;
import org.bukkit.block.Block;
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
        SupplyBlock block;
        long arrived;
        boolean claimed;
        boolean announced, published, attempted;

        Delivery(SupplyPlan.Drop plan) { this.plan = plan; }
    }
    private record Opening(Delivery delivery, Location origin, long deadline) { }

    private final JavaPlugin plugin;
    private final SupplySettings settings;
    private final Predicate<Player> active;
    private final Predicate<Location> safe;
    private final Consumer<String> notice;
    private final SupplyRecovery recovery;
    private final MatchTelemetry stats;
    private final PaperArt art;
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
    private long lastMarkerCheck = -1;

    PaperSupply(JavaPlugin plugin, SupplySettings settings, Predicate<Player> active, Predicate<Location> safe,
                Consumer<String> notice, MatchTelemetry stats, PaperArt art) {
        this.plugin = plugin;
        this.settings = settings;
        this.active = active;
        this.safe = safe;
        this.notice = notice;
        this.stats = stats;
        this.art = art;
        recovery = new SupplyRecovery(plugin);
    }

    boolean recover() { return recovery.recover(); }

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
            if (!delivery.announced && second >= delivery.plan.announceAt(settings.announceSeconds())) {
                delivery.announced = true;
                validateReservation(delivery);
                notice.accept(label(delivery) + " 예고 · 경기 시간 " + (opensAt / 60) + "분 " + (opensAt % 60) + "초");
            }
            if (!delivery.published && second >= delivery.plan.publishAt(settings.coordinateSeconds())) {
                delivery.published = true;
                validateReservation(delivery);
                if (delivery.position == null) {
                    delivery.attempted = true;
                    stats.count(Counter.NO_POSITION);
                    notice.accept(label(delivery) + " · 안전한 위치를 확보하지 못해 취소됐습니다.");
                    continue;
                }
                var p = delivery.position;
                notice.accept(label(delivery) + " 위치 · " + p.getBlockX() + ", " + p.getBlockY() + ", " + p.getBlockZ());
            }
            if (second >= opensAt) spawn(delivery);
        }

        if (second != lastMarkerCheck) {
            lastMarkerCheck = second;
            for (var delivery : List.copyOf(spawned.values())) {
                if (claimable(delivery)) {
                    art.supplyMarker(delivery.position,delivery.plan.premium());
                    continue;
                }
                stats.count(Counter.CANCELLED_AFTER_PUBLICATION);
                remove(delivery);
                notice.accept(label(delivery) + " · 상자 소유권 또는 내용물이 달라 보급을 중단했습니다.");
            }
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

    private void validateReservation(Delivery delivery) {
        if (delivery.position != null && !validTerrain(delivery.position)) {
            release(delivery.position);
            delivery.position = null;
        }
        if (delivery.position == null) delivery.position = reserve(delivery);
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
                || !safe.test(location) || plugin.getServer().getWorld(location.getWorld().getUID()) != location.getWorld()
                || !SafeArenaSurface.valid(location)) return false;
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
            stats.count(Counter.CANCELLED_AFTER_PUBLICATION);
            String reason = location == null ? "예약 없음"
                    : !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4) ? "청크 미로드"
                    : !safe.test(location) ? "경기장 또는 자기장 경계"
                    : !location.getBlock().isEmpty() ? "상자 자리 점유" : "지표면 또는 개봉 공간 변경";
            notice.accept(label(delivery) + " · " + reason + "으로 취소됐습니다.");
            return;
        }
        var loot = delivery.plan.premium() ? settings.premium() : settings.normal();
        delivery.loot = loot.get(random.nextInt(loot.size())).stream().map(SupplySettings.Reward::create).toList();
        try {
            var entry = recovery.plan(match, location.getBlock());
            delivery.block = recovery.place(entry, location.getBlock());
        } catch (java.io.IOException | RuntimeException failure) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Supply journal or placement failed", failure);
        }
        if (delivery.block == null) {
            release(location);
            delivery.loot = List.of();
            stats.count(Counter.CANCELLED_AFTER_PUBLICATION);
            notice.accept(label(delivery) + " · 상자 또는 복구 기록을 생성하지 못해 취소됐습니다.");
            return;
        }
        spawned.put(Site.at(location), delivery);
        delivery.arrived = elapsed;
        stats.count(Counter.CREATED);
        notice.accept(label(delivery) + "이 도착했습니다.");
        art.supply(location,delivery.plan.premium());
        PlayerUi.audience(location,PlayerUi.Option.PARTICLES,p -> p.spawnParticle(Particle.END_ROD, location.clone().add(0,1,0),15,.4,.3,.4,.02));
    }

    private boolean claimable(Delivery delivery) {
        var world = delivery.position.getWorld();
        return plugin.getServer().getWorld(world.getUID()) == world
                && world.isChunkLoaded(delivery.position.getBlockX() >> 4, delivery.position.getBlockZ() >> 4)
                && delivery.block.claimable();
    }

    private boolean eligible(Player player, Delivery delivery) {
        if (player == null || !player.isOnline() || player.isDead() || player.getGameMode() == GameMode.SPECTATOR
                || !active.test(player) || spawned.get(Site.at(delivery.position)) != delivery || !claimable(delivery)) return false;
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
        var result = MatchInventory.Write.ROLLED_BACK;
        try {
            var inventory = player.getInventory();
            ItemStack[] before = inventory.getStorageContents();
            ItemStack[] after = MatchInventory.fit(before, inventory.getMaxStackSize(), delivery.loot);
            if (after == null) {
                stats.count(Counter.PAYOUT_FAILURES);
                player.sendMessage("보급을 받을 인벤토리 공간이 필요합니다.");
                return;
            }
            result = MatchInventory.apply(before, after, inventory::setStorageContents,
                    failure -> plugin.getLogger().log(java.util.logging.Level.WARNING, "Supply reward write failed", failure));
        } finally {
            delivery.claim.finish(id, result != MatchInventory.Write.ROLLED_BACK);
        }
        if (result != MatchInventory.Write.DELIVERED) {
            stats.count(Counter.PAYOUT_FAILURES);
            if (result == MatchInventory.Write.UNCERTAIN) {
                remove(delivery);
                notice.accept(label(delivery) + " 지급 상태를 확인할 수 없어 회수했습니다. 관리자에게 알려 주세요.");
            } else player.sendMessage("보급 지급이 실패해 다시 시도할 수 있습니다.");
            return;
        }
        delivery.claimed = true;
        stats.supplyClaim(delivery.plan.id(), (elapsed - delivery.arrived) / 1_000_000L);
        remove(delivery);
        notice.accept(player.getName() + " 님이 " + label(delivery) + "을 획득했습니다.");
        if (PlayerUi.enabled(player,PlayerUi.Option.SOUND)) player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1.2f);
        PlayerUi.audience(delivery.position,PlayerUi.Option.PARTICLES,p -> p.spawnParticle(Particle.HAPPY_VILLAGER,delivery.position.clone().add(0,.8,0),12,.4,.3,.4,.02));
    }

    private void remove(Delivery delivery) {
        var location = delivery.position;
        spawned.remove(Site.at(location));
        art.removeSupply(location);
        if (plugin.getServer().getWorld(location.getWorld().getUID()) == location.getWorld()) delivery.block.remove();
        release(location);
        opening.values().removeIf(value -> value.delivery() == delivery);
        delivery.loot = List.of();
    }

    private void release(Location location) {
        if (location == null) return;
        var key = ChunkSite.at(location);
        Integer refs = tickets.get(key);
        if (refs == null) return;
        if (refs > 1) tickets.put(key, refs - 1);
        else {
            tickets.remove(key);
            var world = plugin.getServer().getWorld(key.world());
            if (world != null) world.removePluginChunkTicket(key.x(), key.z(), plugin);
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
        lastMarkerCheck = -1;
        paused = false;
        prepared = false;
        if (match != null) recovery.recover();
        match = null;
    }

    boolean isOpening(UUID id) { return opening.containsKey(id); }

    void nearbyPvp(Location victim) {
        for (var delivery : spawned.values()) {
            if (victim.getWorld().equals(delivery.position.getWorld())
                    && victim.distanceSquared(delivery.position) <= square(settings.pvpRadius())) {
                stats.count(Counter.SUPPLY_PVP);
                return;
            }
        }
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
        stats.count(Counter.OPEN_ATTEMPTS);
        opening.put(id, new Opening(delivery, player.getLocation(), elapsed + settings.openingNanos()));
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
        if (event.getEntity() instanceof Player player && event.getDamage() > 0 && isOpening(player.getUniqueId())) {
            stats.count(Counter.OPEN_HIT_CANCELS);
            interrupt(player.getUniqueId(), "피격됐습니다.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void open(InventoryOpenEvent event) {
        if (protectedAt(event.getInventory().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void otherGui(InventoryOpenEvent event) {
        if (!paused && event.getPlayer() instanceof Player player) interrupt(player.getUniqueId(), "다른 화면을 열었습니다.");
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

    String guide(Player player, boolean details) {
        var lines = new java.util.ArrayList<String>();
        for (var delivery : plans) {
            String name = label(delivery);
            String state = io.github.augmentwild.domain.CombatView.supplyStatus(delivery.published,delivery.claimed,
                    delivery.attempted,spawned.containsValue(delivery));
            if (!delivery.published) { lines.add(name + " · " + state); break; }
            if (delivery.claimed || delivery.attempted && !spawned.containsValue(delivery)) {
                if (details) lines.add(name + " · " + state);
                continue;
            }
            Location point = delivery.position;
            String status = delivery.attempted ? "개봉 가능" : "등장까지 " + Math.max(0,delivery.plan.second()-elapsed/1_000_000_000L) + "초";
            if (details && point != null && player.getWorld().equals(point.getWorld())) {
                Location from = player.getLocation();
                status += String.format(java.util.Locale.ROOT," · %d %d %d · %s · %.0fm",point.getBlockX(),point.getBlockY(),
                        point.getBlockZ(),io.github.augmentwild.domain.Tracker.direction(point.getX()-from.getX(),point.getZ()-from.getZ()),point.distance(from));
            }
            lines.add(name + " · " + status);
            if (!details) break;
        }
        return lines.isEmpty() ? "보급 일정 종료" : String.join(" / ",lines);
    }

    private static String label(Delivery delivery) { return delivery.plan.premium() ? "고급 보급" : "일반 보급"; }
    private static double square(double value) { return value * value; }
    private static double horizontalSquared(Location first, Location second) {
        return square(first.getX() - second.getX()) + square(first.getZ() - second.getZ());
    }
}
