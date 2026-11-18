package io.github.augmentwild.paper;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import io.github.augmentwild.domain.Arena;
import io.github.augmentwild.domain.GameSession;
import io.github.augmentwild.domain.MatchSimulationClock;
import io.github.augmentwild.match.LifeState;
import io.github.augmentwild.match.MatchState;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Commands, player events, and the repeating task all run on the server thread. */
public final class PaperMatchService implements Listener {
    private final JavaPlugin plugin;
    private final PaperMatchSettings settings;
    private final Map<UUID, GameSession.DeathIntent> deaths = new LinkedHashMap<>();
    private final Set<UUID> awaitingNativeRespawn = new LinkedHashSet<>();
    private final Set<UUID> departures = new LinkedHashSet<>();
    private GameSession session;
    private MatchSimulationClock clock;
    private BukkitTask task;
    private BukkitTask preparation;
    private final ArenaSearchService search;
    private final PlayerSnapshots snapshots;
    private final UsedRegions usedRegions;
    private final ArenaPlacement placement;
    private final Set<UUID> captured = new LinkedHashSet<>();
    private final Map<UUID, TeleportPermit> permits = new LinkedHashMap<>();
    private ArenaSearchService.ArenaContext arena;
    private long tickNumber;
    private boolean regionCommitted;

    private record TeleportPermit(UUID matchId, Location target) { }
    private int loggedEvents;
    private final PaperRespawnRestrictions restrictions;

    public PaperMatchService(JavaPlugin plugin, PaperMatchSettings settings, ArenaSearchService search) {
        this.plugin = plugin;
        this.settings = settings;
        this.search = search;
        snapshots = new PlayerSnapshots(plugin);
        usedRegions = new UsedRegions(plugin);
        var config = plugin.getConfig();
        placement = new ArenaPlacement(config.getInt("respawn.border-margin"),
                config.getInt("respawn.attempts-per-cycle"), config.getInt("respawn.retry-ticks"),
                config.getInt("respawn.protection-seconds"), config.getInt("respawn.preferred-separation"));
        restrictions = new PaperRespawnRestrictions(this::isRestricted, this::outsideArena,
                this::consumePermit, player -> placement.protectedFromDamage(player.getUniqueId(), System.nanoTime()));
        session = new GameSession(settings.rules());
    }

    public Listener restrictions() { return restrictions; }

    public int previewPlayerCount() {
        return Math.max(settings.minimumPlayers(), session.participantIds().size() - departures.size());
    }

    public String status() {
        long elapsed = clock == null ? 0 : clock.elapsedSeconds();
        String remaining = switch (session.state()) {
            case GATHERING, AUGMENT_SELECTION -> Math.max(0, settings.rules().pvpStartSeconds() - elapsed) + "초 (목숨 제한 시작까지)";
            case PVP -> Math.max(0, settings.rules().pvpStartSeconds() + settings.rules().shrinkSeconds() - elapsed)
                    + "초 (서든데스까지)";
            default -> "해당 없음";
        };
        return "경기 단계 " + session.state() + " | 참가자 " + (session.participantIds().size() - departures.size())
                + "명 | 경과 " + elapsed + "초 | 남은 시간 " + remaining
                + " | 일시정지 " + (clock != null && clock.isPaused() ? "예" : "아니요");
    }

    public String join(Player player) {
        UUID id = player.getUniqueId();
        if (session.state() != MatchState.WAITING) return "경기가 이미 진행 중입니다.";
        if (contains(id)) return "이미 참가했습니다.";
        if (!snapshots.restore(player, this::move)) return "이전 경기 상태를 복구하지 못했습니다.";
        if (session.participantIds().size() >= settings.maximumPlayers()) return "참가 정원이 찼습니다.";
        session.join(id);
        return "대기 중인 경기에 참가했습니다.";
    }

    public String leave(Player player) {
        UUID id = player.getUniqueId();
        if (!contains(id) || departures.contains(id)) return "참가 중인 경기가 없습니다.";
        if (session.state() == MatchState.PREPARING) clearMatch();
        else if (task == null) session.leave(id);
        else departures.add(id);
        return "경기에서 나갔습니다.";
    }

