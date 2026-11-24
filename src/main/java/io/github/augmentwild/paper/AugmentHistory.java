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
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/** One atomic file per completed match. Workers only receive immutable values. */
final class AugmentHistory implements AutoCloseable {
    private static final int SCHEMA = 1;
    private static final String VERSION = "8.2";
    private record Match(UUID id,String version,Instant ended,int participants,Snapshot stats) {}
    private final Path directory;
    private final Logger logger;
    private final int retentionDays;
    private final Map<UUID,Match> matches = new ConcurrentHashMap<>();
    private final java.util.concurrent.ExecutorService disk = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("AugmentWild analytics").factory());
    private volatile String state = "CHECKING";

    AugmentHistory(Path directory,Logger logger,int retentionDays) {
        this.directory = directory;
        this.logger = logger;
        this.retentionDays = retentionDays;
        disk.execute(this::load);
    }

    java.util.concurrent.Future<?> append(UUID id,int participants,Snapshot stats) {
        var match = new Match(id,VERSION,Instant.now(),participants,stats);
        return disk.submit(() -> {
            try {
                Files.createDirectories(directory);
                Path file = directory.resolve(id+".tsv");
                if (!Files.exists(file)) write(file,encode(match));
                matches.putIfAbsent(id,read(file));
                prune();
            } catch (IOException | RuntimeException failure) {
                warn("저장 실패 " + id,failure);
            }
        });
    }

    String status() { return state; }

    private void load() {
        try {
            Files.createDirectories(directory);
            try (var files = Files.newDirectoryStream(directory,"*.tsv")) {
                for (var file : files) {
                    try {
                        var match = read(file);
                        matches.putIfAbsent(match.id(),match);
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
        if (Files.size(path) > 512_000) throw new IOException("Oversized statistics");
        List<String> lines = Files.readAllLines(path);
        if (lines.isEmpty()) throw new IOException("Missing statistics header");
        String[] header = lines.getFirst().split("\\t",-1);
        if (header.length != 5 || !header[0].equals(Integer.toString(SCHEMA))
                || !header[2].matches("[0-9]+\\.[0-9]+")) throw new IOException("Unknown statistics format");
        UUID id = UUID.fromString(header[1]);
        if (!path.getFileName().toString().equals(id+".tsv")) throw new IOException("Statistics ID mismatch");
        int participants = Integer.parseInt(header[4]);
        if (participants < 2) throw new IOException("Invalid statistics roster");
        var rows = new TreeMap<String,Map<Metric,Double>>();
        var offers = new TreeMap<String,Long>();
        for (int index = 1; index < lines.size(); index++) {
            var row = lines.get(index).split("\\t",-1);
            if (row[0].equals("offer") && row.length == 3 && row[1].matches("[a-z0-9_]+/[A-Z]+/[0-9]+")) {
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

    private static void write(Path target,List<String> lines) throws IOException {
        var temporary = Files.createTempFile(target.getParent(),".stats-",".tmp");
        try {
            Files.write(temporary,lines);
            Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }

    List<String> report(String augment,String version) {
        List<Match> selected = matches.values().stream().filter(match -> version.equals(match.version())).toList();
        if (selected.isEmpty()) return List.of("실제 완료 경기 데이터 없음 · " + version + " · " + state);
        var totals = new EnumMap<Metric,Double>(Metric.class);
        var offers = new TreeMap<String,Long>();
        for (var match : selected) {
            match.stats().rows().getOrDefault(augment,Map.of()).forEach((metric,n) -> totals.merge(metric,n,Double::sum));
            match.stats().offersByRound().forEach((key,value) -> {
                if (key.startsWith(augment+"/")) offers.merge(key.substring(augment.length()+1),value,Long::sum);
            });
        }
        int participants = selected.stream().mapToInt(Match::participants).sum();
        Instant oldest = selected.stream().map(Match::ended).min(Instant::compareTo).orElseThrow();
        Instant latest = selected.stream().map(Match::ended).max(Instant::compareTo).orElseThrow();
        return List.of("버전 " + version + " · 경기 " + selected.size() + " · 참가 표본 " + participants,
                java.time.LocalDateTime.ofInstant(oldest,java.time.ZoneOffset.UTC) + " ~ "
                        + java.time.LocalDateTime.ofInstant(latest,java.time.ZoneOffset.UTC) + " UTC",
                "선택률 " + ratio(totals,Metric.PICKED,Metric.OFFERED) + " · 보유 시 승률 " + ratio(totals,Metric.WON,Metric.OWNED),
                "보유율 " + ratio(totals.getOrDefault(Metric.OWNED,0d),participants)
                        + " · 진화율 " + ratio(totals,Metric.EVOLVED,Metric.OWNED),
                "스킬 성공률 " + ratio(totals.getOrDefault(Metric.CAST_OK,0d),
                        totals.getOrDefault(Metric.CAST_OK,0d)+totals.getOrDefault(Metric.CAST_FAILED,0d)),
                "보유당 발동 " + mean(totals,Metric.PROC,Metric.OWNED)
                        + " · 진화 시점 " + mean(totals,Metric.EVOLUTION_SECONDS,Metric.EVOLVED) + "초",
                "희귀도/회차 " + offers,
                "누적 " + totals);
    }

    String summary() {
        return "정상 완료 경기 " + matches.size() + " · 스키마 " + SCHEMA + " · 상태 " + state
                + "\n/aw admin stats augment <id> [버전] · /aw admin stats export";
    }

    String export() {
        disk.execute(() -> {
            try {
                Files.createDirectories(directory);
                var lines = new ArrayList<>(List.of("schema,match_id,game_version,ended_utc,participants,augment,metric,value"));
                matches.values().stream().sorted(java.util.Comparator.comparing(Match::ended))
                        .forEach(match -> new TreeMap<>(match.stats().rows()).forEach((id,row) -> new TreeMap<>(row).forEach((metric,n) ->
                                lines.add(SCHEMA + "," + match.id()+","+match.version()+","+match.ended()+","
                                        + match.participants()+","+id+","+metric+","+n))));
                write(directory.resolve("export.csv"),lines);
                logger.info("Augment statistics exported to " + directory.resolve("export.csv"));
            } catch (IOException | RuntimeException failure) { warn("CSV 내보내기 실패",failure); }
        });
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

    @Override public void close() { disk.shutdown(); }
}
