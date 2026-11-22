package io.github.augmentwild.paper;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import io.github.augmentwild.domain.Arena;
import io.github.augmentwild.domain.ArenaSearchBounds;
import io.github.augmentwild.domain.OceanSampling;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import java.time.Instant;
import java.util.function.Consumer;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Capped asynchronous chunk requests with terrain inspection on the server thread. */
public final class ArenaSearchService implements Listener {
    private record Cell(int x, int z) { }
    private final JavaPlugin plugin;
    private final Random random = new Random();
    private List<CompletableFuture<Chunk>> abandoned = List.of();
    private Scan scan;
    private BukkitTask task;

    public ArenaSearchService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public record ArenaContext(UUID matchId, UUID worldId, Arena area, String policy,
                               int surfaceStep, double worldBorderMargin, Instant verifiedAt, List<Location> spawns) {
        public ArenaContext {
            spawns = spawns.stream().map(Location::clone).toList();
        }

        @Override public List<Location> spawns() {
            return spawns.stream().map(Location::clone).toList();
        }
    }

    public String preview(CommandSender sender, int players) {
        return begin(sender, players, null, plugin.getConfig().getString("arena.world", ""), false, result -> { });
    }

    public String search(CommandSender sender, int players, UUID matchId, String worldName, boolean lab, Consumer<ArenaContext> done) {
        return begin(sender, players, matchId, worldName, lab, done);
    }

    public boolean busy() {
        return task != null || abandoned.stream().anyMatch(request -> !request.isDone());
    }

    void drain(CompletableFuture<Chunk> request) {
        if (request == null || request.isDone()) return;
        var retained = new ArrayList<>(abandoned.stream().filter(pending -> !pending.isDone()).toList());
        retained.add(request);
        abandoned = retained;
    }

    public String check(int players, String worldName, boolean lab) {
        try {
            var plan = plan(players, worldName, lab, true);
            String progress = scan == null ? "없음" : scan.progress();
            return "운영 월드 " + plugin.getConfig().getString("arena.world")
                    + " · 실험 월드 " + plugin.getConfig().getString("dev.combat-lab.world")
                    + "\n" + plan.description() + "\n현재 검사 " + progress;
        } catch (IllegalArgumentException failure) {
            return failure.getMessage();
        }
    }

    private SearchPlan plan(int players, String name, boolean lab, boolean start) {
        World world = plugin.getServer().getWorld(name);
        if (world == null || world.getEnvironment() != World.Environment.NORMAL) {
            throw new IllegalArgumentException((lab ? "테스트" : "경기") + " 월드 '" + name
                    + "'를 기존 월드 관리 도구에서 로드하세요. 오버월드 이름을 확인하세요.");
        }
        if (start && !world.getPVP()) throw new IllegalArgumentException("경기 월드에서 PvP를 허용해야 합니다.");
        return new SearchPlan(world, players, lab, start);
    }

