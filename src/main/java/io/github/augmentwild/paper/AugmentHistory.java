package io.github.augmentwild.paper;

import io.github.augmentwild.domain.AugmentTelemetry.Metric;
import io.github.augmentwild.domain.AugmentTelemetry.Snapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/** One atomic file per completed match. Workers only receive immutable values. */
final class AugmentHistory implements AutoCloseable {
    private static final int SCHEMA = 1;
    private static final String VERSION = "8.2";
    static final String CURRENT = "8.2.1";
    private static final int LIMIT = 4096;
    record Filter(String version,int participants,int days,int round) {
        Filter {
            if (!version.matches("[0-9]+(?:\\.[0-9]+){1,2}") || participants < 0 || participants == 1
                    || days < 0 || days > 3650 || round < 0 || round > 1000) throw new IllegalArgumentException();
        }
        static Filter parse(String[] arguments,int offset) {
            String version = CURRENT;
            int players = 0, days = 0, round = 0;
            var seen = new java.util.HashSet<String>();
            for (int index=offset;index<arguments.length;index++) {
                String[] word = arguments[index].split("=",2);
                String key = word.length == 1 ? "version" : word[0];
                String value = word.length == 1 ? word[0] : word[1];
                if (!seen.add(key)) throw new IllegalArgumentException();
                switch (key) {
                    case "version" -> version = value;
                    case "players" -> players = Integer.parseInt(value);
                    case "days" -> days = Integer.parseInt(value);
                    case "round" -> round = Integer.parseInt(value);
                    default -> throw new IllegalArgumentException();
                }
            }
            return new Filter(version,players,days,round);
        }
    }
    private record Match(UUID id,String version,Instant ended,int participants,Snapshot stats) {}
    private final Path directory;
    private final Logger logger;
    private final int retentionDays;
    private final Map<UUID,Match> matches = new ConcurrentHashMap<>();
    private final java.util.concurrent.ThreadPoolExecutor disk = new java.util.concurrent.ThreadPoolExecutor(
            1,1,0,java.util.concurrent.TimeUnit.SECONDS,new java.util.concurrent.ArrayBlockingQueue<>(64),
            Thread.ofPlatform().name("AugmentWild analytics").factory());
    private volatile String state = "CHECKING";
    private final Map<UUID,Match> failed = new ConcurrentHashMap<>();

    boolean async(Runnable action) {
        try { disk.execute(action); return true; }
        catch (java.util.concurrent.RejectedExecutionException full) { state = "WARN · 통계 대기열 포화"; return false; }
    }

    String retry() {
        return async(() -> {
            load();
            List.copyOf(failed.values()).forEach(this::store);
        }) ? "통계 저장을 재시도합니다." : status();
    }

    AugmentHistory(Path directory,Logger logger,int retentionDays) {
        this.directory = directory;
        this.logger = logger;
        this.retentionDays = retentionDays;
        disk.execute(this::load);
    }

    java.util.concurrent.Future<?> append(UUID id,int participants,Snapshot stats) {
        return append(id,participants,stats,VERSION);
    }

    java.util.concurrent.Future<?> append(UUID id,int participants,Snapshot stats,String version) {
        var match = new Match(id,version,Instant.now(),participants,stats);
        var complete = new java.util.concurrent.CompletableFuture<Void>();
        if (!async(() -> {
            store(match);
            complete.complete(null);
        })) {
            retainFailed(match);
            complete.completeExceptionally(new IllegalStateException(status()));
        }
        return complete;
    }

    private void retainFailed(Match match) {
        if (failed.size() < 64) failed.putIfAbsent(match.id(),match);
        else logger.severe("Analytics retry buffer full for " + match.id());
    }

    private void store(Match match) {
        try {
            Files.createDirectories(directory);
            Path file = directory.resolve(match.id()+".tsv");
            if (!Files.exists(file,java.nio.file.LinkOption.NOFOLLOW_LINKS)) write(file,encode(match));
            add(read(file));
            failed.remove(match.id());
            prune();
        } catch (IOException | RuntimeException failure) {
            retainFailed(match);
            warn("저장 실패 " + match.id(),failure);
        }
    }

