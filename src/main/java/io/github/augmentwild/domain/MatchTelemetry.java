package io.github.augmentwild.domain;

import java.util.*;

public final class MatchTelemetry {
    public enum Counter { CREATED, NO_POSITION, CANCELLED_AFTER_PUBLICATION, OPEN_ATTEMPTS, OPEN_HIT_CANCELS,
        OPEN_SUCCESSES, CLAIM_DELAY_MS, SUPPLY_PVP, PAYOUT_FAILURES, TRACK_USES, TRACK_NO_TARGET,
        SCOUT_ATTEMPTS, SCOUT_SUCCESSES, SCOUT_ENGAGEMENTS, DEATH_VIEWS }

    private final Map<Counter, Long> counts = new EnumMap<>(Counter.class);
    private final Map<UUID, Observation> scouts = new HashMap<>();
    private boolean active;

    private record Observation(UUID target, long deadline) { }

    public void start() {
        counts.clear();
        scouts.clear();
        active = true;
    }

    public void count(Counter counter) { add(counter, 1); }

    public void add(Counter counter, long value) {
        if (active) counts.merge(counter, value, Long::sum);
    }

    public void scout(UUID viewer, UUID target, long deadline) {
        if (!active) return;
        count(Counter.SCOUT_SUCCESSES);
        scouts.put(viewer, new Observation(target, deadline));
    }

    public void engage(UUID attacker, UUID victim, long time) {
        verifyEngagement(attacker, victim, time);
        verifyEngagement(victim, attacker, time);
    }

    private void verifyEngagement(UUID viewer, UUID other, long time) {
        Observation observation = scouts.get(viewer);
        if (observation == null) return;
        if (observation.deadline() <= time) scouts.remove(viewer);
        else if (other.equals(observation.target())) {
            scouts.remove(viewer);
            count(Counter.SCOUT_ENGAGEMENTS);
        }
    }

    public Map<Counter, Long> finish() {
        if (!active) return Map.of();
        active = false;
        scouts.clear();
        return Map.copyOf(counts);
    }
}