    private String begin(CommandSender sender,int players,UUID matchId,String worldName,boolean lab,Consumer<ArenaContext> done) {
        if (busy()) return "경기장 탐색 또는 청크 요청 정리가 진행 중입니다.";
        abandoned = List.of();
        try {
            scan = new Scan(sender, plan(players, worldName, lab, matchId != null), matchId, done);
        } catch (IllegalArgumentException failure) {
            return failure.getMessage();
        } catch (org.bukkit.configuration.InvalidConfigurationException | java.io.IOException failure) {
            return "경기장 사용 기록을 읽지 못했습니다. " + failure.getMessage();
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        return "경기장 탐색을 시작했습니다.\n" + scan.plan.description();
    }

    private static ArenaSearchBounds bounds(World world, int width, int height, double clearance) {
        var border = world.getWorldBorder();
        var center = border.getCenter();
        return ArenaSearchBounds.fit(center.getX(), center.getZ(), border.getSize(),
                border.getMaxCenterCoordinate(), width, height, clearance);
    }

    public static boolean validPlacement(World world, Arena area, double clearance) {
        var border = world.getWorldBorder();
        var bounds = bounds(world, (int) area.width(), (int) area.height(), clearance);
        return bounds.contains(new ArenaSearchBounds.Corner((int) area.minX(), (int) area.minZ()))
                && border.isInside(new Location(world, area.minX(), 0, area.minZ()))
                && border.isInside(new Location(world, Math.nextDown(area.maxX()), 0, Math.nextDown(area.maxZ())));
    }

    private final class SearchPlan {
        private final World world;
        private final int players, width, height, radius, attempts, spacing, margin, zeroStep, ratioStep, refineStep;
        private final boolean lab, generate, expand, zero;
        private final double maximumOcean, refineBand, clearance;
        private final ArenaSearchBounds fit, near;
        private final ArenaSearchBounds.Corner spawnCorner;

        SearchPlan(World world, int players, boolean lab, boolean start) {
            this.world = world;
            this.players = players;
            this.lab = lab;
            var cfg = plugin.getConfig();
            String labPrefix = "dev.combat-lab.";
            int labSize = lab ? cfg.getInt(labPrefix + "arena-size", 128) : 0;
            width = labSize == 0 ? cfg.getInt("arena.width") : labSize;
            height = labSize == 0 ? cfg.getInt("arena.height") : labSize;
            int limit = Math.min(5000, cfg.getInt("arena.maximum-size"));
            radius = lab ? cfg.getInt(labPrefix + "search-radius", 128) : cfg.getInt("arena.search.radius");
            attempts = cfg.getInt(lab ? labPrefix + "max-candidate-attempts" : "arena.search.max-candidate-attempts", 120);
            expand = cfg.getBoolean(lab ? labPrefix + "expand-within-world-border" : "arena.search.expand-within-world-border", !lab);
            generate = start && cfg.getBoolean(lab ? labPrefix + "generate-missing-chunks" : "arena.search.generate-missing-chunks");
            spacing = lab ? cfg.getInt(labPrefix + "min-player-distance", 40) : cfg.getInt("arena.spawn.min-player-distance");
            margin = lab ? cfg.getInt(labPrefix + "spawn-margin", 8) : cfg.getInt("arena.spawn.border-margin");
            clearance = cfg.getDouble("arena.search.world-border-margin", 0);
            String policy = cfg.getString("arena.biome.ocean-policy", "MAX_RATIO");
            zero = "ZERO".equalsIgnoreCase(policy);
            zeroStep = cfg.getInt(start ? "arena.search.commit-surface-step" : "arena.search.surface-sample-step");
            boolean ratio = "MAX_RATIO".equalsIgnoreCase(policy);
            maximumOcean = cfg.getDouble("arena.biome.maximum-ocean-ratio", 0.05);
            ratioStep = cfg.getInt("arena.biome.ratio-sample-step", 32);
            refineStep = cfg.getInt("arena.biome.ratio-refine-step", 8);
            refineBand = cfg.getDouble("arena.biome.ratio-refine-band", 0.02);
            if (players < 1 || players > 16 || width < 1 || height < 1 || width > limit || height > limit
                    || radius < 0 || radius > 1_000_000 || attempts < 1 || attempts > 10_000
                    || spacing < 0 || margin < 1 || 2L * margin >= Math.min(width, height)
                    || !Double.isFinite(clearance) || clearance < 0 || clearance > limit
                    || !zero && !ratio || zeroStep < 1 || zeroStep > 16
                    || !(maximumOcean >= 0 && maximumOcean <= 1 && refineBand >= 0 && refineBand <= 1)
                    || ratioStep < 2 || ratioStep > 64 || refineStep < 1 || refineStep >= ratioStep) {
                throw new IllegalArgumentException("경기장 크기·거리·바다 검사 설정 범위를 확인하세요.");
            }
            if (cfg.getDouble("border.final-size") > Math.min(width, height)) {
                throw new IllegalArgumentException("최종 자기장 크기가 경기장보다 큽니다. border.final-size 또는 경기장 크기를 확인하세요.");
            }
            fit = bounds(world, width, height, clearance);
            var origin = world.getSpawnLocation();
            near = fit.near(origin.getBlockX(), origin.getBlockZ(), width, height, radius);
            spawnCorner = new ArenaSearchBounds.Corner(origin.getBlockX() - width / 2, origin.getBlockZ() - height / 2);
            if (fit.empty() || near.empty() && !expand) throw new IllegalArgumentException(description()
                    + "\n경기장 배치 불가능. " + (fit.empty()
                    ? "경기장 전체와 경계 여유가 들어가는 정수 좌표가 없습니다. 최소 보더 폭 "
                        + (Math.max(width,height) + 2 * clearance) + "블록 및 보더 중심을 확인하세요."
                    : "스폰 주변 탐색 범위와 보더 내부 유효 범위가 겹치지 않습니다. 탐색 반경 또는 범위 확장 설정을 확인하세요."));
        }

        String description() {
            var border = world.getWorldBorder();
            var center = border.getCenter();
            return String.format(java.util.Locale.ROOT,
                    "%s 월드 %s · 월드보더 %.2f×%.2f (중심 %.2f, %.2f) · 경기장 %d×%d · 경계 여유 %.2f\n"
                    + "반경 %d · 스폰 최소 거리 %d · 스폰 경계 여유 %d · 바다 %s · 청크 생성 %s · 배치 목표 %d명\n"
                    + "배치 가능 중심 %s · 스폰 주변 %s · 범위 확장 %s",
                    lab ? "실험" : "운영", world.getName(), border.getSize(), border.getSize(), center.getX(), center.getZ(),
                    width, height, clearance, radius, spacing, margin, zero ? "ZERO" : String.format(java.util.Locale.ROOT,"MAX_RATIO %.2f%%",maximumOcean * 100),
                    generate ? "허용 (서버 부하 증가 가능)" : "금지", players, fit.centers(width,height),
                    near.centers(width,height), expand ? "허용" : "금지");
        }
    }

    public boolean running() { return task != null; }

    public void cancel() {
        if (task != null) task.cancel();
        task = null;
        if (scan != null) {
            scan.report("CANCELLED");
            scan.pending.values().forEach(this::drain);
        }
        scan = null;
    }

    @EventHandler
    public void observeTick(ServerTickEndEvent event) {
        if (scan != null) scan.maxTickMillis = Math.max(scan.maxTickMillis, event.getTickDuration());
    }

    private void tick() {
        Scan current = scan;
        if (!current.finished) {
            try {
                current.finished = current.advance();
            } catch (RuntimeException failure) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Arena search failed", failure);
                current.sender.sendMessage("경기장 탐색 오류가 발생했습니다. 서버 로그를 확인하세요.");
                current.rejected.merge("exception:" + failure.getClass().getSimpleName(), 1, Integer::sum);
                current.finished = true;
            }
            if (current.finished) current.finishedAt = System.nanoTime();
            return; // Include this tick's Paper end-of-tick duration before reporting.
        }
        current.report(current.result == null ? "FAILED" : "FOUND");
        cancel();
        current.done.accept(current.result);
    }