    private void add(Match match) {
        matches.putIfAbsent(match.id(),match);
        if (matches.size() > LIMIT) matches.values().stream().min(java.util.Comparator.comparing(Match::ended)
                .thenComparing(Match::id)).ifPresent(oldest -> matches.remove(oldest.id(),oldest));
    }

    String status() { return state + " · 재시도 " + failed.size() + " · 대기 " + disk.getQueue().size(); }

    private void load() {
        try {
            Files.createDirectories(directory);
            try (var files = Files.newDirectoryStream(directory,"*.tsv")) {
                for (var file : files) {
                    try {
                        var match = read(file);
                        if (match.ended().isBefore(Instant.now().minus(retentionDays,ChronoUnit.DAYS))) Files.deleteIfExists(file);
                        else add(match);
                    } catch (IOException | RuntimeException failure) {
                        warn("손상된 통계 파일 보존 · " + file.getFileName(),failure);
                    }
                }
            }
            prune();
            if (state.equals("CHECKING")) state = "PASS";
        } catch (IOException failure) { warn("불러오기 실패",failure); }
    }

    private void prune() throws IOException {
        Instant cutoff = Instant.now().minus(retentionDays,ChronoUnit.DAYS);
        for (var match : matches.values()) {
            if (!match.ended().isBefore(cutoff)) continue;
            Files.deleteIfExists(directory.resolve(match.id()+".tsv"));
            matches.remove(match.id(),match);
        }
    }

    private static Match read(Path path) throws IOException {
        if (!Files.isRegularFile(path,java.nio.file.LinkOption.NOFOLLOW_LINKS) || Files.size(path) > 512_000) throw new IOException("Oversized statistics");
        List<String> lines = Files.readAllLines(path);
        if (lines.isEmpty()) throw new IOException("Missing statistics header");
        String[] header = lines.getFirst().split("\\t",-1);
        if (header.length != 5 || !header[0].equals(Integer.toString(SCHEMA))
                || !header[2].matches("[0-9]+(?:\\.[0-9]+){1,2}")) throw new IOException("Unknown statistics format");
        UUID id = UUID.fromString(header[1]);
        if (!path.getFileName().toString().equals(id+".tsv")) throw new IOException("Statistics ID mismatch");
        int participants = Integer.parseInt(header[4]);
        if (participants < 2) throw new IOException("Invalid statistics roster");
        var rows = new TreeMap<String,Map<Metric,Double>>();
        var offers = new TreeMap<String,Long>();
        for (int index = 1; index < lines.size(); index++) {
            var row = lines.get(index).split("\\t",-1);
            if (row[0].equals("offer") && row.length == 3 && row[1].matches("(?:pick/)?[a-z0-9_]+/[A-Z]+/[0-9]+")) {
                long count = Long.parseLong(row[2]);
                if (count <= 0 || offers.putIfAbsent(row[1],count) != null) throw new IOException("Invalid offer aggregate");
            } else if (row[0].equals("row") && row.length == 4 && row[1].matches("[a-z0-9_]+")) {
                double amount = Double.parseDouble(row[3]);
                if (!Double.isFinite(amount) || amount <= 0 || rows.computeIfAbsent(row[1],ignored -> new EnumMap<>(Metric.class))
                        .putIfAbsent(Metric.valueOf(row[2]),amount) != null) throw new IOException("Invalid metric aggregate");
            } else throw new IOException("Invalid statistics row");
        }
        return new Match(id,header[2],Instant.parse(header[3]),participants,new Snapshot(rows,offers));
    }

    private static List<String> encode(Match match) {
        var lines = new ArrayList<String>();
        lines.add(SCHEMA+"\t"+match.id()+"\t"+match.version()+"\t"+match.ended()+"\t"+match.participants());
        new TreeMap<>(match.stats().rows()).forEach((id,row) -> new TreeMap<>(row)
                .forEach((metric,value) -> lines.add("row\t"+id+"\t"+metric+"\t"+value)));
        new TreeMap<>(match.stats().offersByRound()).forEach((key,count) -> lines.add("offer\t"+key+"\t"+count));
        return lines;
    }