    public String start(CommandSender sender) {
        if (session.state() != MatchState.WAITING) return "경기가 이미 준비 중이거나 진행 중입니다.";
        if (search.busy()) return "이전 경기장 탐색이 정리될 때까지 기다리세요.";
        var players = session.participantIds().stream().sorted().toList();
        if (players.size() < settings.minimumPlayers()) {
            return "참가자가 최소 " + settings.minimumPlayers() + "명 필요합니다.";
        }
        if (players.stream().anyMatch(id -> available(id) == null || snapshots.pending(id))) {
            return "모든 참가자가 접속하고 살아 있어야 하며 이전 복구 작업이 없어야 합니다.";
        }
        UUID matchId = session.matchId();
        session.transition(MatchState.PREPARING);
        String message = search.search(sender, players.size(), matchId, context -> {
            if (!matchId.equals(session.matchId()) || session.state() != MatchState.PREPARING) return;
            if (context == null) {
                clearMatch();
                return;
            }
            preparePlacement(sender, players, context);
        });
        if (!search.running()) clearMatch();
        return message;
    }

    private Player available(UUID id) {
        Player player = plugin.getServer().getPlayer(id);
        return player != null && player.isOnline() && !player.isDead() ? player : null;
    }

    private void preparePlacement(CommandSender sender, List<UUID> players, ArenaSearchService.ArenaContext context) {
        try {
            var world = plugin.getServer().getWorld(context.worldId());
            if (world == null || !world.getPVP()) throw new IllegalStateException("Arena world or PvP unavailable");
            // Capture every player before moving anyone.
            for (UUID id : players) {
                Player player = available(id);
                if (player == null) throw new IllegalStateException("Participant unavailable: " + id);
                snapshots.capture(player);
                captured.add(id);
            }
            var targets = context.spawns();
            final class Placing implements Runnable {
                private int next;
                private CompletableFuture<Chunk> pending;
                private final long started = System.nanoTime();
                @Override public void run() {
                    try {
                        if (!context.matchId().equals(session.matchId())) return;
                        if (System.nanoTime() - started >= settings.placementTimeoutNanos()) {
                            throw new IllegalStateException("Arena placement timed out");
                        }
                        Player player = available(players.get(next));
                        if (player == null) throw new IllegalStateException("Participant left during placement");
                        Location target = targets.get(next);
                        if (pending == null) {
                            pending = world.getChunkAtAsync(target.getBlockX() >> 4, target.getBlockZ() >> 4, false);
                            return;
                        }
                        if (!pending.isDone()) return;
                        if (pending.join() == null || !SafeArenaSurface.valid(target) || !move(player, target)) {
                            throw new IllegalStateException("Arena spawn unavailable or teleport rejected");
                        }
                        snapshots.enterMatch(player);
                        pending = null;
                        if (++next == players.size()) activate(sender, players, context);
                    } catch (Exception failure) {
                        failPreparation(sender, failure);
                    }
                }
            }
            preparation = plugin.getServer().getScheduler().runTaskTimer(plugin, new Placing(), 1L, 1L);
        } catch (Exception failure) {
            failPreparation(sender, failure);
        }
    }

