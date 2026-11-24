package io.github.augmentwild.paper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** Only appends new, known settings, preserving the operator's original YAML. */
final class ConfigMigration {
    private static final Map<String,Integer> DEFAULTS = new LinkedHashMap<>();
    static {
        DEFAULTS.put("settings-version",1);
        DEFAULTS.put("telemetry-retention-days",90);
    }

    record Plan(String original,String output,Map<String,Integer> additions) {}

    private ConfigMigration() {}

    static Plan plan(String original) throws InvalidConfigurationException {
        var yaml = new YamlConfiguration();
        yaml.loadFromString(original);
        if (yaml.getKeys(false).isEmpty()) throw new InvalidConfigurationException("설정 맵이 비었습니다.");
        var additions = new LinkedHashMap<String,Integer>();
        for (var setting : DEFAULTS.entrySet()) {
            Object value = yaml.get(setting.getKey());
            if (!yaml.contains(setting.getKey())) additions.put(setting.getKey(),setting.getValue());
            else if (!(value instanceof Integer n) || n < 1 || n > (setting.getKey().equals("settings-version") ? 1 : 3650))
                throw new InvalidConfigurationException("충돌 · " + setting.getKey() + " = " + value);
        }
        if (additions.isEmpty()) return new Plan(original,original,Map.of());
        var output = new StringBuilder(original);
        if (!original.endsWith("\n")) output.append('\n');
        output.append("\n# AugmentWild 설정 스키마 및 통계 보관 기간\n");
        additions.forEach((key,value) -> output.append(key).append(": ").append(value).append('\n'));
        var verified = new YamlConfiguration();
        verified.loadFromString(output.toString());
        for (var setting : additions.entrySet())
            if (!setting.getValue().equals(verified.get(setting.getKey())))
                throw new InvalidConfigurationException("신규 설정 검증 실패 · " + setting.getKey());
        return new Plan(original,output.toString(),Map.copyOf(additions));
    }

    static String run(Path file,String[] args) {
        try {
            if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) return "FAIL · config.yml을 찾을 수 없습니다.";
            String original = Files.readString(file);
            if (args.length == 1 && args[0].equals("backup")) return "백업 · " + backup(file,original).getFileName();
            var plan = plan(original);
            if (args.length == 1 && args[0].equals("check")
                    || args.length == 2 && args[0].equals("migrate") && args[1].equals("--dry-run")) {
                return "스키마 " + (plan.additions().isEmpty() ? "PASS" : "WARN")
                        + " · 추가 예정 " + plan.additions() + " · 기존 값과 주석 보존 · 적용 후 재시작";
            }
            if (args.length == 2 && args[0].equals("migrate") && args[1].equals("--apply")) {
                if (plan.additions().isEmpty()) return "PASS · 최신 스키마";
                Path saved = backup(file,original);
                Path temporary = Files.createTempFile(file.getParent(),".config-",".yml");
                try {
                    Files.writeString(temporary,plan.output(),StandardCharsets.UTF_8);
                    if (!plan(Files.readString(temporary)).additions().isEmpty()) throw new IOException("검증 실패");
                    if (!original.equals(Files.readString(file))) throw new IOException("동시 수정 감지");
                    Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
                } finally { Files.deleteIfExists(temporary); }
                return "적용 · " + plan.additions() + " · 백업 " + saved.getFileName() + " · 재시작 필요";
            }
            return "/aw admin config check | migrate --dry-run | migrate --apply | backup";
        } catch (IOException | InvalidConfigurationException failure) {
            return "FAIL · " + failure.getMessage();
        }
    }

    private static Path backup(Path file,String original) throws IOException {
        Path save = Files.createTempFile(file.getParent(),"config-backup-" + Instant.now().toEpochMilli() + "-", ".yml");
        Files.writeString(save,original,StandardCharsets.UTF_8);
        return save;
    }
}