    private final class Scan {
        private final CommandSender sender;
        private final SearchPlan plan;
        private final World world;
        private final int players;
        private final UUID matchId;
        private final Consumer<ArenaContext> done;
        private final boolean generate,lab;
        private final int progressTicks, spawnSamplesPerChunk, spawnPoolSize;
        private int ticks;
        private boolean finished, reported;
        private long finishedAt;
        private long totalSamples, chunkRequests;
        private int peakPending;
        private double maxTickMillis;
        private long maxInspectionBurst;
        private final long inspectionBudget;
        private ArenaContext result;
        private final int width, height, attempts, pipeline, spacing, margin;
        private final ArenaSearchBounds.Candidates candidates = new ArenaSearchBounds.Candidates();
        private Arena area;
        private int stride;
        private int[] xWeights, zWeights;
        private long oceanWeight;
        private boolean refining;
        private final double historyBuffer;
        private final List<Arena> occupied = new ArrayList<>();
        private final Map<Cell, CompletableFuture<Chunk>> pending = new LinkedHashMap<>();
        private final LinkedHashSet<Cell> retried = new LinkedHashSet<>();
        private final List<Location> spawns = new ArrayList<>();
        private final Map<String,Integer> rejected = new LinkedHashMap<>();
        private final long started = System.nanoTime();
        private final long timeout;
        private List<Cell> cells = List.of();
        private int next, attempt, minX, maxX, minZ, maxZ, samples, safeColumns;