    private void activate(CommandSender sender, List<UUID> players, ArenaSearchService.ArenaContext context)
            throws java.io.IOException, org.bukkit.configuration.InvalidConfigurationException {
        var world = plugin.getServer().getWorld(context.worldId());
        if (world == null || !world.getPVP() || players.stream().anyMatch(id -> !inside(context, available(id)))) {
            throw new IllegalStateException("Participant moved or arena settings changed before start");
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        usedRegions.start(context);
        regionCommitted = true;
        arena = context;
        preparation.cancel();
        preparation = null;
        session.transition(MatchState.GATHERING);
        // Start timing only after the placement and history write have succeeded.
        long started = System.nanoTime();
        clock = new MatchSimulationClock(session, settings.rules(), () -> System.nanoTime() - started,
                settings.selectionSeconds(), settings.normalSupplySecond(), settings.premiumSupplySecond());
        sender.sendMessage("경기가 시작됐습니다. 파밍 중 PvP 허용, 목숨 무제한.");
        for (UUID id : players) {
            Player player = available(id);
            if (player != null && player != sender) player.sendMessage("증강 야생 경기가 시작됐습니다.");
        }
    }

    private boolean inside(ArenaSearchService.ArenaContext context, Player player) {
        return player != null && context.worldId().equals(player.getWorld().getUID())
                && context.area().contains(player.getLocation().getX(), player.getLocation().getZ(), 0);
    }

    private void failPreparation(CommandSender sender, Exception failure) {
        plugin.getLogger().log(Level.WARNING, "Match preparation failed", failure);
        clearMatch();
        sender.sendMessage("경기 준비가 중단됐습니다. 자세한 이유는 서버 로그를 확인하세요.");
    }

    public String stop() {
        if (session.state() == MatchState.WAITING) return "진행 중인 경기가 없습니다.";
        clearMatch();
        return "경기를 종료했습니다.";
    }

    public void shutdown() {
        clearMatch();
    }

    private void clearMatch() {
        search.cancel();
        if (preparation != null) {
            preparation.cancel();
            preparation = null;
        }
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (session.state() != MatchState.WAITING) {
            var result = session.cancel();
            if (session.state() == MatchState.ENDING) session.transition(MatchState.CLEANUP);
            plugin.getLogger().info("Match " + session.matchId() + " ended with " + result.outcome()
                    + (result.winner() == null ? "" : " (winner " + result.winner() + ")"));
        }
        if (regionCommitted) {
            try {
                usedRegions.end(arena);
            } catch (Exception failure) {
                plugin.getLogger().log(Level.WARNING, "Could not record arena end time", failure);
            }
        }
        regionCommitted = false;
        arena = null;
        clock = null;
        for (UUID id : captured) {
            Player player = available(id);
            if (player != null) snapshots.restore(player, this::move);
        }
        captured.clear();
        permits.clear();
        placement.clear();
        tickNumber = 0;
        loggedEvents = 0;
        departures.clear();
        deaths.clear();
        awaitingNativeRespawn.clear();
        session = new GameSession(settings.rules());
    }

    private void tick() {
        try {
            clock.tick();
            var events = clock.events();
            while (loggedEvents < events.size()) {
                plugin.getLogger().info("Match " + session.matchId() + " timeline event " + events.get(loggedEvents++));
            }
            long now = clock.elapsedNanos();
            awaitingNativeRespawn.removeAll(departures);
            for (UUID id : departures) {
                session.leave(id);
                placement.forget(id);
                captured.remove(id);
                Player player = available(id);
                if (player != null) snapshots.restore(player, this::move);
            }
            deaths.values().forEach(session::applyDeath);
            departures.clear();
            deaths.clear();
            if (session.state() == MatchState.PVP || session.state() == MatchState.SUDDEN_DEATH) {
                session.resolveTick(Set.of(), now);
            } else if (session.participantIds().isEmpty()) {
                session.cancel();
            }
            if (session.state() == MatchState.ENDING || session.state() == MatchState.CLEANUP) {
                clearMatch();
                return;
            }
            var world = plugin.getServer().getWorld(arena.worldId());
            if (world == null) throw new IllegalStateException("Arena world unloaded");
            Arena safe = currentSafeArea();
            tickNumber++;
            for (UUID id : session.participantIds()) {
                Player player = available(id);
                if (player != null && !awaitingNativeRespawn.contains(id)) {
                    placement.respawn(player, world, safe, session, now, tickNumber, this::move);
                }
            }
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.SEVERE, "Match tick failed", failure);
            clearMatch();
        }
    }

