package io.github.augmentwild.paper;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import io.github.augmentwild.domain.GameSession;
import io.github.augmentwild.domain.CombatLab;
import io.github.augmentwild.domain.HudMessages.Layer;
import net.kyori.adventure.text.Component;
import io.github.augmentwild.domain.MatchSimulationClock;
import io.github.augmentwild.domain.MatchBorder;
import io.github.augmentwild.domain.MatchTelemetry;
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
import org.bukkit.World;
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
    private MatchBorder border;
    private PaperMatchBorder borderDisplay;
    private boolean regionCommitted;
    private final CombatLab lab;
    private UUID labWorld;
    private long matchStarted;
    private long lastSurvivorSample;

    private record TeleportPermit(UUID matchId, Location target) { }
    private int loggedEvents;
    private final PaperRespawnRestrictions restrictions;
    private final PaperAugments augments;
    private final PaperIntel intel;
    private final PaperSupply supply;
    private final PaperHud hud = new PaperHud();
    private final MatchDisplays displays = new MatchDisplays(this::augmentActive);
    private final CombatFeedback feedback;
    private final Map<UUID,List<String>> results = new LinkedHashMap<>();
    private final Map<UUID,Long> outAt = new LinkedHashMap<>();
    private final Map<UUID,String> causes = new LinkedHashMap<>();
    private int entrants;
    private final MatchTelemetry telemetry = new MatchTelemetry();

    public PaperMatchService(JavaPlugin plugin, PaperMatchSettings settings, ArenaSearchService search) {
        this.plugin = plugin;
        this.settings = settings;
        this.search = search;
        snapshots = new PlayerSnapshots(plugin);
        usedRegions = new UsedRegions(plugin);
        var config = plugin.getConfig();
        lab = new CombatLab(config.getBoolean("dev.combat-lab.enabled"), config.getString("dev.combat-lab.world", ""),
                config.getString("arena.world", ""));
        placement = new ArenaPlacement(config.getInt("respawn.border-margin"),
                config.getInt("respawn.attempts-per-cycle"), config.getInt("respawn.retry-ticks"),
                config.getInt("respawn.protection-seconds"), config.getInt("respawn.preferred-separation"));
        session = new GameSession(settings.rules());
        restrictions = new PaperRespawnRestrictions(this::isRestricted, this::outsideArena,
                this::consumePermit, player -> placement.protectedFromDamage(player.getUniqueId(), System.nanoTime()),
                player -> session.state() == MatchState.AUGMENT_SELECTION && augmentMember(player.getUniqueId()),
                this::labParticipant);
        augments = new PaperAugments(plugin, () -> clock, this::augmentMember, this::augmentActive,
                player -> placement.protectedFromDamage(player.getUniqueId(), System.nanoTime()),
                () -> session.state(), this::insideAugmentArena, this::insideAugmentBorder, this::move, placement::protect, settings.rules().procDepth(), telemetry, hud);
        feedback = new CombatFeedback(plugin,this::augmentActive,this::insideAugmentArena,augments,hud);
        supply = new PaperSupply(plugin, SupplySettings.load(config, settings.rules().gatheringSeconds()),
                this::augmentActive, this::insideAugmentBorder, this::notice, telemetry);
        intel = new PaperIntel(plugin, () -> clock, () -> session.participantIds(), this::augmentActive,
                this::insideAugmentBorder, augments, telemetry, hud, supply::nearbyPvp);
    }

    private void notice(String message) {
        for (UUID id : session.participantIds()) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) player.sendMessage(message);
        }
    }

    public Listener feedbackListener() { return feedback; }
    public Listener uiListener() { return new PlayerUi(); }
    public String ui(Player p) { return PlayerUi.open(p); }
    public String guide(Player p) { return augmentActive(p) && PlayerUi.enabled(p,PlayerUi.Option.SUPPLY)
            ? supply.guide(p,true) : "보급 안내를 사용할 수 없습니다."; }
    public String result(Player p) {
        var lines = results.get(p.getUniqueId());
        if (lines == null || task != null || snapshots.pending(p.getUniqueId())) return "현재 확인할 경기 결과가 없습니다.";
        PlayerUi.result(p,lines);
        return "경기 결과";
    }

    public Listener supplyListener() { return supply; }
    public Listener intelListener() { return intel; }
    public String locate(Player player) { return intel.locate(player); }
    public String scout(Player player) { return intel.openScout(player); }
    public String deathReport(Player player) { return intel.openDeath(player); }

    public Listener restrictions() { return restrictions; }
    public Listener augmentListener() { return augments; }
    public Listener officialAugmentListener() { return augments.officialListener(); }

    private boolean insideAugmentArena(Location location) {
        return arena != null && arena.worldId().equals(location.getWorld().getUID())
                && arena.area().contains(location.getX(), location.getZ(), 0);
    }
    private boolean insideAugmentBorder(Location location) {
        return border != null && clock != null && insideAugmentArena(location)
                && border.area(clock.elapsedSeconds()).contains(location.getX(), location.getZ(), 0);
    }

    public String skill(Player player, String[] args) { return augments.skill(player, args); }
    public String castAugment(Player player, String id) { return augments.cast(player, id); }
    public String openAugments(Player player) { return augments.open(player); }
    public String openCodex(Player player) { return augments.openCodex(player); }

    private boolean augmentMember(UUID id) {
        return task != null && contains(id) && !departures.contains(id)
                && session.player(id).life().status() != LifeState.Status.ELIMINATED;
    }

    private boolean augmentActive(Player player) {
        return augmentMember(player.getUniqueId()) && !player.isDead() && !isRestricted(player)
                && arena != null && arena.worldId().equals(player.getWorld().getUID())
                && !outsideArena(player, player.getLocation());
    }

    public int previewPlayerCount() {
        return Math.max(settings.minimumPlayers(), session.participantIds().size() - departures.size());
    }

    public String status(CommandSender sender) {
        long elapsed = clock == null ? 0 : clock.elapsedSeconds();
        String remaining = switch (session.state()) {
            case GATHERING, AUGMENT_SELECTION -> Math.max(0, settings.rules().pvpStartSeconds() - elapsed) + "초 (목숨 제한 시작까지)";
            case PVP -> Math.max(0, settings.rules().pvpStartSeconds() + settings.rules().shrinkSeconds() - elapsed)
                    + "초 (서든데스까지)";
            default -> "해당 없음";
        };
        String status = "경기 단계 " + session.state() + " | 참가자 " + (session.participantIds().size() - departures.size())
                + "명 | 경과 " + elapsed + "초 | 남은 시간 " + remaining
                + " | 일시정지 " + (clock != null && clock.isPaused() ? "예" : "아니요");
        if (border == null) return status;
        var area = border.area(elapsed);
        String shrink = elapsed < settings.rules().pvpStartSeconds()
                ? "축소까지 " + (settings.rules().pvpStartSeconds() - elapsed) + "초"
                : "축소 " + border.progressPercent(elapsed) + "%";
        status += String.format(java.util.Locale.ROOT, " | 자기장 %.1f×%.1f | %s", area.width(), area.height(), shrink);
        if (sender instanceof Player player && contains(player.getUniqueId())) {
            var life = session.player(player.getUniqueId()).life();
            String lives = elapsed < settings.rules().pvpStartSeconds() ? "무제한"
                    : session.state() == MatchState.SUDDEN_DEATH ? "부활 불가" : Integer.toString(life.lives());
            status += " | 생존 상태 " + life.status() + " | 남은 목숨 " + lives;
        }
        return status;
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

    public void recoverSupply() { supply.recover(); }

    public String start(CommandSender sender) { return start(sender, false); }

    private boolean labParticipant(Player player) {
        return labWorld != null && contains(player.getUniqueId());
    }

    public String labCommand(CommandSender sender, String[] args) {
        boolean authorised = sender.hasPermission("augmentwild.admin") && sender.hasPermission("augmentwild.admin.test");
        if (!authorised) return "관리자 권한이 없습니다.";
        String action = args.length == 0 ? "status" : args[0].toLowerCase(java.util.Locale.ROOT);
        String result = labAction(sender, action, args);
        plugin.getLogger().info("Combat lab command actor=" + sender.getName() + " action="
                + (action.matches("[a-z]{1,12}") ? action : "invalid") + " match=" + session.matchId()
                + " result=" + result.replace('\n', ' '));
        return result;
    }

    private String labAction(CommandSender sender, String action, String[] args) {
        if (action.equals("status") && args.length <= 1) {
            return "전투 실험실 " + (lab.ready() ? "설정됨 (" + lab.world() + ")" : "사용 불가") + " · "
                    + (labWorld != null ? "테스트 경기 " + session.state() : "테스트 경기 없음");
        }
        if (!lab.ready()) return "전투 실험실을 활성화하고 운영 월드와 다른 기존 테스트 월드를 지정하세요.";
        if (action.equals("start") && args.length == 1) return start(sender, true);
        if (action.equals("presets") && args.length == 1) {
            return "실험 프리셋 " + io.github.augmentwild.augment.AugmentCombinations.PRESETS.keySet();
        }
        if (!lab.allows(true, labWorld != null && task != null, clock == null || clock.isPaused())) {
            return "증강 실험 명령은 진행 중이며 일시정지 상태가 아닌 테스트 경기에서만 사용할 수 있습니다.";
        }
        int length = switch (action) {
            case "list", "clear", "report", "reset" -> 2;
            case "grant", "remove", "health", "preset" -> 3;
            case "progress" -> 4;
            default -> 0;
        };
        if (length == 0 || args.length != length) return "사용법: /aw admin test <status|start|grant|remove|list|clear|progress|report|reset|health|presets|preset>";
        Player player = plugin.getServer().getPlayerExact(args[1]);
        if (player == null || !labParticipant(player) || !augmentActive(player)
                || !labWorld.equals(player.getWorld().getUID())) return "살아 있는 테스트 참가자를 지정하세요.";
        String id = args.length > 2 ? args[2].toLowerCase(java.util.Locale.ROOT) : "";
        return switch (action) {
            case "grant" -> augments.labGrant(player, id);
            case "preset" -> augments.labPreset(player, id);
            case "remove" -> augments.labRemove(player, id);
            case "list", "report" -> augments.labReport(player, action.equals("report"));
            case "clear", "reset" -> augments.labClear(player, action.equals("reset"));
            case "progress" -> {
                try {
                    int value = Integer.parseInt(args[3]);
                    yield value < 0 ? "진행도는 0 이상의 정수입니다." : augments.labProgress(player, id, value);
                } catch (NumberFormatException failure) {
                    yield "진행도는 0 이상의 정수입니다.";
                }
            }
            case "health" -> {
                double health;
                try { health = Double.parseDouble(args[2]); }
                catch (NumberFormatException failure) { yield "유효한 체력 포인트를 입력하세요."; }
                double max = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
                if (!Double.isFinite(health) || health <= 0 || health > max) yield "체력 포인트는 0 초과, 최대 체력 이하여야 합니다.";
                player.setHealth(health);
                yield "실험 체력 " + health + "/" + max;
            }
            default -> throw new IllegalStateException(action);
        };
    }

    private String start(CommandSender sender, boolean experiment) {
        if (session.state() != MatchState.WAITING) return "경기가 이미 준비 중이거나 진행 중입니다.";
        if (search.busy()) return "이전 경기장 탐색이 정리될 때까지 기다리세요.";
        if (!supply.recover()) return "미처리 보급 복구 기록이 있습니다. 서버 로그를 확인하세요.";
        var players = session.participantIds().stream().sorted().toList();
        if (players.size() < settings.minimumPlayers()) {
            return "참가자가 최소 " + settings.minimumPlayers() + "명 필요합니다.";
        }
        if (players.stream().anyMatch(id -> available(id) == null || snapshots.pending(id))) {
            return "모든 참가자가 접속하고 살아 있어야 하며 이전 복구 작업이 없어야 합니다.";
        }
        String worldName = plugin.getConfig().getString("arena.world", "");
        if (experiment) {
            World world = plugin.getServer().getWorld(lab.world());
            if (world == null || world.getEnvironment() != World.Environment.NORMAL
                    || world.equals(plugin.getServer().getWorld(worldName))) {
                return "운영 경기와 분리된 기존 테스트 오버월드를 로드하세요.";
            }
            if (players.stream().anyMatch(id -> !world.equals(available(id).getWorld()))
                    || world.getPlayers().stream().anyMatch(player -> !players.contains(player.getUniqueId()))) {
                return "모든 테스트 참가자가 테스트 월드에 있어야 하며 비참가자가 없어야 합니다.";
            }
            worldName = lab.world();
            labWorld = world.getUID();
        }
        UUID matchId = session.matchId();
        session.transition(MatchState.PREPARING);
        String message = search.search(sender, players.size(), matchId, worldName, context -> {
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
                if (player == null || labWorld != null && !labWorld.equals(player.getWorld().getUID())) {
                    throw new IllegalStateException("Participant unavailable at arena preparation: " + id);
                }
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
        if (world == null || !world.getPVP() || players.stream().anyMatch(id -> !inside(context, available(id)))
                || labWorld != null && world.getPlayers().stream().anyMatch(player -> !players.contains(player.getUniqueId()))) {
            throw new IllegalStateException("Participant moved or arena settings changed before start");
        }
        border = new MatchBorder(context.area(), settings.rules(), settings.borderDamageInterval());
        borderDisplay = new PaperMatchBorder(plugin.getServer(), border.area(0));
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        if (labWorld == null) {
            usedRegions.start(context);
            regionCommitted = true;
        }
        arena = context;
        preparation.cancel();
        preparation = null;
        session.transition(MatchState.GATHERING);
        // Start timing only after the placement and history write have succeeded.
        long started = System.nanoTime();
        matchStarted = started;
        if (labWorld != null) {
            notice("관리자 전투 실험 경기입니다. 결과는 실험실에서만 사용됩니다.");
            plugin.getLogger().info("Combat lab started match=" + session.matchId());
        }
        lastSurvivorSample = Long.MIN_VALUE;
        clock = new MatchSimulationClock(session, settings.rules(), () -> System.nanoTime() - started,
                settings.selectionSeconds(), supply.start(session.matchId(), context.spawns()));
        augments.start(session.matchId(), clock, labWorld != null);
        entrants = players.size();
        results.clear();
        telemetry.start(players);
        intel.start(session.matchId());
        sender.sendMessage("경기가 시작됐습니다. 파밍 중 PvP 허용, 목숨 무제한.");
        for (UUID id : players) {
            Player player = available(id);
            if (player != null) {
                borderDisplay.attach(player);
                if (player != sender) player.sendMessage("증강 야생 경기가 시작됐습니다.");
            }
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
        intel.shutdown();
    }

    private void clearMatch() {
        boolean played = clock != null;
        if (played) feedback.tick(System.nanoTime());
        displays.clear();
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
            if (played) {
                for (UUID id : session.participantIds()) {
                    var lines = new java.util.ArrayList<String>();
                    lines.add(result.winner() == null ? (result.outcome() == GameSession.Outcome.DRAW ? "무승부" : "경기 중단")
                            : "우승 · " + plugin.getServer().getOfflinePlayer(result.winner()).getName());
                    if (result.outcome() != GameSession.Outcome.CANCELLED) {
                        long rank = outAt.containsKey(id) ? 1 + session.participantIds().stream()
                                .filter(other -> outAt.getOrDefault(other,Long.MAX_VALUE) > outAt.get(id)).count() : 1;
                        lines.add("순위 " + rank);
                    }
                    lines.add("처치 " + telemetry.kills(id) + " · 사망 " + telemetry.deaths(id));
                    lines.add(feedback.totals(id));
                    lines.add("생존 시간 " + io.github.augmentwild.domain.CombatView.time(outAt.getOrDefault(id,clock.elapsedNanos()) / 1_000_000_000L));
                    lines.add("스킬 사용 " + augments.skillUses(id));
                    lines.add("보유 증강 · " + String.join(", ",augments.ownedNames(id)));
                    lines.addAll(telemetry.attained(id));
                    results.put(id,List.copyOf(lines));
                }
            }
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
        if (borderDisplay != null) borderDisplay.clear();
        borderDisplay = null;
        border = null;
        supply.clear();
        String mode = labWorld == null ? "normal" : "lab";
        if (clock != null) plugin.getLogger().info("Match tempo match=" + session.matchId() + " mode=" + mode + " "
                + telemetry.tempo(clock.elapsedSeconds(), (System.nanoTime() - matchStarted) / 1_000_000_000L));
        var stats = telemetry.finish();
        if (!stats.isEmpty()) plugin.getLogger().info("Match feature totals match=" + session.matchId()
                + " mode=" + mode + " " + new java.util.TreeMap<>(stats));
        feedback.clear();
        outAt.clear();
        causes.clear();
        intel.clear();
        augments.clear();
        hud.clear(plugin);
        regionCommitted = false;
        labWorld = null;
        arena = null;
        clock = null;
        for (UUID id : captured) {
            Player player = available(id);
            if (player != null && snapshots.restore(player, this::move) && played && plugin.isEnabled() && results.containsKey(id)) PlayerUi.result(player,results.get(id));
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
                displays.detach(id);
                supply.disconnect(id);
                intel.remove(id);
                hud.clear(id, plugin.getServer().getPlayer(id));
                augments.remove(id);
                session.leave(id);
                placement.forget(id);
                captured.remove(id);
                Player player = available(id);
                Player online = plugin.getServer().getPlayer(id);
                if (online != null) borderDisplay.detach(online);
                if (player != null) snapshots.restore(player, this::move);
            }
            for (var entry : deaths.entrySet()) {
                UUID id = entry.getKey();
                if (!contains(id) || !session.applyDeath(entry.getValue())) continue;
                boolean eliminated = session.player(id).life().status() == LifeState.Status.ELIMINATED;
                if (eliminated) outAt.putIfAbsent(id,clock.elapsedNanos());
                String feed = causes.get(id) + (eliminated ? " · 최종 탈락" : " · 사망");
                for (UUID viewer : session.participantIds()) {
                    Player p = plugin.getServer().getPlayer(viewer);
                    if (p != null && PlayerUi.enabled(p,PlayerUi.Option.FEED)) p.sendMessage(feed);
                }
            }
            causes.clear();
            departures.clear();
            deaths.clear();
            augments.collectCompleted();
            long elapsed = clock.elapsedSeconds();
            long shrinkStart = settings.rules().pvpStartSeconds();
            long intoShrink = Math.min(elapsed - shrinkStart, settings.rules().shrinkSeconds());
            if (intoShrink >= 0) {
                long interval = intoShrink == settings.rules().shrinkSeconds() ? intoShrink : intoShrink / 60 * 60;
                if (interval > lastSurvivorSample) {
                    lastSurvivorSample = interval;
                    int alive = 0;
                    for (UUID id : session.participantIds()) {
                        if (session.player(id).life().status() != LifeState.Status.ELIMINATED) alive++;
                    }
                    telemetry.survivors(elapsed, alive);
                }
            }
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
            var activeSession = session;
            var frame = border.update(now, clock.isPaused());
            borderDisplay.update(frame.area());
            tickNumber++;
            supply.tick(now, clock.isPaused());
            for (UUID id : activeSession.participantIds()) {
                if (session != activeSession || task == null) return;
                Player online = plugin.getServer().getPlayer(id);
                if (online != null) augments.sync(online);
                Player player = available(id);
                if (player != null && !awaitingNativeRespawn.contains(id)) {
                    if (!clock.isPaused()) placement.respawn(player, world, frame.area(), activeSession, now, tickNumber, this::move);
                    if (session != activeSession || task == null) return;
                    for (var notice : frame.notices()) player.sendMessage(notice.message());
                    if (frame.damagePulse() && settings.borderDamagePoints() > 0) {
                        var location = player.getLocation();
                        if (frame.damages(contains(id) && !isRestricted(player), world.equals(location.getWorld()),
                                placement.protectedFromDamage(id, System.nanoTime()), location.getX(), location.getZ())) {
                            PaperMatchBorder.applyDamage(player, settings.borderDamagePoints());
                        }
                    }
                }
            }
            intel.tick();
            long realTime = System.nanoTime();
            feedback.tick(realTime);
            var round = clock.selection().orElse(null);
            for (UUID id : activeSession.participantIds()) {
                Player player = plugin.getServer().getPlayer(id);
                if (player != null) {
                    displays.aim(player,tickNumber,realTime);
                    if (tickNumber % 20 == 1) displays.sidebar(player,sidebar(player));
                }
                if (player == null || !augmentActive(player)) {
                    hud.clear(id, player);
                    continue;
                }
                var choice = round == null ? null : round.choices().get(id);
                if (choice != null && choice.status() == io.github.augmentwild.domain.SelectionRound.Status.PENDING) {
                    hud.set(id, Layer.SELECTION, Component.text("증강 선택 · 남은 시간 " + clock.selectionRemainingSeconds() + "초"));
                } else if (clock.isPaused()) hud.set(id, Layer.SELECTION, Component.text("경기 시계 일시정지"));
                else hud.remove(id, Layer.SELECTION);
                if (supply.isOpening(id)) hud.set(id, Layer.SUPPLY, Component.text("보급 개봉 중 · 이동하거나 피격되면 중단됩니다."));
                else hud.remove(id, Layer.SUPPLY);
                if (clock.isPaused()) hud.remove(id, Layer.PROC);
                hud.render(player, realTime);
            }
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.SEVERE, "Match tick failed", failure);
            clearMatch();
        }
    }

    private List<String> sidebar(Player player) {
        long elapsed = clock.elapsedSeconds(), start = settings.rules().pvpStartSeconds(), end = start + settings.rules().shrinkSeconds();
        long next = io.github.augmentwild.domain.CombatView.nextPick(elapsed,settings.rules().selectionInterval(),settings.rules().gatheringSeconds());
        UUID id = player.getUniqueId();
        return List.of(switch (session.state()) {
            case AUGMENT_SELECTION -> "증강 선택 중"; case GATHERING -> "파밍 단계"; case PVP -> "자기장 축소"; case SUDDEN_DEATH -> "서든데스";
            default -> session.state().name(); }, "경기 시간 " + io.github.augmentwild.domain.CombatView.time(elapsed),
            "생존 " + session.participantIds().stream().filter(p -> session.player(p).life().status()!=LifeState.Status.ELIMINATED).count() + "/" + entrants,
            "처치 " + telemetry.kills(id) + " · 사망 " + telemetry.deaths(id),
            clock.selection().isPresent() ? "선택 진행 중" : next < 0 ? "증강 선택 종료" : "다음 증강 " + io.github.augmentwild.domain.CombatView.time(next),
            supply.guide(player,false), elapsed < start ? "축소 시작 " + io.github.augmentwild.domain.CombatView.time(start-elapsed)
                    : elapsed < end ? "축소 종료 " + io.github.augmentwild.domain.CombatView.time(end-elapsed) : "축소 완료",
            "안전 구역 폭 " + Math.round(border.area(elapsed).width()), "남은 목숨 " + (elapsed < start ? "무제한" : session.state() == MatchState.SUDDEN_DEATH ? "부활 불가" : session.player(id).life().lives()));
    }

    private boolean outsideArena(Player player, Location destination) {
        if (labWorld != null && !contains(player.getUniqueId()) && destination != null && destination.getWorld() != null
                && labWorld.equals(destination.getWorld().getUID())) return true;
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

    @EventHandler public void tickEnded(ServerTickEndEvent event) {
        telemetry.tick(event.getTickDuration());
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!contains(id)) {
            recover(event.getPlayer());
            if (labWorld != null && labWorld.equals(event.getPlayer().getWorld().getUID())) {
                plugin.getLogger().warning("Closing combat lab after a nonparticipant joined its world");
                clearMatch();
            }
        }
        if (contains(id) && !departures.contains(id)) {
            if (arena != null && !event.getPlayer().isDead() && outsideArena(event.getPlayer(), event.getPlayer().getLocation())
                    && session.player(id).life().status() == LifeState.Status.ACTIVE && !awaitingNativeRespawn.contains(id)) {
                departures.add(id);
                return;
            }
            session.connected(id, true);
            if (borderDisplay != null) borderDisplay.attach(event.getPlayer());
            if (!event.getPlayer().isDead()) awaitingNativeRespawn.remove(id);
        }
    }

    @EventHandler public void worldChange(org.bukkit.event.player.PlayerChangedWorldEvent e) { displays.detach(e.getPlayer().getUniqueId()); }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        displays.detach(id);
        if (!contains(id)) return;
        supply.disconnect(id);
        intel.remove(id);
        hud.clear(id, event.getPlayer());
        if (borderDisplay != null) borderDisplay.detach(event.getPlayer());
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
            Player killer = event.getEntity().getKiller();
            telemetry.death(id, killer != null && contains(killer.getUniqueId()) && !departures.contains(killer.getUniqueId())
                    ? killer.getUniqueId() : null);
            displays.detach(id);
            var source = event.getDamageSource().getCausingEntity();
            String cause = source instanceof Player attacker && contains(attacker.getUniqueId())
                    ? attacker.getName() + " ⚔ " : "환경 피해 · ";
            var last = event.getEntity().getLastDamageCause();
            var trace = last == null ? null : augments.trace(last);
            causes.put(id,cause + event.getEntity().getName() + (trace != null && trace.synthetic() && trace.augment()!=null
                    ? " · " + augments.effectName(trace.augment()) : ""));
            feedback.settle(id);
            awaitingNativeRespawn.add(id);
            supply.disconnect(id);
            intel.death(event.getEntity(), event.getDamageSource());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerPostRespawnEvent event) {
        Player player = event.getPlayer();
        intel.onRespawn(player);
        UUID id = player.getUniqueId();
        if (!contains(id)) {
            recover(player);
            return;
        }
        if (task == null || !awaitingNativeRespawn.remove(id) || departures.contains(id)) return;
        borderDisplay.refresh(player);
        if (session.player(id).life().status() == LifeState.Status.ELIMINATED) {
            player.sendMessage("현재 경기에서 탈락했습니다.");
        } else {
            player.sendMessage("경기 리스폰이 완료될 때까지 이동과 상호작용이 제한됩니다.");
        }
    }
}