        Scan(CommandSender sender, SearchPlan plan, UUID matchId, Consumer<ArenaContext> done)
                throws org.bukkit.configuration.InvalidConfigurationException, java.io.IOException {
            this.sender = sender;
            this.plan = plan;
            this.world = plan.world;
            this.players = plan.players;
            this.matchId = matchId;
            this.done = done;
            this.lab = plan.lab;
            this.generate = plan.generate;
            width = plan.width;
            height = plan.height;
            attempts = plan.attempts;
            stride = plan.zero ? plan.zeroStep : plan.ratioStep;
            var cfg = plugin.getConfig();
            progressTicks = cfg.getInt("arena.search.progress-ticks");
            spawnSamplesPerChunk = cfg.getInt("arena.search.spawn-probes-per-chunk");
            spawnPoolSize = cfg.getInt("arena.search.spawn-pool-size");
            pipeline = cfg.getInt("arena.search.chunks-per-tick");
            int micros = cfg.getInt("arena.search.inspection-budget-micros",1500);
            inspectionBudget = TimeUnit.MICROSECONDS.toNanos(Math.clamp(micros,250,5000));
            spacing = plan.spacing;
            margin = plan.margin;
            historyBuffer = cfg.getDouble("arena.search.used-region-buffer-blocks");
            int seconds = cfg.getInt("arena.search.max-search-seconds");
            if (progressTicks < 1 || spawnSamplesPerChunk < 1 || spawnSamplesPerChunk > 16
                    || spawnPoolSize < players || spawnPoolSize > 4096 || pipeline < 1 || pipeline > 8
                    || !Double.isFinite(historyBuffer) || historyBuffer < 0 || seconds < 1) {
                throw new IllegalArgumentException("경기장 탐색 파이프라인 설정 범위를 확인하세요.");
            }
            timeout = TimeUnit.SECONDS.toNanos(seconds);
            var historyFile = new java.io.File(plugin.getDataFolder(), "used-regions.yml");
            if (!lab && historyFile.isFile()) {
                var history = new YamlConfiguration();
                history.load(historyFile);
                for (var entry : history.getMapList("regions")) {
                    if (!world.getUID().toString().equals(entry.get("world-id"))) continue;
                    occupied.add(new Arena(number(entry,"center-x"), number(entry,"center-z"),
                            number(entry,"width"), number(entry,"height")));
                }
            }
        }

        private double number(Map<?,?> entry, String key) {
            if (entry.get(key) instanceof Number number) return number.doubleValue();
            throw new IllegalArgumentException("used-regions.yml: " + key);
        }

        private String progress() {
            return "후보 " + attempt + "/" + attempts + " · " + (cells.isEmpty() ? "후보 선정"
                    : (plan.zero || refining ? "정밀 지형 검사" : "층화 표본 검사")
                    + " · 청크 " + (next - pending.size()) + "/" + cells.size())
                    + " · 안전 스폰 " + (spawns.isEmpty() ? 0 : spacedSpawns(false).size()) + "/" + players
                    + " · 거부 " + rejected;
        }

        private boolean fail(String reason) {
            rejected.merge(reason, 1, Integer::sum);
            sender.sendMessage("경기장 탐색 실패. 후보 " + attempt + "개, 거부 사유 " + rejected);
            if (!generate && rejected.keySet().stream().anyMatch(key -> key.contains("청크"))) {
                sender.sendMessage("미생성 구역이라면 월드를 미리 생성하거나 "
                        + (lab ? "dev.combat-lab" : "arena.search") + ".generate-missing-chunks를 설정하세요.");
            }
            return true;
        }

