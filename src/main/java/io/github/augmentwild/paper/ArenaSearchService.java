package io.github.augmentwild.paper;

import io.github.augmentwild.domain.Arena;
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
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Read-only ZERO-policy preview of existing terrain. All block access runs on the server thread. */
public final class ArenaSearchService {
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
                               int surfaceStep, Instant verifiedAt, List<Location> spawns) {
        public ArenaContext {
            spawns = spawns.stream().map(Location::clone).toList();
        }

        @Override public List<Location> spawns() {
            return spawns.stream().map(Location::clone).toList();
        }
    }

    public String preview(CommandSender sender, int players) {
        return begin(sender, players, null, result -> { });
    }

    public String search(CommandSender sender, int players, UUID matchId, Consumer<ArenaContext> done) {
        return begin(sender, players, matchId, done);
    }

    public boolean busy() {
        return task != null || abandoned.stream().anyMatch(request -> !request.isDone());
    }

    private String begin(CommandSender sender, int players, UUID matchId, Consumer<ArenaContext> done) {
        if (busy()) return "경기장 탐색 또는 청크 요청 정리가 진행 중입니다.";
        abandoned = List.of();
        var cfg = plugin.getConfig();
        World world = plugin.getServer().getWorld(cfg.getString("arena.world", ""));
        if (world == null || world.getEnvironment() != World.Environment.NORMAL) return "기존 오버월드를 arena.world에 지정하세요.";
        if (matchId != null && !world.getPVP()) return "경기 월드에서 PvP를 허용해야 합니다.";
        if (!"ZERO".equalsIgnoreCase(cfg.getString("arena.biome.ocean-policy"))) return "ZERO 검사 정책을 지정하세요.";
        try {
            scan = new Scan(sender, world, players, matchId, done);
        } catch (IllegalArgumentException | org.bukkit.configuration.InvalidConfigurationException | java.io.IOException failure) {
            return "경기장 탐색 설정 또는 기록을 읽지 못했습니다. " + failure.getMessage();
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        return "경기장 탐색을 시작했습니다.";
    }

    public boolean running() { return task != null; }

    public void cancel() {
        if (task != null) task.cancel();
        task = null;
        if (scan != null) abandoned = List.copyOf(scan.pending.values());
        scan = null;
    }

    private void tick() {
        Scan current = scan;
        try {
            if (!current.advance()) return;
        } catch (RuntimeException failure) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Arena search failed", failure);
            current.sender.sendMessage("경기장 탐색 오류가 발생했습니다. 서버 로그를 확인하세요.");
        }
        cancel();
        current.done.accept(current.result);
    }

    private final class Scan {
        private final CommandSender sender;
        private final World world;
        private final int players;
        private final UUID matchId;
        private final Consumer<ArenaContext> done;
        private final boolean generate;
        private final int progressTicks, spawnSamplesPerChunk, spawnPoolSize;
        private int ticks;
        private ArenaContext result;
        private final int width, height, radius, attempts, stride, pipeline, spacing, margin;
        private final double historyBuffer;
        private final List<Arena> occupied = new ArrayList<>();
        private final Map<Cell, CompletableFuture<Chunk>> pending = new LinkedHashMap<>();
        private final List<Location> spawns = new ArrayList<>();
        private final Map<String,Integer> rejected = new LinkedHashMap<>();
        private final long started = System.nanoTime();
        private final long timeout;
        private final int originX, originZ;
        private List<Cell> cells = List.of();
        private int next, attempt, minX, maxX, minZ, maxZ, samples, safeColumns;

        Scan(CommandSender sender, World world, int players, UUID matchId, Consumer<ArenaContext> done)
                throws org.bukkit.configuration.InvalidConfigurationException, java.io.IOException {
            this.sender = sender;
            this.world = world;
            this.players = players;
            this.matchId = matchId;
            this.done = done;
            var cfg = plugin.getConfig();
            width = cfg.getInt("arena.width");
            height = cfg.getInt("arena.height");
            int limit = Math.min(5000, cfg.getInt("arena.maximum-size"));
            radius = cfg.getInt("arena.search.radius");
            attempts = cfg.getInt("arena.search.max-candidate-attempts");
            generate = matchId != null && cfg.getBoolean("arena.search.generate-missing-chunks");
            stride = cfg.getInt(matchId == null ? "arena.search.surface-sample-step" : "arena.search.commit-surface-step");
            progressTicks = cfg.getInt("arena.search.progress-ticks");
            spawnSamplesPerChunk = cfg.getInt("arena.search.spawn-probes-per-chunk");
            spawnPoolSize = cfg.getInt("arena.search.spawn-pool-size");
            pipeline = cfg.getInt("arena.search.chunks-per-tick");
            spacing = cfg.getInt("arena.spawn.min-player-distance");
            margin = cfg.getInt("arena.spawn.border-margin");
            historyBuffer = cfg.getDouble("arena.search.used-region-buffer-blocks");
            int seconds = cfg.getInt("arena.search.max-search-seconds");
            if (width < 1 || height < 1 || width > limit || height > limit || radius < 1 || radius > 1_000_000
                    || attempts < 1 || progressTicks < 1 || spawnSamplesPerChunk < 1 || spawnSamplesPerChunk > 16
                    || spawnPoolSize < players || spawnPoolSize > 4096 || stride < 1 || stride > 16 || pipeline < 1 || pipeline > 8
                    || spacing < 0 || margin < 1 || 2L * margin >= Math.min(width, height)
                    || !Double.isFinite(historyBuffer) || historyBuffer < 0 || seconds < 1) {
                throw new IllegalArgumentException("arena 설정 범위를 확인하세요.");
            }
            timeout = TimeUnit.SECONDS.toNanos(seconds);
            var origin = world.getSpawnLocation();
            originX = origin.getBlockX();
            originZ = origin.getBlockZ();
            var historyFile = new java.io.File(plugin.getDataFolder(), "used-regions.yml");
            if (historyFile.isFile()) {
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

        boolean advance() {
            if (System.nanoTime() - started >= timeout || (cells.isEmpty() && attempt >= attempts)) {
                sender.sendMessage("경기장 탐색 실패. 후보 " + attempt + "개, 거부 사유 " + rejected);
                return true;
            }
            if (cells.isEmpty()) {
                pending.values().removeIf(CompletableFuture::isDone);
                if (!pending.isEmpty()) return false;
                prepareCandidate();
                return false;
            }
            if (++ticks % progressTicks == 0) {
                sender.sendMessage("경기장 후보 " + attempt + " | 검사 청크 "
                        + (next - pending.size()) + "/" + cells.size() + " | 표면 " + samples);
            }
            int handled = 0;
            var iterator = pending.entrySet().iterator();
            while (iterator.hasNext() && handled < pipeline) {
                var request = iterator.next();
                if (!request.getValue().isDone()) continue;
                handled++;
                Chunk chunk = request.getValue().join();
                iterator.remove();
                if (chunk == null || !world.isChunkLoaded(chunk.getX(), chunk.getZ())) {
                    reject("생성 또는 로드되지 않은 청크");
                    return false;
                }
                if (!inspect(chunk)) {
                    reject("바다 바이옴");
                    return false;
                }
            }
            if (next == cells.size() && pending.isEmpty()) {
                var spaced = spacedSpawns();
                if (spaced.size() < players) {
                    reject("안전 스폰 부족");
                    return false;
                }
                sender.sendMessage("경기장 후보 중심 " + ((minX + maxX + 1.0) / 2) + ", " + ((minZ + maxZ + 1.0) / 2)
                        + " | " + width + "×" + height + " | 표면 표본 " + samples + "개, 간격 " + stride
                        + " | 안전 스폰 후보 " + spawns.size() + "개");

                result = new ArenaContext(matchId, world.getUID(),
                        new Arena(minX + width / 2.0, minZ + height / 2.0, width, height),
                        "ZERO", stride, Instant.now(), spaced);
                return true;
            }
            for (int issued = 0; issued < pipeline && next < cells.size() && pending.size() < pipeline; issued++) {
                var cell = cells.get(next++);
                pending.put(cell, world.getChunkAtAsync(cell.x(), cell.z(), generate));
            }
            return false;
        }

        void prepareCandidate() {
            attempt++;
            minX = originX + random.nextInt(-radius, radius + 1) - width / 2;
            minZ = originZ + random.nextInt(-radius, radius + 1) - height / 2;
            maxX = minX + width - 1;
            maxZ = minZ + height - 1;
            var border = world.getWorldBorder();
            var area = new Arena(minX + width / 2.0, minZ + height / 2.0, width, height);
            if (!border.isInside(new Location(world, minX, 0, minZ))
                    || !border.isInside(new Location(world, maxX + 1.0, 0, maxZ + 1.0))) {
                reject("월드 경계");
                return;
            }
            if (occupied.stream().anyMatch(previous -> area.overlaps(previous, historyBuffer))) {
                reject("사용 기록 중첩");
                return;
            }
            int firstX = minX >> 4, lastX = maxX >> 4, firstZ = minZ >> 4, lastZ = maxZ >> 4;
            int middleX = (firstX + lastX) >> 1, middleZ = (firstZ + lastZ) >> 1;
            // Examine center, corners and edge midpoints before the interior.
            var order = new LinkedHashSet<Cell>();
            order.add(new Cell(middleX, middleZ));
            for (int x : new int[]{firstX, middleX, lastX}) {
                for (int z : new int[]{firstZ, middleZ, lastZ}) order.add(new Cell(x, z));
            }
            var interior = new ArrayList<Cell>();
            for (int x = firstX; x <= lastX; x++) {
                for (int z = firstZ; z <= lastZ; z++) interior.add(new Cell(x, z));
            }
            Collections.shuffle(interior, random);
            order.addAll(interior);
            cells = List.copyOf(order);
        }

        boolean inspect(Chunk chunk) {
            int x0 = chunk.getX() << 4, z0 = chunk.getZ() << 4;
            for (int x = Math.max(x0, minX); x <= Math.min(x0 + 15, maxX); x++) {
                if ((x - minX) % stride != 0 && x != maxX) continue;
                for (int z = Math.max(z0, minZ); z <= Math.min(z0 + 15, maxZ); z++) {
                    if ((z - minZ) % stride != 0 && z != maxZ) continue;
                    int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                    samples++;
                    if (SafeArenaSurface.ocean(world, x, y, z)) return false;
                }
            }
            for (int i = 0; i < spawnSamplesPerChunk; i++) {
                int x = x0 + random.nextInt(1, 15), z = z0 + random.nextInt(1, 15);
                if (x - minX < margin || maxX - x < margin || z - minZ < margin || maxZ - z < margin) continue;
                Location point = SafeArenaSurface.at(world, x, z);
                if (point == null) continue;
                safeColumns++;
                if (spawns.size() < spawnPoolSize) spawns.add(point);
                else {
                    int slot = random.nextInt(safeColumns);
                    if (slot < spawnPoolSize) spawns.set(slot, point);
                }
                break;
            }
            return true;
        }

        private List<Location> spacedSpawns() {
            Collections.shuffle(spawns, random);
            var spaced = new ArrayList<Location>();
            for (var point : spawns) {
                if (spaced.stream().anyMatch(other -> other.equals(point) || Math.hypot(other.getX() - point.getX(),
                        other.getZ() - point.getZ()) < spacing)) continue;
                spaced.add(point);
                if (spaced.size() == players) break;
            }
            return spaced;
        }

        void reject(String reason) {
            rejected.merge(reason, 1, Integer::sum);
            // The outstanding batch remains bounded. Drain it before trying another candidate.
            cells = List.of();
            next = 0;
            samples = 0;
            safeColumns = 0;
            spawns.clear();
        }
    }
}
