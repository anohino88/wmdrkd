package io.github.augmentwild.domain;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Mutable on the match thread. Snapshots contain aggregate rows and no player identifiers. */
public final class AugmentTelemetry {
    public enum Metric { OFFERED, PICKED, AUTO_PICKED, REROLLED, OWNED, WON, PROC, CAST_OK, CAST_FAILED,
        HP_DAMAGE, HEALED, SHIELDED, EVOLVED, EVOLUTION_SECONDS, SYNERGY }

    public record Snapshot(Map<String,Map<Metric,Double>> rows, Map<String,Long> offersByRound) {
        public Snapshot {
            var copied = new TreeMap<String,Map<Metric,Double>>();
            rows.forEach((id,row) -> copied.put(id,Map.copyOf(row)));
            rows = Map.copyOf(copied);
            offersByRound = Map.copyOf(offersByRound);
        }
    }

    private final Map<String,EnumMap<Metric,Double>> rows = new TreeMap<>();
    private final Map<String,Long> offers = new TreeMap<>();
    private final Map<String,Set<UUID>> owners = new HashMap<>();
    private Set<UUID> members = Set.of();

    void start(Set<UUID> players) {
        rows.clear();
        offers.clear();
        owners.clear();
        members = Set.copyOf(players);
    }

    public void offer(UUID player,String id,String rarity,int round) {
        if (!members.contains(player)) return;
        add(player,id,Metric.OFFERED,1);
        offers.merge(id + "/" + rarity + "/" + round,1L,Long::sum);
    }

    public void own(UUID player,String id) {
        if (members.contains(player) && owners.computeIfAbsent(id,ignored -> new HashSet<>()).add(player))
            add(player,id,Metric.OWNED,1);
    }

    public void add(UUID player,String id,Metric metric,double amount) {
        if (id == null || !members.contains(player) || amount <= 0 || !Double.isFinite(amount)) return;
        rows.computeIfAbsent(id,ignored -> new EnumMap<>(Metric.class)).merge(metric,amount,Double::sum);
    }

    public Snapshot snapshot(UUID winner) {
        var copy = new TreeMap<String,Map<Metric,Double>>();
        rows.forEach((id,values) -> {
            var row = new EnumMap<>(values);
            if (winner != null && owners.getOrDefault(id,Set.of()).contains(winner)) row.put(Metric.WON,1d);
            copy.put(id,row);
        });
        return new Snapshot(copy,offers);
    }
}