        boolean advance() {
            if (System.nanoTime() - started >= timeout) return fail("시간 제한");
            if (++ticks % progressTicks == 0) sender.sendMessage("경기장 준비 중 · 참가자 " + players + "명 · " + progress());
            if (cells.isEmpty()) {
                pending.values().removeIf(CompletableFuture::isDone);
                if (!pending.isEmpty()) return false;
                if (attempt >= attempts) return fail("후보 검사 한도");
                return prepareCandidate();
            }
            long inspectionStart = System.nanoTime();
            int handled = 0;
            var iterator = pending.entrySet().iterator();
            while (iterator.hasNext() && handled < pipeline) {
                var request = iterator.next();
                if (!request.getValue().isDone()) continue;
                handled++;
                if (request.getValue().isCompletedExceptionally() || request.getValue().isCancelled()) {
                    iterator.remove();
                    reject("청크 요청 실패");
                    return false;
                }
                Chunk chunk = request.getValue().join();
                Cell cell = request.getKey();
                iterator.remove();
                if (chunk == null) {
                    reject(generate ? "청크 로드 불가" : "미생성 또는 로드 불가 청크 (생성 금지)");
                    return false;
                }
                if (!world.isChunkLoaded(chunk.getX(),chunk.getZ())) {
                    if (!retried.add(cell)) {
                        reject("검사 전 청크 언로드");
                        return false;
                    }
                    pending.put(cell,world.getChunkAtAsync(cell.x(),cell.z(),generate));
                    chunkRequests++;
                    break;
                }
                boolean safe = inspect(chunk);
                long burst = System.nanoTime() - inspectionStart;
                maxInspectionBurst = Math.max(maxInspectionBurst,burst);
                if (!safe) {
                    reject(plan.zero ? "바다 바이옴" : "바다 비율 초과 (표본 추정)");
                    return false;
                }
                if (burst >= inspectionBudget) return false;
            }
            if (next == cells.size() && pending.isEmpty()) {
                var spaced = spacedSpawns(true);
                double estimate = (double) oceanWeight / ((long) width * height);
                if (!plan.zero && !refining
                        && (OceanSampling.refine(estimate,plan.maximumOcean,plan.refineBand)
                            || estimate <= plan.maximumOcean && spaced.size() < players)) {
                    refining = true;
                    schedulePass(plan.refineStep);
                    return false;
                }
                if (!plan.zero && OceanSampling.exceeds(oceanWeight, (long) width * height, plan.maximumOcean)) {
                    reject("바다 비율 초과 (표본 추정)");
                    return false;
                }
                if (spaced.size() < players) {
                    reject("안전 지표 또는 배치 간격 부족");
                    return false;
                }
                if (!validPlacement(world,area,plan.clearance)) return fail("탐색 중 월드보더 변경");
                String estimateMessage = plan.zero ? "ZERO" : String.format(java.util.Locale.ROOT,
                        "바다 비율 추정 %.2f%% (층화 표본)",estimate * 100);
                sender.sendMessage("경기장 후보 중심 " + area.x() + ", " + area.z() + " | " + width + "×" + height
                        + " | 표면 표본 " + samples + "개, 간격 " + stride + " | " + estimateMessage
                        + " | 안전 스폰 " + spaced.size() + "/" + players);
                result = new ArenaContext(matchId, world.getUID(), area, plan.zero ? "ZERO" : "MAX_RATIO",
                        stride, plan.clearance, Instant.now(), spaced);
                return true;
            }
            for (int issued = 0; issued < pipeline && next < cells.size() && pending.size() < pipeline; issued++) {
                var cell = cells.get(next++);
                pending.put(cell, world.getChunkAtAsync(cell.x(),cell.z(),generate));
                chunkRequests++;
                peakPending = Math.max(peakPending, pending.size());
            }
            return false;
        }

        boolean prepareCandidate() {
            if (!plan.fit.equals(bounds(world,width,height,plan.clearance))) return fail("탐색 중 월드보더 변경");
            boolean nearFirst = !plan.expand || attempt < Math.min(8, (attempts + 1) / 2);
            var preferred = lab || plan.radius == 0 ? plan.spawnCorner : null;
            var corner = nearFirst ? candidates.take(plan.near, preferred, random) : null;
            if (corner == null && plan.expand) {
                var closest = new ArenaSearchBounds.Corner(
                        Math.clamp(plan.spawnCorner.x(),plan.fit.minX(),plan.fit.maxX()),
                        Math.clamp(plan.spawnCorner.z(),plan.fit.minZ(),plan.fit.maxZ()));
                corner = candidates.take(plan.fit, closest, random);
            }
            if (corner == null) return fail("유효 좌표 소진");
            attempt++;
            minX = corner.x();
            minZ = corner.z();
            maxX = minX + width - 1;
            maxZ = minZ + height - 1;
            area = new Arena(minX + width / 2.0, minZ + height / 2.0, width, height);
            if (!validPlacement(world,area,plan.clearance)) return fail("Paper 월드보더 포함 검사 실패");
            if (occupied.stream().anyMatch(previous -> area.overlaps(previous, historyBuffer))) {
                reject("사용 기록 중첩");
                return false;
            }
            refining = false;
            schedulePass(plan.zero ? plan.zeroStep : plan.ratioStep);
            return false;
        }

        private void schedulePass(int step) {
            stride = step;
            samples = 0;
            oceanWeight = 0;
            next = 0;
            retried.clear();
            if (!plan.zero) {
                xWeights = OceanSampling.axisWeights(width, step);
                zWeights = OceanSampling.axisWeights(height, step);
            }
            int firstX = minX >> 4, lastX = maxX >> 4, firstZ = minZ >> 4, lastZ = maxZ >> 4;
            int middleX = (firstX + lastX) >> 1, middleZ = (firstZ + lastZ) >> 1;
            // Prioritize the center and outer rows where the selected strata include them.
            var order = new LinkedHashSet<Cell>();
            for (int x : new int[]{middleX, firstX, lastX}) {
                for (int z : new int[]{middleZ, firstZ, lastZ}) {
                    if (inPass(x,z)) order.add(new Cell(x,z));
                }
            }
            var interior = new ArrayList<Cell>();
            for (int x = firstX; x <= lastX; x++) {
                for (int z = firstZ; z <= lastZ; z++) {
                    if (inPass(x,z)) interior.add(new Cell(x,z));
                }
            }
            Collections.shuffle(interior, random);
            order.addAll(interior);
            cells = List.copyOf(order);
        }

