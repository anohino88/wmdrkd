package io.github.augmentwild.paper;

import io.github.augmentwild.domain.GameSession;
import io.github.augmentwild.domain.MatchSimulationClock;
import io.github.augmentwild.match.LifeState;
import io.github.augmentwild.match.MatchState;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Commands, player events, and the repeating task all run on the server thread. */
public final class PaperMatchService implements Listener {
    private final JavaPlugin plugin;
    private final PaperMatchSettings settings;
    private final Set<UUID> deaths = new LinkedHashSet<>();
    private final Set<UUID> awaitingNativeRespawn = new LinkedHashSet<>();
    private final Set<UUID> departures = new LinkedHashSet<>();
    private GameSession session;
    private MatchSimulationClock clock;
    private BukkitTask task;
    private int loggedEvents;
    private final PaperRespawnRestrictions restrictions;

    public PaperMatchService(JavaPlugin plugin, PaperMatchSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        restrictions = new PaperRespawnRestrictions(this::isRestricted);
        session = new GameSession(settings.rules());
    }

    public Listener restrictions() { return restrictions; }

    public String status() {
        long elapsed = clock == null ? 0 : clock.elapsedSeconds();
        String remaining = switch (session.state()) {
            case GATHERING, AUGMENT_SELECTION -> Math.max(0, settings.rules().pvpStartSeconds() - elapsed) + "초 (PvP까지)";
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
        if (session.participantIds().size() >= settings.maximumPlayers()) return "참가 정원이 찼습니다.";
        session.join(id);
        return "대기 중인 경기에 참가했습니다.";
    }

    public String leave(Player player) {
        UUID id = player.getUniqueId();
        if (!contains(id) || departures.contains(id)) return "참가 중인 경기가 없습니다.";
        if (task == null) session.leave(id);
        else departures.add(id);
        return "경기에서 나갔습니다.";
    }

    public String start() {
        if (task != null || session.state() != MatchState.WAITING) return "경기가 이미 진행 중입니다.";
        if (session.participantIds().size() < settings.minimumPlayers()) {
            return "참가자가 최소 " + settings.minimumPlayers() + "명 필요합니다.";
        }
        long origin = System.nanoTime();
        clock = new MatchSimulationClock(session, settings.rules(), () -> System.nanoTime() - origin,
                settings.selectionSeconds(), settings.normalSupplySecond(), settings.premiumSupplySecond());
        try {
            session.transition(MatchState.PREPARING);
            session.transition(MatchState.GATHERING);
            // With no offer provider the existing timeline uses its configured timeout.
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.SEVERE, "Could not start match scheduler", failure);
            clearMatch();
            return "경기 시작에 실패했습니다.";
        }
        plugin.getLogger().info("Match " + session.matchId() + " started in GATHERING");
        return "개발용 도메인 경기를 시작했습니다. 경기장 배치 없이 현재 월드에서 GATHERING을 진행합니다.";
    }

    public String stop() {
        if (task == null) return "진행 중인 경기가 없습니다.";
        clearMatch();
        return "경기를 종료했습니다.";
    }

    public void shutdown() {
        clearMatch();
    }

    private void clearMatch() {
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
        clock = null;
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
            // The native respawn and the match deadline are separate gates.
            var ready = session.respawnDue(now, id -> !awaitingNativeRespawn.contains(id) && !departures.contains(id));
            for (UUID id : ready) {
                Player player = plugin.getServer().getPlayer(id);
                if (player != null) player.sendMessage("경기 리스폰 대기가 끝났습니다.");
            }
            awaitingNativeRespawn.removeAll(departures);
            if (session.state() == MatchState.PVP || session.state() == MatchState.SUDDEN_DEATH) {
                session.resolveTick(departures, deaths, now);
            } else {
                departures.forEach(session::leave);
                deaths.stream().filter(this::contains).forEach(id -> session.death(id, now));
                if (session.participantIds().isEmpty()) session.cancel();
            }
            departures.clear();
            deaths.clear();
            if (session.state() == MatchState.ENDING || session.state() == MatchState.CLEANUP) clearMatch();
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.SEVERE, "Match tick failed", failure);
            clearMatch();
        }
    }

    private boolean contains(UUID id) {
        return session.participantIds().contains(id);
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (contains(id) && !departures.contains(id)) {
            session.connected(id, true);
            if (!event.getPlayer().isDead()) awaitingNativeRespawn.remove(id);
        }
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!contains(id)) return;
        if (task == null) session.leave(id);
        else session.connected(id, false);
    }

    private boolean isRestricted(Player player) {
        UUID id = player.getUniqueId();
        return task != null && contains(id) && (deaths.contains(id) || departures.contains(id)
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
                && session.player(id).life().status() == LifeState.Status.ACTIVE && deaths.add(id)) {
            awaitingNativeRespawn.add(id);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (task == null || !awaitingNativeRespawn.remove(id) || departures.contains(id)) return;
        PaperRespawnRestrictions.safeTestSpawn(plugin.getConfig(), event.getRespawnLocation())
                .ifPresent(event::setRespawnLocation);
        if (session.player(id).life().status() == LifeState.Status.ELIMINATED) {
            player.sendMessage("현재 경기에서 탈락했습니다.");
        } else {
            player.sendMessage("경기 리스폰이 완료될 때까지 이동과 상호작용이 제한됩니다.");
        }
    }
}
