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
import io.github.augmentwild.domain.StartRoster;
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
import org.bukkit.GameMode;
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
    private long labAdvanceNanos;
    private BukkitTask task;
    private BukkitTask preparation;
    private CompletableFuture<Chunk> preparationChunk;
    private CommandSender preparingSender;
    private final Set<UUID> optedOut = new LinkedHashSet<>();
    private final Set<UUID> waitingSignups = new LinkedHashSet<>();
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
    private boolean soloLab;
    private long matchStarted;
    private java.time.Instant startedAt;
    private final String rulesFingerprint;
    private long archiveSnapshotNanos;
    private long lastSurvivorSample;

    private record TeleportPermit(UUID matchId, Location target) { }
    private int loggedEvents;
    private final PaperRespawnRestrictions restrictions;
    private final PaperAugments augments;
    private final PaperIntel intel;
    private final PaperSupply supply;
    private final PaperHud hud = new PaperHud();
    private final MatchDisplays displays = new MatchDisplays(this::augmentActive);
    private final PaperOverheadHealth overhead;
    private final CombatFeedback feedback;
    private final PaperArt art;
    private final Map<UUID,List<String>> results = new LinkedHashMap<>();
    private final Map<UUID,Long> outAt = new LinkedHashMap<>();
    private final Map<UUID,String> causes = new LinkedHashMap<>();
    private final Set<UUID> entrants = new LinkedHashSet<>();
    private final Map<UUID,List<String>> departureStats = new LinkedHashMap<>();
    private final Map<UUID,UUID> creditedKillers = new LinkedHashMap<>();
    private final Map<UUID,Integer> killStreaks = new LinkedHashMap<>();
    private final Set<UUID> augmentKills = new LinkedHashSet<>();
    private final Set<UUID> displayFailures = new LinkedHashSet<>();
    private void updateDisplay(Player player, Runnable update) {
        if (displayFailures.contains(player.getUniqueId())) return;
        try {
            update.run();
        } catch (RuntimeException failure) {
            displayFailures.add(player.getUniqueId());
            plugin.getLogger().log(Level.WARNING,"개인 경기 화면 갱신 중단",failure);
        }
    }
    private final PracticeTarget target = new PracticeTarget();
    private final MatchTelemetry telemetry = new MatchTelemetry();
    private final AugmentHistory analytics;
    private final MatchArchive archive;
    private final Map<UUID,MatchArchive.PlayerResult> departed = new LinkedHashMap<>();

    public PaperMatchService(JavaPlugin plugin, PaperMatchSettings settings, ArenaSearchService search) {
        this.plugin = plugin;
        this.settings = settings;
        int retention = Math.clamp(plugin.getConfig().getInt("telemetry-retention-days",90),1,3650);
        analytics = new AugmentHistory(plugin.getDataFolder().toPath().resolve("analytics"),plugin.getLogger(),retention);
        archive = new MatchArchive(plugin.getDataFolder().toPath().resolve("match-history"),retention,plugin.getLogger());
        rulesFingerprint = rulesFingerprint(plugin, settings);
        overhead = new PaperOverheadHealth(plugin, this::augmentActive);
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
        art = new PaperArt(plugin,this::augmentActive,() -> clock != null && !clock.isPaused(),at -> at.getNearbyPlayers(24));
        augments = new PaperAugments(plugin, () -> clock, this::augmentMember, this::augmentActive,
                player -> placement.protectedFromDamage(player.getUniqueId(), System.nanoTime()),
                () -> session.state(), this::insideAugmentArena, this::insideAugmentBorder, this::move, placement::protect, settings.rules().procDepth(), telemetry, hud, art);
        feedback = new CombatFeedback(plugin,this::augmentActive,this::insideAugmentArena,augments,hud,art);
        supply = new PaperSupply(plugin, SupplySettings.load(config, settings.rules().gatheringSeconds()),
                this::augmentActive, this::insideAugmentBorder, this::supplyNotice, telemetry, art);
        intel = new PaperIntel(plugin, () -> clock, () -> session.participantIds(), this::augmentActive,
                this::insideAugmentBorder, augments, telemetry, hud, supply::nearbyPvp, art);
        feedback.onConfirmed(intel::confirmed);
    }

    private void notice(String message) {
        for (UUID id : session.participantIds()) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) player.sendMessage(message);
        }
    }

    private void supplyNotice(String message) {
        notice(message);
        if (message.contains(" 예고 ") || message.contains(" 위치 ") || message.endsWith("도착했습니다."))
            for (UUID id : session.participantIds()) {
                Player player = plugin.getServer().getPlayer(id);
                if (player != null && augmentActive(player) && PlayerUi.enabled(player,PlayerUi.Option.SUPPLY))
                    hud.notification(player,io.github.augmentwild.domain.HudMessages.Kind.SUPPLY,message,message);
            }
    }

    public Listener galleryListener() { return new ArtGallery.Input(plugin); }
    public Listener artListener() { return art; }
    PaperArt art() { return art; }

    public String artCommand(CommandSender sender,String action,boolean admin) {
        if (admin && !sender.hasPermission("augmentwild.admin")) return "관리자 권한이 없습니다.";
        if (admin) return switch (action) {
            case "status" -> art.diagnostics(false);
            case "report" -> art.diagnostics(true);
            case "validate" -> art.validate(sender);
            case "menu" -> sender instanceof Player player ? art.openDiagnostics(player,true) : "플레이어 전용 메뉴입니다.";
            default -> "/aw admin art <status|report|validate|menu>";
        };
        if (!(sender instanceof Player player) || !player.hasPermission("augmentwild.play")) return "플레이어 전용 명령입니다.";
        return switch (action) {
            case "status" -> art.packStatus(player);
            case "retry" -> art.retry(player);
            case "safe" -> art.toggleSafe(player);
            case "menu" -> art.openDiagnostics(player,player.hasPermission("augmentwild.admin"));
            default -> "/aw art <status|retry|safe|menu>";
        };
    }
    public Listener feedbackListener() { return feedback; }
    public Listener uiListener() { return new PlayerUi(); }
    public String ui(Player p) { return PlayerUi.open(p,art); }
    public String statusMenu(Player p) {
        ArtGallery.read(p,art,PaperArt.Screen.STATUS,"경기 현황",statusLines(p));
        return "";
    }

    private List<String> statusLines(Player p) {
        var lines = new java.util.ArrayList<>(List.of(status(p).split(" [|] ")));
        if (!isLobby()) {
            long alive = session.participantIds().stream().filter(id -> !departures.contains(id)
                    && session.player(id).life().status() != LifeState.Status.ELIMINATED).count();
            lines.add("생존 " + alive + " / " + entrants.size());
        }
        return lines;
    }

    public String adminMenu(Player p) {
        if (!p.hasPermission("augmentwild.admin")) return "관리자 권한이 없습니다.";
        ArtGallery.read(p,art,PaperArt.Screen.ADMIN,"서버 진단",List.of(operations(p,"dashboard",new String[0]).split("\n")));
        return "";
    }

    public String trackMenu(Player p) {
        if (!p.hasPermission("augmentwild.play") || !p.hasPermission("augmentwild.track")) return "위치 추적 권한이 없습니다.";
        ArtGallery.track(p,art,intel.trackingHelp(),() -> p.sendMessage(
                p.hasPermission("augmentwild.play") && p.hasPermission("augmentwild.track") ? locate(p) : "위치 추적 권한이 없습니다."));
        return "";
    }

    public String augmentSection(Player p, String section) { return augments.openSection(p,section); }

    public String guide(Player p) {
        if (!augmentActive(p) || !PlayerUi.enabled(p,PlayerUi.Option.SUPPLY)) return "보급 안내를 사용할 수 없습니다.";
        ArtGallery.read(p,art,PaperArt.Screen.SUPPLY,"보급 길잡이",List.of(supply.guide(p,true).split(" / ")));
        return "보급 길잡이";
    }
    public String result(Player p) {
        var lines = results.get(p.getUniqueId());
        if (lines == null || task != null || snapshots.pending(p.getUniqueId())) return "현재 확인할 경기 결과가 없습니다.";
        PlayerUi.result(p,lines,art);
        return "경기 결과";
    }

    public Listener supplyListener() { return supply; }
    public Listener intelListener() { return intel; }
    public String locate(Player player) { return intel.locate(player); }

    /** A real diamond right click performs the same transaction as /aw locate. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void diamondTracker(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND
                || event.getItem() == null || event.getItem().getType() != org.bukkit.Material.DIAMOND
                || event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                   && event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!player.hasPermission("augmentwild.play") || !player.hasPermission("augmentwild.track")
                || !augmentActive(player)) return;
        // Respect a different plugin's cancelled block interaction. Bukkit may
        // pre-cancel air interactions when there is nothing to interact with.
        if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK && event.isCancelled()) return;
        event.setCancelled(true);
        player.sendMessage(locate(player));
    }
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

    public UUID menuSession() { return session.matchId(); }
    public boolean isLobby() { return session.state() == MatchState.WAITING; }
    public boolean canCancelFromMenu(Player p) {
        return inMatch(p) || isLobby() && settings.autoJoin() && !optedOut.contains(p.getUniqueId()) && canPlay(p);
    }
    public boolean inMatch(Player player) { return contains(player.getUniqueId()) && !departures.contains(player.getUniqueId()); }
    public boolean soloInProgress(Player player) {
        return soloLab && labWorld != null && task != null && inMatch(player)
                && labWorld.equals(player.getWorld().getUID());
    }
    public boolean picking(Player player) {
        if (clock == null || !inMatch(player)) return false;
        var choice = clock.selection().map(round -> round.choices().get(player.getUniqueId())).orElse(null);
        return choice != null && choice.status() == io.github.augmentwild.domain.SelectionRound.Status.PENDING;
    }
    public List<String> skillDetails(Player player,String id) { return augments.skillDetails(player,id); }
    public String previewSkill(Player player) { return augments.previewSkill(player); }
    public List<String> availableSkills(Player player) { return augments.availableSkills(player); }
    public List<String> registeredAugments() { return augments.registeredIds(); }
    public List<String> ownedAugments(Player player) { return augments.ownedIds(player); }
    public String combatLog(Player p) {
        ArtGallery.read(p,art,PaperArt.Screen.JOURNAL,"나의 전투 기록 · UTC",List.of(hud.combatLog(p).split("\n")));
        return "전투 기록";
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

    private boolean canPlay(Player player) {
        return player != null && player.isOnline() && !player.isDead()
                && player.getGameMode() != GameMode.SPECTATOR && player.hasPermission("augmentwild.play");
    }

    private StartRoster roster(boolean automatic) {
        var eligible = plugin.getServer().getOnlinePlayers().stream().filter(this::canPlay)
                .map(Player::getUniqueId).filter(id -> !optedOut.contains(id) && !snapshots.pending(id)).sorted().toList();
        return StartRoster.select(waitingSignups, eligible, automatic, settings.maximumPlayers());
    }

    public int previewPlayerCount() {
        return Math.max(settings.minimumPlayers(),roster(settings.autoJoin()).players().size());
    }

    public String arenaCheck() {
        var eligible = roster(settings.autoJoin());
        return rosterSummary(eligible) + "\n" + search.check(previewPlayerCount(),
                plugin.getConfig().getString("arena.world",""),false);
    }

    private String rosterSummary(StartRoster roster) {
        int online = plugin.getServer().getOnlinePlayers().size();
        return "온라인 " + online + "명 · 참가 대상 " + roster.players().size() + "/" + settings.maximumPlayers()
                + "명 (자동 " + roster.automatic() + "명) · 정원 초과 " + roster.capacityExcluded()
                + "명 · 그 외 제외 " + Math.max(0,online - roster.players().size() - roster.capacityExcluded()) + "명";
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
        if (!canPlay(player)) return "참가 권한이 있고 살아 있는 비관전자만 참가할 수 있습니다.";
        if (contains(id)) return "이미 참가했습니다.";
        if (!snapshots.restore(player, this::move)) return "이전 경기 상태를 복구하지 못했습니다.";
        if (waitingSignups.size() >= settings.maximumPlayers()) return "참가 정원이 찼습니다.";
        optedOut.remove(id);
        waitingSignups.add(id);
        session.join(id);
        return "대기 중인 경기에 참가했습니다.";
    }

    public String leave(Player player) {
        UUID id = player.getUniqueId();
        if (session.state() == MatchState.WAITING) {
            optedOut.add(id);
            waitingSignups.remove(id);
            if (contains(id)) session.leave(id);
            return "다음 경기 자동 참가를 거부했습니다. /aw join으로 다시 참가할 수 있습니다.";
        }
        if (!contains(id) || departures.contains(id)) return "참가 중인 경기가 없습니다.";
        if (session.state() == MatchState.PREPARING) {
            optedOut.add(id);
            waitingSignups.remove(id);
            interruptPreparation("참가자가 경기 준비 중 탈퇴했습니다.");
        } else if (task != null) departures.add(id);
        return "경기에서 나갔습니다.";
    }

    public void recoverSupply() { supply.recover(); }

    public String start(CommandSender sender) {
        if (!sender.hasPermission("augmentwild.admin")) return "관리자 권한이 없습니다.";
        String blocked = startBlocked();
        if (blocked != null) return blocked;
        var roster = roster(settings.autoJoin());
        boolean solo = roster.players().size() == 1 && settings.soloPractice();
        if (solo && !labSoloAllowed())
            return rosterSummary(roster) + "\n1인 연습을 시작하려면 운영 월드와 분리된 전투 실험실을 활성화하세요. /aw admin test check";
        return rosterSummary(roster) + "\n" + start(sender, roster.players(), solo, solo);
    }

    private boolean labSoloAllowed() {
        return lab.ready() && plugin.getConfig().getBoolean("dev.combat-lab.allow-solo", true);
    }

    /** This explicit lab command always selects just the administering player. */
    public String soloStart(Player player) {
        if (!player.hasPermission("augmentwild.admin") || !player.hasPermission("augmentwild.admin.test"))
            return "관리자 실험 권한이 없습니다.";
        if (!labSoloAllowed()) return "1인 테스트는 활성화된 전용 전투 실험실에서만 가능합니다.";
        String blocked = startBlocked();
        if (blocked != null) return blocked;
        if (!canPlay(player) || snapshots.pending(player.getUniqueId())) return "이전 상태 복구 및 참가 자격을 확인하세요.";
        if (waitingSignups.stream().anyMatch(id -> !id.equals(player.getUniqueId())))
            return "대기 중인 다른 참가자가 있어 혼자 테스트를 시작할 수 없습니다.";
        return start(player,List.of(player.getUniqueId()),true,true);
    }

    private boolean labParticipant(Player player) {
        return labWorld != null && contains(player.getUniqueId());
    }

    public String newcomerGuide(Player player) {
        var rules = settings.rules();
        var rows = List.of("경기 참가 · /aw join · 정원 " + settings.maximumPlayers() + "명",
                "파밍 · " + rules.gatheringSeconds() + "초 · 전투 가능, 파밍 사망 시 목숨 차감 없음",
                "증강 선택 · " + rules.selectionInterval() + "초 간격 · 제한 " + settings.selectionSeconds() + "초 · /aw augment",
                "스킬 조작 · " + PlayerUi.skillInput(player).help() + " · /aw skill menu",
                "영구 성장 · 조건을 달성할 때마다 누적 · 보유 증강의 진행도를 확인",
                "진화 · 증강마다 목표가 다름 · 도감에서 실제 조건 확인",
                "시너지와 공명 · 기록관에서 필요한 구성원과 보유 상태 확인",
                "보급 · /aw supply · 좌표 공개 후 방향과 거리 확인",
                "적 탐지 · 다이아몬드 " + rules.trackerCost() + "개 · /aw track",
                "자기장 · " + rules.pvpStartSeconds() + "초에 축소 시작 · 경고가 뜨면 안쪽으로 이동",
                "부활 · " + rules.respawnSeconds() + "초 · 부활 보호 중에는 스킬 잠금",
                "최종 생존 · 목숨 제한 이후 마지막 생존자가 승리 · /aw result",
                "조작 연습 · 격리 실험실에서 스킬 메뉴·표적·쿨다운 확인 · 관리자에게 요청");
        ArtGallery.read(player,art,PaperArt.Screen.JOURNAL,"처음 하는 경기",rows);
        return "플레이어 가이드를 열었습니다.";
    }

    public String operations(CommandSender sender,String feature,String[] args) {
        if (!sender.hasPermission("augmentwild.admin")) return "관리자 권한이 없습니다.";
        return switch (feature) {
            case "config" -> ConfigMigration.run(plugin.getDataFolder().toPath().resolve("config.yml"),args);
            case "stats" -> statistics(sender,args);
            case "history" -> args.length == 1 ? historyLookup(sender,args[0],true) : "/aw admin history <match-id>";
            case "recover" -> switch (args.length == 1 ? args[0] : "") {
                case "history" -> archive.retry();
                case "stats" -> analytics.retry();
                case "snapshots" -> {
                    if (session.state() != MatchState.WAITING) yield "BLOCKED · 대기 상태에서만 이전 스냅샷을 다시 검사합니다.";
                    int before = snapshots.pendingCount();
                    for (var player : plugin.getServer().getOnlinePlayers()) if (snapshots.pending(player.getUniqueId())) recover(player);
                    yield "이전 상태 복원 · 대기 " + before + " → " + snapshots.pendingCount();
                }
                default -> "/aw admin recover <history|stats|snapshots>";
            };
            case "dashboard" -> {
                var online = roster(settings.autoJoin());
                var world = plugin.getServer().getWorld(plugin.getConfig().getString("arena.world","world"));
                var labLoaded = lab.ready() && plugin.getServer().getWorld(lab.world()) != null;
                var rows = List.of("서버 · " + plugin.getServer().getVersion() + " · Java " + Runtime.version(),
                        "플러그인 " + plugin.getPluginMeta().getVersion() + " · 경기 " + session.state(),
                        "참가 가능 " + online.players().size() + "/" + settings.maximumPlayers(),
                        (world == null ? "FAIL · 운영 월드 미로드" : "PASS · 운영 월드 로드 · 월드보더 " + Math.round(world.getWorldBorder().getSize())) + " · /aw admin arena check",
                        (labLoaded ? "PASS · 격리 실험 월드 로드" : "WARN · 실험 월드 미설정 또는 미로드") + " · /aw admin test check",
                        "리소스팩 · /aw admin art validate",art.diagnostics(false),
                        "통계 저장 " + analytics.status() + " · /aw admin stats",
                        "경기 결과 " + archive.status() + " · /aw admin recover history",
                        "경기 스냅샷 최근 ms " + String.format(java.util.Locale.ROOT,"%.2f",archiveSnapshotNanos/1e6),
                        "이전 상태 복원 대기 " + snapshots.pendingCount() + " · 재접속 시 재확인",
                        "경기장 · " + (preparation != null ? "CHECKING · 준비 중" : arena != null ? "PASS · 참가자 배치 완료"
                                : "NOT_RUN · 운영 월드 로드 여부와 별개") + " · /aw admin arena check",
                        "보급 복구 · " + supply.recoveryStatus(),
                        "전투 저널 · " + feedback.metrics(),
                        "HUD · " + hud.metrics(),
                        "설정 검사 · /aw admin config check",
                        "연출 · " + art.metrics(),
                        "최근 평균 틱 " + String.format(java.util.Locale.ROOT,"%.2fms",org.bukkit.Bukkit.getAverageTickTime()));
                if (sender instanceof Player player) ArtGallery.diagnostics(player,art,rows);
                yield String.join("\n",rows);
            }
            default -> "지원하지 않는 운영 명령입니다.";
        };
    }

    private String statistics(CommandSender sender,String[] args) {
        if (args.length == 1 && args[0].equals("export")) return analytics.export();
        if (args.length == 0) {
            var summary = analytics.summary();
            if (sender instanceof Player player) ArtGallery.read(player,art,PaperArt.Screen.ADMIN,"증강 통계",List.of(summary.split("\n")));
            return summary;
        }
        try {
            final java.util.function.Supplier<List<String>> report;
            if (args.length >= 2 && args[0].equals("augment")
                    && (augments.registeredIds().contains(args[1]) || Set.of("unclassified","life_echo","stacking").contains(args[1])
                        || io.github.augmentwild.augment.AugmentCombinations.UNIQUE.containsKey(args[1]))) {
                String id = args[1];
                var filter = AugmentHistory.Filter.parse(args,2);
                report = () -> analytics.report(id,filter);
            } else if (args.length >= 3 && args[0].equals("group")) {
                var filter = AugmentHistory.Filter.parse(args,3);
                List<String> ids = switch (args[1]) {
                    case "rarity" -> augments.registeredIds(args[2]);
                    case "family" -> augments.registeredIds().stream().filter(id -> art.family(id).equals(args[2])).toList();
                    default -> List.of();
                };
                if (ids.isEmpty()) return "조회할 희귀도 또는 계열을 확인하세요.";
                report = () -> analytics.group(ids,filter);
            } else return analytics.summary();
            var scheduler = plugin.getServer().getScheduler();
            return analytics.async(() -> {
                List<String> data = report.get();
                try {
                    scheduler.runTask(plugin,() -> {
                        if (!sender.hasPermission("augmentwild.admin")) return;
                        if (sender instanceof Player player) {
                            if (player.isOnline()) ArtGallery.statistics(player,art,data,0);
                        } else sender.sendMessage(String.join("\n",data));
                    });
                } catch (org.bukkit.plugin.IllegalPluginAccessException stopping) {
                    // A report can complete after plugin disable.
                }
            }) ? "통계를 조회합니다." : analytics.status();
        } catch (IllegalArgumentException invalid) {
            return "조회 조건이 올바르지 않습니다. " + analytics.summary();
        }
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
        if (action.equals("check") && args.length == 1) {
            if (!lab.ready()) return "dev.combat-lab.enabled와 운영 월드와 다른 dev.combat-lab.world를 확인하세요.";
            var world = plugin.getServer().getWorld(lab.world());
            if (world == null) return "테스트 월드 '" + lab.world() + "'를 기존 월드 관리 도구에서 로드하세요.";
            return "실험 월드 접속자 " + world.getPlayers().size() + "명\n"
                    + search.check(Math.max(labSoloAllowed() ? 1 : settings.minimumPlayers(),roster(false).players().size()),
                            lab.world(),true);
        }
        if (!lab.ready()) return "전투 실험실을 활성화하고 운영 월드와 다른 기존 테스트 월드를 지정하세요.";
        if (action.equals("start") && args.length == 1) {
            String blocked = startBlocked();
            return blocked == null ? start(sender,roster(false).players(),true,false) : blocked;
        }
        if (action.equals("solo") && args.length == 1)
            return sender instanceof Player player ? soloStart(player) : "1인 테스트는 플레이어만 시작할 수 있습니다.";
        if (action.equals("presets") && args.length == 1) {
            return "실험 프리셋 " + io.github.augmentwild.augment.AugmentCombinations.PRESETS.keySet();
        }
        if (!lab.allows(true, labWorld != null && task != null, clock == null || clock.isPaused())) {
            return "증강 실험 명령은 진행 중이며 일시정지 상태가 아닌 테스트 경기에서만 사용할 수 있습니다.";
        }
        if (action.equals("preview") && args.length <= 2 && sender instanceof Player viewer) {
            UUID thisMatch = session.matchId();
            java.util.function.BooleanSupplier allowed = () -> viewer.hasPermission("augmentwild.admin")
                    && viewer.hasPermission("augmentwild.admin.test") && thisMatch.equals(session.matchId())
                    && labWorld != null && labWorld.equals(viewer.getWorld().getUID()) && labParticipant(viewer)
                    && augmentActive(viewer) && clock != null && !clock.isPaused();
            if (!allowed.getAsBoolean()) return "살아 있는 테스트 참가자만 연출을 볼 수 있습니다.";
            ArtGallery.browse(viewer,art,allowed,args.length == 2 ? args[1].toLowerCase(java.util.Locale.ROOT) : "");
            return "연출 실험실";
        }
        if (action.equals("advance") && args.length == 1 && sender instanceof Player owner) {
            if (!soloInProgress(owner) || !augmentActive(owner) || session.state() != MatchState.GATHERING)
                return "1인 실험의 파밍 단계에서만 다음 증강 선택으로 이동할 수 있습니다.";
            long interval = settings.rules().selectionInterval();
            long next = (clock.elapsedSeconds() / interval + 1) * interval;
            if (next > settings.rules().gatheringSeconds()) return "남은 선택 회차가 없습니다.";
            labAdvanceNanos += next * 1_000_000_000L - clock.elapsedNanos();
            clock.tick();
            return "다음 증강 선택 도달";
        }
        if (action.equals("dummy") && args.length == 2 && sender instanceof Player owner) {
            if (!soloInProgress(owner) || !augmentActive(owner)) return "1인 테스트 참가자만 사용할 수 있습니다.";
            return switch (args[1]) {
                case "stationary","moving","high","shield" -> target.create(owner,args[1],this::insideAugmentBorder);
                case "report" -> target.report();
                case "clear" -> { target.clear(); yield "표적 제거"; }
                default -> "dummy stationary|moving|high|shield|report|clear";
            };
        }
        int length = switch (action) {
            case "list", "clear", "report", "reset","cooldowns","gear","heal","shield" -> 2;
            case "grant", "remove", "health", "preset","cast" -> 3;
            case "progress" -> 4;
            default -> 0;
        };
        if (length == 0 || args.length != length) return "사용법: /aw admin test <status|start|solo|preview|dummy|advance|grant|remove|cast|progress|preset|cooldowns|gear|heal|health|shield|list|clear|report|reset|presets>";
        Player player = plugin.getServer().getPlayerExact(args[1]);
        if (player == null || !labParticipant(player) || !augmentActive(player)
                || !labWorld.equals(player.getWorld().getUID())) return "살아 있는 테스트 참가자를 지정하세요.";
        String id = args.length > 2 ? args[2].toLowerCase(java.util.Locale.ROOT) : "";
        return switch (action) {
            case "gear" -> {
                if (!soloInProgress(player) || player.getInventory().firstEmpty() == -1)
                    yield "빈 슬롯이 있는 1인 실험에서만 장비를 받을 수 있습니다.";
                player.getInventory().setItem(player.getInventory().firstEmpty(),
                        new org.bukkit.inventory.ItemStack(org.bukkit.Material.IRON_SWORD));
                yield "실험용 철검 지급";
            }
            case "heal" -> {
                player.setHealth(player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
                yield "실험 체력 복구";
            }
            case "shield" -> {
                var effect = new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.ABSORPTION,200,1);
                player.addPotionEffect(effect);
                yield "바닐라 흡수 실험";
            }
            case "cooldowns" -> augments.labResetSkills(player);
            case "cast" -> augments.cast(player,id);
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

    private String startBlocked() {
        if (session.state() != MatchState.WAITING) return "경기가 이미 준비 중이거나 진행 중입니다.";
        if (search.busy()) return "이전 경기장 탐색이 정리될 때까지 기다리세요.";
        if (!supply.recover()) return "미처리 보급 복구 기록이 있습니다. 서버 로그를 확인하세요.";
        return null;
    }

    private String start(CommandSender sender,List<UUID> players,boolean experiment,boolean remoteSolo) {
        boolean singlePractice = experiment && players.size() == 1 && labSoloAllowed();
        if (players.size() < settings.minimumPlayers() && !singlePractice)
            return "참가자가 최소 " + settings.minimumPlayers() + "명 필요합니다.";
        if (players.stream().anyMatch(id -> !canPlay(available(id)) || snapshots.pending(id)))
            return "모든 참가자가 접속하고 살아 있어야 하며 이전 복구 작업이 없어야 합니다.";

        String worldName = plugin.getConfig().getString("arena.world", "");
        World practiceWorld = null;
        if (experiment) {
            practiceWorld = plugin.getServer().getWorld(lab.world());
            if (practiceWorld == null || practiceWorld.getEnvironment() != World.Environment.NORMAL || !practiceWorld.getPVP()
                    || practiceWorld.equals(plugin.getServer().getWorld(worldName))) {
                return "운영 경기와 분리된 기존 PvP 테스트 오버월드를 로드하세요.";
            }
            UUID worldId = practiceWorld.getUID();
            if (!remoteSolo && players.stream().anyMatch(id -> !worldId.equals(available(id).getWorld().getUID()))
                    || practiceWorld.getPlayers().stream().anyMatch(player -> !players.contains(player.getUniqueId()))) {
                return "모든 테스트 참가자가 테스트 월드에 있어야 하며 비참가자가 없어야 합니다.";
            }
            worldName = lab.world();
        }

        UUID matchId = session.matchId();
        String message = search.search(sender,players.size(),matchId,worldName,experiment,context -> {
            if (!matchId.equals(session.matchId()) || session.state() != MatchState.PREPARING) return;
            if (context == null) clearMatch();
            else preparePlacement(sender,players,context,remoteSolo);
        });
        if (!search.running()) return message;

        // The search schedules its first step for the next tick. Lock this exact roster first.
        for (UUID id : session.participantIds()) if (!players.contains(id)) session.leave(id);
        for (UUID id : players) if (!contains(id)) session.join(id);
        labWorld = practiceWorld == null ? null : practiceWorld.getUID();
        soloLab = singlePractice;
        if (singlePractice) session.enablePractice();
        preparingSender = sender;
        session.transition(MatchState.PREPARING);
        return (singlePractice ? "1인 연습 · 승패 기록 없음\n" : "") + message;
    }

    private Player available(UUID id) {
        Player player = plugin.getServer().getPlayer(id);
        return player != null && player.isOnline() && !player.isDead() ? player : null;
    }

    private void preparePlacement(CommandSender sender,List<UUID> players,ArenaSearchService.ArenaContext context,boolean remoteSolo) {
        try {
            var world = plugin.getServer().getWorld(context.worldId());
            if (world == null || !world.getPVP() || !ArenaSearchService.validPlacement(world,context.area(),context.worldBorderMargin()))
                throw new IllegalStateException("Arena world, PvP or world border changed");
            // Capture every player before moving anyone.
            for (UUID id : players) {
                Player player = available(id);
                if (!canPlay(player) || labWorld != null && !remoteSolo && !labWorld.equals(player.getWorld().getUID())) {
                    throw new IllegalStateException("Participant unavailable at arena preparation: " + id);
                }
                if (!captured.contains(id)) {
                    snapshots.capture(player);
                    captured.add(id);
                }
            }
            var targets = context.spawns();
            final class Placing implements Runnable {
                private int next;
                private final long started = System.nanoTime();
                @Override public void run() {
                    try {
                        if (!context.matchId().equals(session.matchId())) return;
                        if (System.nanoTime() - started >= settings.placementTimeoutNanos()) {
                            throw new IllegalStateException("Arena placement timed out");
                        }
                        Player player = available(players.get(next));
                        if (!canPlay(player)) throw new IllegalStateException("Participant unavailable during placement");
                        if (labWorld != null && world.getPlayers().stream().anyMatch(p -> !players.contains(p.getUniqueId())))
                            throw new IllegalStateException("Nonparticipant entered the combat lab");
                        if (labWorld != null && !remoteSolo && !world.equals(player.getWorld()))
                            throw new IllegalStateException("Participant left the combat lab world");
                        if (preparationChunk != null) return;
                        Location target = targets.get(next);
                        var request = world.getChunkAtAsyncUrgently(target,context.generate());
                        preparationChunk = request;
                        request.whenComplete((chunk,failure) -> place(request,player,target,chunk,failure));
                    } catch (Exception failure) {
                        failPreparation(sender, failure);
                    }
                }

                // Paper completes the chunk future on the server thread. Validate and move while its load ticket is live.
                private void place(CompletableFuture<Chunk> request, Player player, Location target, Chunk chunk, Throwable failure) {
                    if (preparationChunk != request || !context.matchId().equals(session.matchId())) return;
                    preparationChunk = null;
                    try {
                        if (failure != null) throw new IllegalStateException("Arena spawn chunk request failed",failure);
                        if (chunk == null) throw new IllegalStateException("Arena spawn chunk unavailable: " + target);
                        if (!world.isChunkLoaded(chunk.getX(),chunk.getZ())) return; // Bounded by the placement deadline.
                        if (!canPlay(player)) throw new IllegalStateException("Participant unavailable during placement");
                        if (!SafeArenaSurface.validSpawn(target)) throw new IllegalStateException("Arena spawn became unsafe: " + target);
                        if (!move(player,target)) throw new IllegalStateException("Arena teleport rejected: " + target
                                + " · actual " + player.getLocation());
                        snapshots.enterMatch(player);
                        if (++next == players.size()) activate(sender,players,context);
                    } catch (Exception aborted) {
                        failPreparation(sender,aborted);
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
        if (world == null || !world.getPVP() || !ArenaSearchService.validPlacement(world,context.area(),context.worldBorderMargin())
                || players.stream().anyMatch(id -> !canPlay(available(id)) || !inside(context, available(id)))
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
        preparingSender = null;
        waitingSignups.clear();
        session.transition(MatchState.GATHERING);
        // Start timing only after the placement and history write have succeeded.
        long started = System.nanoTime();
        matchStarted = started;
        startedAt = java.time.Instant.now();
        if (labWorld != null) {
            notice("관리자 전투 실험 경기입니다. 결과는 실험실에서만 사용됩니다.");
            plugin.getLogger().info("Combat lab started match=" + session.matchId());
        }
        lastSurvivorSample = Long.MIN_VALUE;
        labAdvanceNanos = 0;
        java.util.function.LongSupplier elapsed = labWorld == null ? () -> System.nanoTime() - started
                : () -> System.nanoTime() - started + labAdvanceNanos;
        clock = new MatchSimulationClock(session, settings.rules(), elapsed,
                settings.selectionSeconds(), supply.start(session.matchId(), context.spawns()));
        augments.start(session.matchId(), clock, labWorld != null);
        entrants.clear();
        entrants.addAll(players);
        displayFailures.clear();
        telemetry.start(players);
        intel.start(session.matchId());
        hud.startJournal(session.matchId(),clock::elapsedNanos);
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

    private void interruptPreparation(String reason) {
        var sender = preparingSender;
        clearMatch();
        if (sender != null) sender.sendMessage(reason + " 경기 준비를 중단했습니다.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void targetDeath(org.bukkit.event.entity.EntityDeathEvent event) {
        if (!target.owns(event.getEntity())) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
        target.died();
    }

    public String stop() {
        if (session.state() == MatchState.WAITING) return "진행 중인 경기가 없습니다.";
        clearMatch();
        return "경기를 종료했습니다.";
    }

    public void shutdown() {
        clearMatch();
        intel.shutdown();
        analytics.close();
        archive.close();
    }

    private void clearMatch() {
        boolean played = clock != null;
        if (played) {
            feedback.finishSamples();
            plugin.getLogger().info("AW_ART_MATCH " + art.metrics());
        }
        target.clear();
        art.clear();
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            if (PaperMenu.protectedInventory(viewer.getOpenInventory().getTopInventory())) viewer.closeInventory();
        }
        displays.clear();
        overhead.clear();
        search.cancel();
        search.drain(preparationChunk);
        preparationChunk = null;
        preparingSender = null;
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
                if (!soloLab && labWorld == null && entrants.size() >= 2 && result.outcome() != GameSession.Outcome.CANCELLED)
                    analytics.append(session.matchId(),entrants.size(),telemetry.augments().snapshot(result.winner()),AugmentHistory.CURRENT);
                archiveResult(result);
                Player victor = result.winner() == null ? null : plugin.getServer().getPlayer(result.winner());
                if (victor != null) killEffect(victor,"최종 우승",org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE,org.bukkit.Particle.END_ROD);
                for (UUID id : entrants) {
                    var lines = new java.util.ArrayList<String>();
                    lines.add(soloLab ? "1인 연습 종료 · 승패 기록 없음" : result.winner() == null ? (result.outcome() == GameSession.Outcome.DRAW ? "무승부" : "경기 중단")
                            : "우승 · " + plugin.getServer().getOfflinePlayer(result.winner()).getName());
                    if (result.outcome() != GameSession.Outcome.CANCELLED) {
                        long rank = outAt.containsKey(id) ? 1 + entrants.stream()
                                .filter(other -> outAt.getOrDefault(other,Long.MAX_VALUE) > outAt.get(id)).count() : 1;
                        lines.add("순위 " + rank);
                    }
                    lines.addAll(departureStats.containsKey(id) ? departureStats.get(id) : resultLines(id));
                    results.remove(id);
                    results.put(id,List.copyOf(lines));
                    if (results.size() > 256) results.remove(results.keySet().iterator().next());
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
        String mode = soloLab ? "practice" : labWorld == null ? "normal" : "lab";
        if (clock != null) plugin.getLogger().info("Match tempo match=" + session.matchId() + " mode=" + mode + " "
                + telemetry.tempo(clock.elapsedSeconds(), (System.nanoTime() - matchStarted) / 1_000_000_000L));
        var stats = telemetry.finish();
        if (!stats.isEmpty()) plugin.getLogger().info("Match feature totals match=" + session.matchId()
                + " mode=" + mode + " " + new java.util.TreeMap<>(stats));
        killStreaks.clear();
        augmentKills.clear();
        feedback.clear();
        entrants.clear();
        departureStats.clear();
        departed.clear();
        creditedKillers.clear();
        outAt.clear();
        causes.clear();
        intel.clear();
        augments.clear();
        if (played) {
            plugin.getLogger().info("AW_HUD_MATCH " + hud.metrics());
            plugin.getLogger().info("AW_MATCH_ANALYTICS match=" + session.matchId() + " snapshot_ns=" + archiveSnapshotNanos
                    + " journal=" + feedback.metrics());
        }
        hud.clear(plugin);
        regionCommitted = false;
        labWorld = null;
        soloLab = false;
        arena = null;
        clock = null;
        for (UUID id : captured) {
            Player player = available(id);
            if (player != null && snapshots.restore(player, this::move) && played && plugin.isEnabled() && results.containsKey(id))
                updateDisplay(player,() -> PlayerUi.result(player,results.get(id),art));
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
        if (played) optedOut.clear();
        waitingSignups.removeIf(id -> !canPlay(available(id)) || snapshots.pending(id));
        for (UUID id : waitingSignups) session.join(id);
    }

    private void tick() {
        try {
            clock.tick();
            var events = clock.events();
            while (loggedEvents < events.size()) {
                String event = events.get(loggedEvents++);
                plugin.getLogger().info("Match " + session.matchId() + " timeline event " + event);
                String phaseNotice = switch (event) {
                    case "pvp" -> "목숨 제한 · 자기장 축소 시작";
                    case "sudden-death" -> "서든데스 · 이제 부활할 수 없습니다";
                    default -> null;
                };
                if (phaseNotice != null) for (UUID id : session.participantIds())
                    if (plugin.getServer().getPlayer(id) != null) hud.phase(id,phaseNotice);
            }
            long now = clock.elapsedNanos();
            awaitingNativeRespawn.removeAll(departures);
            for (UUID id : departures) {
                feedback.finishSamples();
                outAt.putIfAbsent(id,clock.elapsedNanos());
                departureStats.put(id,resultLines(id));
                departed.put(id,archivePlayer(id,0));
                art.forget(id);
                displays.detach(id);
                overhead.detach(id);
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
            var feed = new java.util.ArrayList<String>();
            var fallen = new java.util.ArrayList<UUID>();
            for (var entry : deaths.entrySet()) {
                UUID id = entry.getKey();
                if (!contains(id) || !session.applyDeath(entry.getValue())) continue;
                UUID killer = creditedKillers.remove(id);
                telemetry.death(id,killer);
                fallen.add(id);
                boolean eliminated = session.player(id).life().status() == LifeState.Status.ELIMINATED;
                intel.finalizeDeath(id,eliminated,clock.elapsedSeconds() < settings.rules().pvpStartSeconds());
                if (eliminated) outAt.putIfAbsent(id,clock.elapsedNanos());
                String message = io.github.augmentwild.domain.CombatView.feed(causes.get(id),eliminated);
                if (killer != null && !killer.equals(id)) {
                    int streak = killStreaks.merge(killer,1,Integer::sum);
                    if (streak > 1) message += " · " + streak + "연속 처치";
                    Player credited = plugin.getServer().getPlayer(killer);
                    if (credited != null) {
                        String effect = eliminated ? "최종 탈락 확정" : augmentKills.contains(id) ? "증강 처치" : "처치";
                        killEffect(credited,streak > 1 ? effect + " · " + streak + "연속" : effect,
                                eliminated ? org.bukkit.Sound.ENTITY_PLAYER_LEVELUP : org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING,
                                augmentKills.contains(id) ? org.bukkit.Particle.ENCHANT : org.bukkit.Particle.CRIT);
                    }
                }
                feed.add(message);
            }
            if (!feed.isEmpty()) {
                String message = String.join("  |  ",feed);
                for (UUID viewer : session.participantIds()) {
                    Player p = plugin.getServer().getPlayer(viewer);
                    if (p != null && PlayerUi.enabled(p,PlayerUi.Option.FEED)) p.sendMessage(message);
                }
            }
            fallen.forEach(killStreaks::remove);
            augmentKills.clear();
            causes.clear();
            creditedKillers.clear();
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
            target.tick(this::insideAugmentBorder,clock.isPaused());
            art.tick();
            var round = clock.selection().orElse(null);
            for (UUID id : activeSession.participantIds()) {
                Player player = plugin.getServer().getPlayer(id);
                if (player != null) updateDisplay(player,() -> {
                    overhead.update(player,tickNumber);
                    if (tickNumber % 20 == 1) {
                        displays.sidebar(player,sidebar(player));
                        if (player.getOpenInventory().getTopInventory().getHolder() instanceof ArtGallery gallery) {
                            if (gallery.isGuideFor(player)) gallery.updateGuide(player,List.of(supply.guide(player,true).split(" / ")));
                            else if (gallery.isStatusFor(player)) gallery.updateRows(player,"status",statusLines(player));
                        }
                    }
                });
                if (player == null || departures.contains(id) || activeSession.player(id).life().status() == LifeState.Status.ELIMINATED) {
                    hud.clear(id,player);
                    continue;
                }
                if (!augmentActive(player)) {
                    boolean respawning = activeSession.player(id).life().status() == LifeState.Status.RESPAWNING;
                    hud.suspend(id,respawning);
                    if (respawning) {
                        String countdown = activeSession.reservation(id).filter(r -> r.deadline() > now)
                                .map(r -> " · " + Math.ceilDiv(r.deadline()-now,1_000_000_000L) + "초").orElse("");
                        hud.set(id,Layer.RESPAWN,Component.text("부활 대기" + countdown));
                    }
                    hud.render(player,realTime);
                    continue;
                }
                hud.remove(id,Layer.RESPAWN);
                var choice = round == null ? null : round.choices().get(id);
                if (choice != null && choice.status() == io.github.augmentwild.domain.SelectionRound.Status.PENDING) {
                    hud.set(id, Layer.SELECTION, Component.text("증강 선택 · 남은 시간 " + clock.selectionRemainingSeconds() + "초"));
                } else if (clock.isPaused()) hud.set(id, Layer.SELECTION, Component.text("경기 시계 일시정지"));
                else hud.remove(id, Layer.SELECTION);
                if (supply.isOpening(id)) hud.set(id, Layer.SUPPLY, Component.text("보급 개봉 중 · 이동하거나 피격되면 중단됩니다."));
                else hud.remove(id, Layer.SUPPLY);

                hud.crisis(player,realTime);
                if (tickNumber % 5 == 1) {
                    hud.border(player,frame.area(),elapsed >= shrinkStart && elapsed < shrinkStart + settings.rules().shrinkSeconds());
                    hud.status(player,augments.timedStates(id),Math.floorDiv(realTime,1_000_000_000L));
                }
                hud.render(player, realTime);
            }
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.SEVERE, "Match tick failed", failure);
            clearMatch();
        }
    }

    private void killEffect(Player p, String label, org.bukkit.Sound sound, org.bukkit.Particle particle) {
        if (!PlayerUi.enabled(p,PlayerUi.Option.KILL_FX)) return;
        p.showTitle(net.kyori.adventure.title.Title.title(Component.empty(), Component.text(label),
                net.kyori.adventure.title.Title.Times.times(java.time.Duration.ZERO,
                        java.time.Duration.ofMillis(750),java.time.Duration.ofMillis(200))));
        if (PlayerUi.enabled(p,PlayerUi.Option.SOUND)) p.playSound(p.getLocation(),sound,.45f,1.3f);
        if (PlayerUi.enabled(p,PlayerUi.Option.PARTICLES)) p.spawnParticle(particle,p.getEyeLocation().add(0,.25,0),4);
    }

    private static String rulesFingerprint(JavaPlugin plugin,PaperMatchSettings settings) {
        try {
            var hash = java.security.MessageDigest.getInstance("SHA-256");
            hash.update(settings.rules().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            for (String key : List.of("augments.yml","official-augments.yml","synergies.yml","resonance.yml")) {
                var file = plugin.getDataFolder().toPath().resolve(key);
                if (java.nio.file.Files.isRegularFile(file)) hash.update(java.nio.file.Files.readAllBytes(file));
                else try (var resource = plugin.getResource(key)) {
                    if (resource != null) hash.update(resource.readAllBytes());
                }
            }
            return java.util.HexFormat.of().formatHex(hash.digest());
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException failure) {
            plugin.getLogger().log(Level.WARNING,"경기 규칙 지문을 읽을 수 없습니다.",failure);
            return "UNCLASSIFIED";
        }
    }

    private int rank(UUID id) {
        Long finished = outAt.get(id);
        return finished == null ? 1 : 1 + (int)entrants.stream()
                .filter(other -> outAt.getOrDefault(other,Long.MAX_VALUE) > finished).count();
    }

    private MatchArchive.PlayerResult archivePlayer(UUID id,int rank) {
        var left = departed.get(id);
        if (left != null) return new MatchArchive.PlayerResult(id,rank,left.kills(),left.deaths(),left.lives(),
                left.survival(),left.dealt(),left.received(),left.healed(),left.shield(),left.level(),left.levelCurrent(),
                left.casts(),left.supplies(),left.augments(),left.attained());
        var player = plugin.getServer().getPlayer(id);
        return new MatchArchive.PlayerResult(id,rank,telemetry.kills(id),telemetry.deaths(id),
                session.participantIds().contains(id) ? session.player(id).life().lives() : -1,
                outAt.getOrDefault(id,clock.elapsedNanos()) / 1_000_000_000L,
                feedback.dealt(id),feedback.received(id),hud.healed(id),feedback.shielded(id),
                player == null ? augments.lastKnownLevel(id) : player.getLevel(),player != null,
                augments.skillUses(id),telemetry.supplyClaims(id),augments.ownedIds(id),
                java.util.stream.Stream.concat(telemetry.attained(id).stream(),
                        augments.ownedNames(id).stream().map(name -> "획득 · " + name)).toList());
    }

    private void archiveResult(GameSession.Result result) {
        long begin = System.nanoTime();
        try {
            var rows = new LinkedHashMap<UUID,MatchArchive.PlayerResult>();
            for (UUID id : entrants) rows.put(id,archivePlayer(id,result.outcome() == GameSession.Outcome.CANCELLED ? 0 : rank(id)));
            var outcome = switch (result.outcome()) {
                case WINNER -> MatchArchive.Outcome.WIN;
                case DRAW -> MatchArchive.Outcome.DRAW;
                case CANCELLED, IN_PROGRESS -> MatchArchive.Outcome.CANCELLED;
            };
            var mode = soloLab ? MatchArchive.Mode.PRACTICE : labWorld != null ? MatchArchive.Mode.LAB : MatchArchive.Mode.NORMAL;
            supply.snapshotAvailability();
            var end = java.time.Instant.now();
            archive.append(new MatchArchive.Result(session.matchId(),plugin.getPluginMeta().getVersion(),rulesFingerprint,
                    end.isBefore(startedAt) ? end : startedAt,end,mode,outcome,result.winner(),clock.elapsedSeconds(),rows,
                    telemetry.supplyEvents()));
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING,"경기 결과 스냅샷 실패 " + session.matchId(),failure);
        } finally { archiveSnapshotNanos = System.nanoTime()-begin; }
    }

    public String history(Player player,String[] args) {
        if (args.length == 0) {
            historyPage(player,0);
            return "나의 경기 기록";
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("last")) {
            var last = archive.list(player.getUniqueId(),0,1);
            return last.isEmpty() ? "저장된 기록 없음 · " + archive.status() : historyLookup(player,last.getFirst().id().toString(),false);
        }
        return args.length == 1 ? historyLookup(player,args[0],false) : "/aw history [last|match-id]";
    }

    private void historyPage(Player player,int page) {
        var data = archive.list(player.getUniqueId(),page,9);
        var lines = data.stream().map(e -> e.ended() + " · " + e.mode() + " · " + e.outcome() + " · "
                + e.players() + "인 · 순위 " + (e.rank() == 0 ? "-" : e.rank())).toList();
        ArtGallery.history(player,art,lines,data.stream().map(e -> (Runnable)() -> historyLookup(player,e.id().toString(),false)).toList(),
                page > 0 ? () -> historyPage(player,page-1) : null,
                archive.list(player.getUniqueId(),page+1,9).isEmpty() ? null : () -> historyPage(player,page+1));
    }

    private String historyLookup(CommandSender sender,String key,boolean admin) {
        if (!key.matches("(?i)[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")) return "올바른 경기 UUID를 입력하세요.";
        UUID viewer = sender instanceof Player p ? p.getUniqueId() : null;
        var scheduler = plugin.getServer().getScheduler();
        return archive.lookup(UUID.fromString(key),viewer,admin,result -> {
            try {
                scheduler.runTask(plugin,() -> {
                    if (sender instanceof Player p && !p.isOnline() || admin && !sender.hasPermission("augmentwild.admin")) return;
                    if (result == null) { sender.sendMessage("조회 가능한 경기 기록이 없습니다."); return; }
                    var header = new java.util.ArrayList<>(List.of("경기 " + result.id(),result.started() + " ~ " + result.ended(),
                            result.mode() + " · " + result.outcome() + " · " + result.players().size() + "인 · " + result.elapsed() + "초",
                            "우승자 · " + (result.winner() == null ? "-" : result.winner()),"규칙 지문 · " + result.rules()));
                    if (admin) {
                        header.add("보급 사건 · " + result.supplyEvents());
                        result.players().values().stream().sorted(java.util.Comparator.comparingInt(MatchArchive.PlayerResult::rank))
                                .forEach(row -> header.add(row.id() + " · 순위 " + row.rank() + " · 처치 " + row.kills() + " · 사망 " + row.deaths()));
                    } else {
                        var row = result.players().get(viewer);
                        header.add("순위 " + (row.rank() == 0 ? "-" : row.rank()) + " · 처치 " + row.kills() + " · 사망 " + row.deaths());
                        header.add("생존 " + row.survival() + "초 · 목숨 " + (row.lives() < 0 ? "미확인" : row.lives()));
                        header.add(String.format(java.util.Locale.ROOT,"확인 HP · 가한 %.1f · 받은 %.1f · 회복 %.1f · 보호막 %.1f",
                                row.dealt(),row.received(),row.healed(),row.shield()));
                        header.add((row.levelCurrent() ? "최종 레벨 " : "마지막 확인 레벨 ") + row.level()
                                + " · 스킬 " + row.casts() + " · 보급 " + row.supplies());
                        header.add("최종 증강 · " + String.join(", ",row.augments()));
                        header.addAll(row.attained());
                    }
                    if (sender instanceof Player p) ArtGallery.read(p,art,PaperArt.Screen.JOURNAL,"경기 기록 상세",header);
                    else sender.sendMessage(String.join("\n",header));
                });
            } catch (org.bukkit.plugin.IllegalPluginAccessException stopping) {
                // The server stopped between disk read and callback. Files are already safe.
            }
        }) ? "경기 기록을 조회합니다." : archive.status();
    }

    private List<String> resultLines(UUID id) {
        var lines = new java.util.ArrayList<String>();
        lines.add("처치 " + telemetry.kills(id) + " · 사망 " + telemetry.deaths(id));
        lines.add(feedback.totals(id));
        lines.add(String.format(java.util.Locale.ROOT,"확인된 체력 회복 %.1f HP",hud.healed(id)));
        lines.add("생존 시간 " + io.github.augmentwild.domain.CombatView.time(outAt.getOrDefault(id,clock.elapsedNanos()) / 1_000_000_000L));
        lines.add("스킬 사용 " + augments.skillUses(id) + " · 보급 획득 " + telemetry.supplyClaims(id));
        Player online = plugin.getServer().getPlayer(id);
        if (online != null) lines.add("최종 레벨 " + online.getLevel());
        else lines.add("마지막 확인 레벨 " + augments.lastKnownLevel(id));
        lines.add("획득 증강 · " + String.join(", ",augments.ownedNames(id)));
        lines.addAll(telemetry.attained(id));
        return List.copyOf(lines);
    }

    private List<String> sidebar(Player player) {
        long elapsed = clock.elapsedSeconds(), start = settings.rules().pvpStartSeconds(), end = start + settings.rules().shrinkSeconds();
        long next = io.github.augmentwild.domain.CombatView.nextPick(elapsed,settings.rules().selectionInterval(),settings.rules().gatheringSeconds());
        UUID id = player.getUniqueId();
        return List.of(switch (session.state()) {
            case AUGMENT_SELECTION -> "증강 선택 중"; case GATHERING -> "파밍 단계"; case PVP -> "자기장 축소"; case SUDDEN_DEATH -> "서든데스";
            default -> session.state().name(); }, "경기 시간 " + io.github.augmentwild.domain.CombatView.time(elapsed),
            "생존 " + session.participantIds().stream().filter(p -> session.player(p).life().status()!=LifeState.Status.ELIMINATED).count() + "/" + entrants.size(),
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
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            recover(player);
            art.offer(player);
        }
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
                if (session.state() == MatchState.PREPARING) interruptPreparation("비참가자가 실험 월드에 접속했습니다.");
                else clearMatch();
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

    @EventHandler public void worldChange(org.bukkit.event.player.PlayerChangedWorldEvent e) {
        art.forget(e.getPlayer().getUniqueId());
        displays.detach(e.getPlayer().getUniqueId());
        overhead.detach(e.getPlayer().getUniqueId());
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        displays.detach(id);
        overhead.detach(id);
        if (!contains(id)) return;
        supply.disconnect(id);
        intel.remove(id);
        hud.clear(id, event.getPlayer());
        if (borderDisplay != null) borderDisplay.detach(event.getPlayer());
        if (session.state() == MatchState.PREPARING) {
            waitingSignups.remove(id);
            interruptPreparation("참가자가 경기 준비 중 접속을 종료했습니다.");
        } else if (task == null) {
            waitingSignups.remove(id);
            session.leave(id);
        }
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
            creditedKillers.put(id, killer != null && contains(killer.getUniqueId()) && !departures.contains(killer.getUniqueId())
                    ? killer.getUniqueId() : null);
            art.forget(id);
            displays.detach(id);
            overhead.detach(id);
            var source = event.getDamageSource().getCausingEntity();
            String cause = source instanceof Player attacker && contains(attacker.getUniqueId())
                    ? attacker.getName() + " ⚔ " : source == null ? "환경 피해 · " : "생물 또는 기타 피해 · ";
            var last = event.getEntity().getLastDamageCause();
            var trace = last == null ? null : augments.trace(last);
            if (trace != null && trace.synthetic() && trace.augment() != null) augmentKills.add(id);
            causes.put(id,cause + event.getEntity().getName() + (trace != null && trace.synthetic() && trace.augment()!=null
                    ? " · " + augments.effectName(trace.augment()) : ""));
            feedback.settle(id);
            awaitingNativeRespawn.add(id);
            supply.disconnect(id);
            intel.death(event.getEntity(), event.getDamageSource());
            var journalKiller = creditedKillers.get(id);
            var damage = event.getDamageSource().getDamageType().getKey().toString();
            hud.record(event.getEntity(),hud.event(journalKiller,id,null,io.github.augmentwild.domain.CombatEvent.Kind.DEATH,damage,
                    null,null,null,Double.NaN,Double.NaN,Double.NaN,Double.NaN,0,0,0,0,false,true,false,false,false),"사망 · " + damage);
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
