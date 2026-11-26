package io.github.augmentwild.paper;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Logger;

/** Private result snapshots. This archive has no authority over recovery, rewards or game rules. */
final class MatchArchive implements AutoCloseable {
    private static final int LIMIT = 64, INDEX_LIMIT = 4096;
    private static final long MAX_BYTES = 512_000;
    record PlayerResult(UUID id, int rank, int kills, int deaths, int lives, long survival,
                        double dealt, double received, double healed, double shield, int level,
                        boolean levelCurrent, int casts, int supplies, List<String> augments,
                        List<String> attained) {
        PlayerResult {
            Objects.requireNonNull(id);
            augments = List.copyOf(augments);
            attained = List.copyOf(attained);
            if (rank < 0 || kills < 0 || deaths < 0 || lives < -1 || survival < 0 || level < 0
                    || casts < 0 || supplies < 0 || !points(dealt) || !points(received) || !points(healed) || !points(shield))
                throw new IllegalArgumentException("Invalid participant result");
        }
        private static boolean points(double value) { return Double.isFinite(value) && value >= 0; }
    }
    enum Mode { NORMAL, PRACTICE, LAB }
    enum Outcome { WIN, DRAW, CANCELLED }
    record Result(UUID id, String plugin, String rules, Instant started, Instant ended,
                  Mode mode, Outcome outcome, UUID winner, long elapsed, Map<UUID,PlayerResult> players,
                  Map<String,Long> supplyEvents) {
        Result {
            players = Map.copyOf(players);
            supplyEvents = Map.copyOf(supplyEvents);
            if (players.isEmpty() || elapsed < 0 || ended.isBefore(started)
                    || (outcome == Outcome.WIN) != (winner != null) || winner != null && !players.containsKey(winner)
                    || players.entrySet().stream().anyMatch(e -> !e.getKey().equals(e.getValue().id())))
                throw new IllegalArgumentException("Invalid match result");
        }
    }
    record Entry(UUID id, Instant ended, Mode mode, Outcome outcome, int players, int rank) {}
    private record Header(UUID id, Instant ended, Mode mode, Outcome outcome, Map<UUID,Integer> ranks) {
        Header(Result result) {
            this(result.id(), result.ended(), result.mode(), result.outcome(), result.players().values().stream()
                    .collect(java.util.stream.Collectors.toUnmodifiableMap(PlayerResult::id,PlayerResult::rank)));
        }
    }
    private final Path directory;
    private final int retentionDays;
    private final Logger logger;
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(LIMIT),Thread.ofPlatform().name("AugmentWild match archive").factory());
    private final Map<UUID,Result> pending = new LinkedHashMap<>();
    private final Map<UUID,Header> index = new ConcurrentHashMap<>();
    private volatile String status = "CHECKING";
    private volatile long writes, diskNanos;
    private volatile Instant checked;
    private volatile int isolated;

    MatchArchive(Path directory, int retentionDays, Logger logger) {
        this.directory = directory;
        this.retentionDays = retentionDays;
        this.logger = logger;
        worker.execute(this::load);
    }

    synchronized boolean append(Result match) {
        if (index.containsKey(match.id()) || pending.containsKey(match.id())) return true;
        if (pending.size() >= LIMIT) {
            status = "FAIL · 저장 대기열 포화";
            logger.severe("Match archive rejected result " + match.id());
            return false;
        }
        pending.put(match.id(),match);
        schedule(() -> save(match));
        return true;
    }

    private boolean schedule(Runnable task) {
        try { worker.execute(task); return true; }
        catch (RejectedExecutionException full) { status = "WARN · 작업 대기열 포화 또는 종료"; return false; }
    }

    synchronized String retry() {
        var failed = List.copyOf(pending.values());
        return schedule(() -> {
            load();
            failed.forEach(this::save);
        }) ? "경기 기록을 재검사하고 미저장 결과를 다시 시도합니다." : status();
    }

    String status() {
        synchronized (this) {
            return status + " · 검사 " + (checked == null ? "미완료" : checked) + " · 미저장 " + pending.size()
                    + " · 대기 " + worker.getQueue().size() + " · 격리 " + isolated + " · 저장 " + writes
                    + " · 최근 기록 ms " + String.format(Locale.ROOT,"%.2f",diskNanos/1e6);
        }
    }

    List<Entry> list(UUID owner, int page, int pageSize) {
        return index.values().stream().filter(row -> row.ranks.containsKey(owner) && !expired(row.ended))
                .sorted(Comparator.comparing(Header::ended).reversed().thenComparing(Header::id))
                .skip((long)page * pageSize).limit(pageSize)
                .map(row -> new Entry(row.id,row.ended,row.mode,row.outcome,row.ranks.size(),row.ranks.get(owner))).toList();
    }

    boolean lookup(UUID match, UUID viewer, boolean admin, Consumer<Result> callback) {
        return schedule(() -> {
            Result result = null;
            try {
                var file = path(match);
                if (Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)) {
                    var saved = read(file);
                    if (!expired(saved.ended()) && (admin || saved.players().containsKey(viewer))) result = saved;
                }
            } catch (IOException | RuntimeException failure) {
                warn("경기 조회 실패 " + match,failure);
            }
            callback.accept(result);
        });
    }

    private Path path(UUID id) { return directory.resolve(id + ".tsv"); }
    private boolean expired(Instant ended) { return ended.isBefore(Instant.now().minus(retentionDays,ChronoUnit.DAYS)); }

    private void save(Result result) {
        synchronized (this) { if (!pending.containsKey(result.id())) return; }
        long started = System.nanoTime();
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                Files.createDirectories(directory);
                Path file = path(result.id());
                if (!Files.exists(file,LinkOption.NOFOLLOW_LINKS)) AugmentHistory.write(file,encode(result));
                Result saved = read(file);
                addIndex(new Header(saved));
                for (var row : index.values()) {
                    if (expired(row.ended)) {
                        Files.deleteIfExists(path(row.id));
                        index.remove(row.id,row);
                    }
                }
                synchronized (this) { pending.remove(result.id()); }
                writes++;
                diskNanos = System.nanoTime()-started;
                checked = Instant.now();
                status = isolated == 0 ? "PASS" : "WARN · 손상 파일 격리";
                return;
            } catch (IOException | RuntimeException failure) {
                if (attempt == 1) warn("경기 결과 저장 실패 " + result.id(),failure);
            }
        }
    }

    private void addIndex(Header row) {
        index.putIfAbsent(row.id,row);
        if (index.size() > INDEX_LIMIT) index.values().stream().min(Comparator.comparing(Header::ended).thenComparing(Header::id))
                .ifPresent(oldest -> index.remove(oldest.id,oldest));
    }

    private void load() {
        try {
            Files.createDirectories(directory);
            boolean damaged = false;
            try (var files = Files.newDirectoryStream(directory,"*.tsv")) {
                for (var path : files) {
                    Result result;
                    try { result = read(path); }
                    catch (IOException | RuntimeException failure) {
                        damaged = true;
                        var quarantine = directory.resolve("quarantine");
                        Files.createDirectories(quarantine);
                        Files.move(path,quarantine.resolve(UUID.randomUUID()+"-"+path.getFileName()),StandardCopyOption.ATOMIC_MOVE);
                        isolated++;
                        warn("손상된 경기 파일을 격리했습니다 " + path.getFileName(),failure);
                        continue;
                    }
                    if (expired(result.ended())) {
                        Files.deleteIfExists(path);
                        index.remove(result.id());
                    } else addIndex(new Header(result));
                }
            }
            synchronized (this) {
                status = pending.isEmpty() && !damaged && isolated == 0 ? "PASS" : "WARN · 미저장 또는 격리 기록";
            }
        } catch (IOException | RuntimeException failure) { warn("경기 기록 확인 실패",failure); }
        finally { checked = Instant.now(); }
    }

    private void warn(String message, Exception failure) {
        status = "WARN · " + message;
        logger.log(java.util.logging.Level.WARNING,message,failure);
    }

    private static List<String> encode(Result r) {
        var lines = new ArrayList<String>();
        lines.add(String.join("\t","1",r.id.toString(),text(r.plugin),text(r.rules),r.started.toString(),
                r.ended.toString(),r.mode.name(),r.outcome.name(),r.winner == null ? "-" : r.winner.toString(),""+r.elapsed));
        r.players.values().stream().sorted(Comparator.comparing(PlayerResult::id)).forEach(p -> lines.add(String.join("\t",
                "p",p.id.toString(),""+p.rank,""+p.kills,""+p.deaths,""+p.lives,""+p.survival,""+p.dealt,
                ""+p.received,""+p.healed,""+p.shield,""+p.level,""+p.levelCurrent,""+p.casts,""+p.supplies,
                text(String.join("\n",p.augments)),text(String.join("\n",p.attained)))));
        new TreeMap<>(r.supplyEvents).forEach((key,value) -> lines.add("s\t"+key+"\t"+value));
        return lines;
    }
    private static String text(String value) { return Base64.getUrlEncoder().encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    private static String decode(String value) { return new String(Base64.getUrlDecoder().decode(value),java.nio.charset.StandardCharsets.UTF_8); }
    private static List<String> list(String value) { return decode(value).lines().toList(); }

    private static Result read(Path file) throws IOException {
        if (!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) || Files.size(file)>MAX_BYTES)
            throw new IOException("Invalid or oversized match file");
        var lines = Files.readAllLines(file);
        if (lines.isEmpty()) throw new IOException("Missing match header");
        var h = lines.getFirst().split("\t",-1);
        if (h.length != 10 || !h[0].equals("1")) throw new IOException("Unsupported match schema");
        UUID id = UUID.fromString(h[1]);
        if (!file.getFileName().toString().equals(id+".tsv")) throw new IOException("Match filename mismatch");
        var players = new LinkedHashMap<UUID,PlayerResult>();
        var supply = new TreeMap<String,Long>();
        for (int i=1; i<lines.size(); i++) {
            var p = lines.get(i).split("\t",-1);
            if (p.length == 3 && p[0].equals("s") && p[1].matches("[a-z0-9_.-]+") && Long.parseLong(p[2]) >= 0
                    && supply.putIfAbsent(p[1],Long.parseLong(p[2])) == null) continue;
            if (p.length != 17 || !p[0].equals("p") || !Set.of("true","false").contains(p[12]))
                throw new IOException("Invalid match row");
            UUID participant = UUID.fromString(p[1]);
            var row = new PlayerResult(participant,Integer.parseInt(p[2]),Integer.parseInt(p[3]),Integer.parseInt(p[4]),
                    Integer.parseInt(p[5]),Long.parseLong(p[6]),Double.parseDouble(p[7]),Double.parseDouble(p[8]),
                    Double.parseDouble(p[9]),Double.parseDouble(p[10]),Integer.parseInt(p[11]),Boolean.parseBoolean(p[12]),
                    Integer.parseInt(p[13]),Integer.parseInt(p[14]),list(p[15]),list(p[16]));
            if (players.putIfAbsent(participant,row) != null) throw new IOException("Duplicate player result");
        }
        return new Result(id,decode(h[2]),decode(h[3]),Instant.parse(h[4]),Instant.parse(h[5]),
                Mode.valueOf(h[6]),Outcome.valueOf(h[7]),h[8].equals("-") ? null : UUID.fromString(h[8]),
                Long.parseLong(h[9]),players,supply);
    }

    @Override public void close() {
        worker.shutdown();
        try { worker.awaitTermination(400,TimeUnit.MILLISECONDS); }
        catch (InterruptedException stopped) { Thread.currentThread().interrupt(); }
        synchronized (this) {
            if (!worker.isTerminated() || !pending.isEmpty())
                logger.warning("Match archive shutdown: queued="+worker.getQueue().size()+" unsaved="+pending.keySet());
        }
    }
}