    private Arena currentSafeArea() {
        if (session.state() != MatchState.PVP && session.state() != MatchState.SUDDEN_DEATH) return arena.area();
        var area = arena.area();
        var rules = settings.rules();
        return area.shrink(clock.elapsedSeconds(), rules.pvpStartSeconds(), rules.shrinkSeconds(),
                Math.min(area.width(), rules.borderFinal()), Math.min(area.height(), rules.borderFinal()));
    }

    private boolean outsideArena(Player player, Location destination) {
        return arena != null && contains(player.getUniqueId()) && (destination == null
                || destination.getWorld() == null || !arena.worldId().equals(destination.getWorld().getUID())
                || !arena.area().contains(destination.getX(), destination.getZ(), 0));
    }

    private boolean move(Player player, Location target) {
        UUID id = player.getUniqueId();
        var permit = new TeleportPermit(session.matchId(), target.clone());
        permits.put(id, permit);
        try {
            return player.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN)
                    && atTarget(player.getLocation(), target);
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "Could not teleport match player " + id, failure);
            return false;
        } finally {
            permits.remove(id, permit);
        }
    }

    private boolean atTarget(Location current, Location target) {
        return java.util.Objects.equals(current.getWorld(), target.getWorld())
                && current.distanceSquared(target) < 0.000001;
    }

    private boolean consumePermit(PlayerTeleportEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        var permit = permits.get(id);
        return permit != null && permit.matchId().equals(session.matchId())
                && event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN
                && permit.target().equals(event.getTo()) && permits.remove(id, permit);
    }

    public void recoverOnlinePlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) recover(player);
    }

    private void recover(Player player) {
        if (!contains(player.getUniqueId()) && !snapshots.restore(player, this::move)) {
            player.sendMessage("이전 경기 상태가 복구 대기 중입니다. 관리자에게 알려 주세요.");
        }
    }

    private boolean contains(UUID id) {
        return session.participantIds().contains(id);
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!contains(id)) recover(event.getPlayer());
        if (contains(id) && !departures.contains(id)) {
            if (arena != null && !event.getPlayer().isDead() && outsideArena(event.getPlayer(), event.getPlayer().getLocation())
                    && session.player(id).life().status() == LifeState.Status.ACTIVE && !awaitingNativeRespawn.contains(id)) {
                departures.add(id);
                return;
            }
            session.connected(id, true);
            if (!event.getPlayer().isDead()) awaitingNativeRespawn.remove(id);
        }
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!contains(id)) return;
        if (session.state() == MatchState.PREPARING) {
            clearMatch();
        } else if (task == null) session.leave(id);
        else {
            session.connected(id, false);
            placement.forgetAttempt(id);
        }
    }

    private boolean isRestricted(Player player) {
        UUID id = player.getUniqueId();
        if (snapshots.restoring(id)) return true;
        if (!contains(id)) return snapshots.pending(id);
        if (session.state() == MatchState.PREPARING) return captured.contains(id);
        return task != null && (deaths.containsKey(id) || departures.contains(id)
                || awaitingNativeRespawn.contains(id) || session.player(id).life().status() != LifeState.Status.ACTIVE);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void preserveOnDeath(PlayerDeathEvent event) {
        UUID id = event.getEntity().getUniqueId();
        if (task == null || !contains(id) || departures.contains(id)) return;
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        UUID id = event.getEntity().getUniqueId();
        if (task != null && contains(id) && !departures.contains(id)
                && session.player(id).life().status() == LifeState.Status.ACTIVE && !deaths.containsKey(id)) {
            deaths.put(id, session.deathIntent(id, clock.elapsedNanos()));
            awaitingNativeRespawn.add(id);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerPostRespawnEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (!contains(id)) {
            recover(player);
            return;
        }
        if (task == null || !awaitingNativeRespawn.remove(id) || departures.contains(id)) return;
        if (session.player(id).life().status() == LifeState.Status.ELIMINATED) {
            player.sendMessage("현재 경기에서 탈락했습니다.");
        } else {
            player.sendMessage("경기 리스폰이 완료될 때까지 이동과 상호작용이 제한됩니다.");
        }
    }
}
