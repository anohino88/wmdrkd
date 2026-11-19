package io.github.augmentwild.augment;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;

/** Opt-in, bounded event samples and per-augment totals scoped to one match. */
public final class CombatAudit {
    public enum Metric { SELECTED, PROC, STACKS, DAMAGE, HEALED, SHIELDED, REVIVED, SYNERGY }

    private final int limit;
    private final Consumer<String> sink;
    private final Map<String, Map<Metric, Double>> totals = new TreeMap<>();
    private final Map<UUID, Integer> aliases = new HashMap<>();
    private UUID match;
    private int samples;

    public CombatAudit(int limit, Consumer<String> sink) {
        if (limit < 0 || limit > 10_000) throw new IllegalArgumentException("dev.combat-audit.max-events");
        this.limit = limit;
        this.sink = sink;
    }

    public void start(UUID id) {
        match = id;
        samples = 0;
        aliases.clear();
        totals.clear();
    }

    public boolean enabled() { return match != null; }
    public boolean sampleAvailable() { return enabled() && samples < limit; }

    public String actor(UUID id) {
        if (id == null) return "-";
        return "actor-" + aliases.computeIfAbsent(id, key -> aliases.size() + 1);
    }

    public void sample(long elapsedSeconds, String fields) {
        if (!sampleAvailable()) return;
        samples++;
        sink.accept("combat-audit match=" + match + " elapsed_s=" + elapsedSeconds + " " + fields);
    }

    public void add(String augment, Metric metric, double amount) {
        if (!enabled() || !Double.isFinite(amount) || amount <= 0) return;
        totals.computeIfAbsent(augment, key -> new java.util.EnumMap<>(Metric.class)).merge(metric, amount, Double::sum);
    }

    public void finish() {
        if (!enabled()) return;
        totals.forEach((id, metrics) -> sink.accept("combat-audit summary match=" + match + " effect=" + id + " " + metrics));
        match = null;
        samples = 0;
        aliases.clear();
        totals.clear();
    }
}