        private boolean inPass(int chunkX,int chunkZ) {
            return plan.zero || sampledAxis(chunkX,minX,maxX,xWeights) && sampledAxis(chunkZ,minZ,maxZ,zWeights);
        }

        private boolean sampledAxis(int chunk,int min,int max,int[] weights) {
            for (int pos = Math.max(chunk << 4,min); pos <= Math.min((chunk << 4) + 15,max); pos++) {
                if (weights[pos - min] > 0) return true;
            }
            return false;
        }

        boolean inspect(Chunk chunk) {
            int x0 = chunk.getX() << 4, z0 = chunk.getZ() << 4;
            for (int x = Math.max(x0, minX); x <= Math.min(x0 + 15, maxX); x++) {
                int weightX = plan.zero ? ((x - minX) % stride == 0 || x == maxX ? 1 : 0) : xWeights[x - minX];
                if (weightX == 0) continue;
                for (int z = Math.max(z0, minZ); z <= Math.min(z0 + 15, maxZ); z++) {
                    int weightZ = plan.zero ? ((z - minZ) % stride == 0 || z == maxZ ? 1 : 0) : zWeights[z - minZ];
                    if (weightZ == 0) continue;
                    int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                    samples++;
                    totalSamples++;
                    if (!SafeArenaSurface.ocean(world, x, y, z)) continue;
                    if (plan.zero) return false;
                    oceanWeight += (long) weightX * weightZ;
                    double upper = refining ? plan.maximumOcean : plan.maximumOcean + plan.refineBand;
                    if (OceanSampling.exceeds(oceanWeight, (long) width * height, upper)) return false;
                }
            }
            for (int i = 0; i < spawnSamplesPerChunk; i++) {
                int x = x0 + random.nextInt(1, 15), z = z0 + random.nextInt(1, 15);
                if (x - minX < margin || maxX - x < margin || z - minZ < margin || maxZ - z < margin) continue;
                Location point = SafeArenaSurface.spawnAt(world, x, z);
                if (point == null) continue;
                safeColumns++;
                if (spawns.size() < spawnPoolSize) spawns.add(point);
                else {
                    int slot = random.nextInt(safeColumns);
                    if (slot < spawnPoolSize) spawns.set(slot, point);
                }
                // Give a small lab or a border-aligned rectangle more than one possible spawn per chunk.
                if (spawns.size() >= players * 4) break;
            }
            return true;
        }

        private List<Location> spacedSpawns(boolean shuffle) {
            if (shuffle) Collections.shuffle(spawns, random);
            var spaced = new ArrayList<Location>();
            for (var point : spawns) {
                if (spaced.stream().anyMatch(other -> other.getBlockX() == point.getBlockX() && other.getBlockZ() == point.getBlockZ() || Math.hypot(other.getX() - point.getX(),
                        other.getZ() - point.getZ()) < spacing)) continue;
                spaced.add(point);
                if (spaced.size() == players) break;
            }
            return spaced;
        }

        void report(String outcome) {
            if (reported) return;
            reported = true;
            long elapsed = (finished ? finishedAt : System.nanoTime()) - started;
            plugin.getLogger().info(String.format(java.util.Locale.ROOT,
                    "Arena search %s: size=%dx%d step=%d candidates=%d examined=%d finalSamples=%d "
                            + "chunkRequests=%d peakPending=%d elapsedMs=%d maxTickMs=%.2f maxTickOverrunMs=%.2f maxInspectionBurstMs=%.2f policy=%s spawns=%d/%d rejected=%s",
                    outcome, width, height, stride, attempt, totalSamples, result == null ? 0 : samples,
                    chunkRequests, peakPending, TimeUnit.NANOSECONDS.toMillis(elapsed), maxTickMillis,
                    Math.max(0, maxTickMillis - 50),maxInspectionBurst / 1_000_000.0,
                    plan.zero ? "ZERO" : "MAX_RATIO",result == null ? spacedSpawns(false).size() : players,players,rejected));
        }

        void reject(String reason) {
            rejected.merge(reason, 1, Integer::sum);
            // The outstanding batch remains bounded. Drain it before trying another candidate.
            cells = List.of();
            retried.clear();
            next = 0;
            samples = 0;
            safeColumns = 0;
            spawns.clear();
        }
    }
}