    static void write(Path target,List<String> lines) throws IOException {
        var temporary = Files.createTempFile(target.getParent(),".stats-",".tmp");
        try {
            Files.write(temporary,lines);
            try (var written = java.nio.channels.FileChannel.open(temporary,java.nio.file.StandardOpenOption.WRITE)) {
                written.force(true);
            }
            Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }

    List<String> report(String augment,String version) {
        return report(augment,new Filter(version,0,0,0));
    }

    private List<Match> select(Filter filter) {
        Instant cutoff = Instant.now().minus(filter.days()==0 ? retentionDays : Math.min(filter.days(),retentionDays),ChronoUnit.DAYS);
        return matches.values().stream().filter(match -> filter.version().equals(match.version())
                && !match.ended().isBefore(cutoff) && (filter.participants() == 0 || filter.participants() == match.participants())).toList();
    }

    List<String> group(List<String> augments,Filter filter) {
        var selected = select(filter);
        var result = new ArrayList<String>();
        result.add("운영 경기 " + selected.size() + " · " + filter.version() + " · 최대 " + retentionDays + "일");
        for (String id : augments) {
            var totals = new EnumMap<Metric,Double>(Metric.class);
            for (var match : selected) match.stats().rows().getOrDefault(id,Map.of()).forEach((k,v) -> totals.merge(k,v,Double::sum));
            String selection = ratio(totals,Metric.PICKED,Metric.OFFERED);
            if (filter.round() > 0) {
                String suffix = "/" + filter.round();
                long offered = 0, picked = 0;
                for (var match : selected) for (var row : match.stats().offersByRound().entrySet()) {
                    String key = row.getKey();
                    if (key.endsWith(suffix) && key.startsWith(id+"/")) offered += row.getValue();
                    if (key.endsWith(suffix) && key.startsWith("pick/"+id+"/")) picked += row.getValue();
                }
                selection = filter.version().equals(VERSION) ? "이전 형식에 회차별 선택 없음" : ratio(picked,offered);
            }
            result.add(id + " · 선택 " + selection + " · 보유 시 승리 " + ratio(totals,Metric.WON,Metric.OWNED));
        }
        return result;
    }

    List<String> report(String augment,Filter filter) {
        List<Match> selected = select(filter);
        String version = filter.version();
        if (selected.isEmpty()) return List.of("실제 완료 경기 데이터 없음 · " + version + " · " + state);
        var totals = new EnumMap<Metric,Double>(Metric.class);
        var offers = new TreeMap<String,Long>();
        var picks = new TreeMap<String,Long>();
        for (var match : selected) {
            match.stats().rows().getOrDefault(augment,Map.of()).forEach((metric,n) -> totals.merge(metric,n,Double::sum));
            match.stats().offersByRound().forEach((key,value) -> {
                if (key.startsWith(augment+"/")) offers.merge(key.substring(augment.length()+1),value,Long::sum);
                if (key.startsWith("pick/"+augment+"/")) picks.merge(key.substring(augment.length()+6),value,Long::sum);
            });
        }
        int participants = selected.stream().mapToInt(Match::participants).sum();
        Instant oldest = selected.stream().map(Match::ended).min(Instant::compareTo).orElseThrow();
        Instant latest = selected.stream().map(Match::ended).max(Instant::compareTo).orElseThrow();
        String selection = ratio(totals,Metric.PICKED,Metric.OFFERED);
        if (filter.round() > 0) {
            String suffix = "/" + filter.round();
            long offered = offers.entrySet().stream().filter(e -> e.getKey().endsWith(suffix)).mapToLong(Map.Entry::getValue).sum();
            long picked = picks.entrySet().stream().filter(e -> e.getKey().endsWith(suffix)).mapToLong(Map.Entry::getValue).sum();
            selection = version.equals(VERSION) ? "이전 형식에 회차별 선택 없음" : ratio(picked,offered);
            selection = filter.round() + "회차 " + selection;
        }
        return List.of("버전 " + version + " · 경기 " + selected.size() + " · 참가 표본 " + participants,
                java.time.LocalDateTime.ofInstant(oldest,java.time.ZoneOffset.UTC) + " ~ "
                        + java.time.LocalDateTime.ofInstant(latest,java.time.ZoneOffset.UTC) + " UTC",
                "선택률 " + selection + " · 보유 시 승률 " + ratio(totals,Metric.WON,Metric.OWNED),
                "보유율 " + ratio(totals.getOrDefault(Metric.OWNED,0d),participants)
                        + " · 진화율 " + ratio(totals,Metric.EVOLVED,Metric.EVOLUTION_ELIGIBLE),
                "스킬 성공률 " + ratio(totals.getOrDefault(Metric.CAST_OK,0d),
                        totals.getOrDefault(Metric.CAST_OK,0d)+totals.getOrDefault(Metric.CAST_FAILED,0d)),
                "보유당 발동 " + mean(totals,Metric.PROC,Metric.OWNED)
                        + " · 진화 시점 " + mean(totals,Metric.EVOLUTION_SECONDS,Metric.EVOLVED) + "초",
                "희귀도/회차 제시 " + offers,
                "희귀도/회차 선택 " + picks,
                "누적 " + totals);
    }

    String summary() {
        return "정상 완료 경기 " + matches.size() + " · 스키마 " + SCHEMA + " · 상태 " + status()
                + "\n/aw admin stats augment <id> [version=8.2.1] [players=16] [days=30] [round=2] · /aw admin stats export";
    }

    String export() {
        if (!async(() -> {
            try {
                Files.createDirectories(directory);
                Path target = directory.resolve("export.csv");
                Path temp = Files.createTempFile(directory,".export-",".csv");
                try {
                    try (var output = Files.newBufferedWriter(temp)) {
                        output.write("schema,match_id,game_version,ended_utc,participants,augment,metric,value");
                        output.newLine();
                        for (var match : matches.values().stream().sorted(java.util.Comparator.comparing(Match::ended)).toList()) {
                            String prefix = SCHEMA + "," + match.id()+","+match.version()+","+match.ended()+","+match.participants()+",";
                            for (var row : new TreeMap<>(match.stats().rows()).entrySet()) for (var metric : new TreeMap<>(row.getValue()).entrySet()) {
                                output.write(prefix+row.getKey()+","+metric.getKey()+","+metric.getValue());
                                output.newLine();
                            }
                            for (var row : new TreeMap<>(match.stats().offersByRound()).entrySet()) {
                                String[] fields = row.getKey().split("/");
                                int position = fields.length-3;
                                output.write(prefix+fields[position]+","+(position==0 ? "OFFER_" : "PICK_")
                                        +fields[position+1]+"_"+fields[position+2]+","+row.getValue());
                                output.newLine();
                            }
                        }
                    }
                    Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
                } finally { Files.deleteIfExists(temp); }
                logger.info("Augment statistics exported to " + directory.resolve("export.csv"));
            } catch (IOException | RuntimeException failure) { warn("CSV 내보내기 실패",failure); }
        })) return status();
        return "통계 CSV 내보내기를 예약했습니다 · analytics/export.csv";
    }

    private static String ratio(Map<Metric,Double> totals,Metric a,Metric b) {
        return ratio(totals.getOrDefault(a,0d),totals.getOrDefault(b,0d));
    }

    private static String mean(Map<Metric,Double> totals,Metric value,Metric count) {
        double denominator = totals.getOrDefault(count,0d);
        return denominator == 0 ? "계산 불가" : String.format(Locale.ROOT,"%.2f",totals.getOrDefault(value,0d)/denominator);
    }

    private static String ratio(double numerator,double denominator) {
        if (denominator == 0) return "계산 불가";
        return String.format(Locale.ROOT,"%.1f%% (%.0f/%.0f)%s",100*numerator/denominator,numerator,denominator,
                denominator < 10 ? " · 표본 부족" : "");
    }

    private void warn(String message,Exception failure) {
        state = "WARN";
        logger.log(java.util.logging.Level.WARNING,message,failure);
    }

    @Override public void close() {
        disk.shutdown();
        try { disk.awaitTermination(400,java.util.concurrent.TimeUnit.MILLISECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        if (!disk.isTerminated() || !failed.isEmpty()) logger.warning("Analytics shutdown: queued="
                + disk.getQueue().size() + " unsaved=" + failed.keySet());
    }
}
