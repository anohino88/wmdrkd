package io.github.augmentwild.domain;

import java.util.*;

public final class MatchTelemetry {
    private final AugmentTelemetry augments = new AugmentTelemetry();
    public AugmentTelemetry augments() { return augments; }
    public int supplyClaims(UUID player) { return playerClaims.getOrDefault(player,0); }
    private final Map<UUID,Integer> playerClaims = new HashMap<>();
    public enum Counter { CREATED, NO_POSITION, CANCELLED_AFTER_PUBLICATION, OPEN_ATTEMPTS, OPEN_HIT_CANCELS,
        OPEN_SUCCESSES, CLAIM_DELAY_MS, SUPPLY_PVP, PAYOUT_FAILURES, TRACK_USES, TRACK_NO_TARGET,
        SCOUT_ATTEMPTS, SCOUT_SUCCESSES, SCOUT_ENGAGEMENTS, DEATH_VIEWS, DRAFTS, MANUAL_PICKS, AUTO_PICKS, REROLLS }

    private final Map<Counter, Long> counts = new EnumMap<>(Counter.class);
    private final Map<UUID, Observation> scouts = new HashMap<>();
    private final Map<UUID, Integer> players = new HashMap<>();
    private final Map<Pair, Long> lastCombat = new HashMap<>();
    private final Map<Long, Integer> engagements = new TreeMap<>();
    private final Map<Long, Integer> survivors = new TreeMap<>();
    private final Map<String, Long> claimDelays = new TreeMap<>();
    private final Map<String, Integer> synergies = new TreeMap<>();
    private final Map<String, Long> evolutions = new TreeMap<>();
    private final Set<String> completedSynergies = new HashSet<>();
    private int[] kills = new int[0];
    private int[] deaths = new int[0];
    private long firstPvpSecond;
    private final long[] tickBuckets = new long[1001];
    private long ticks, slowTicks;
    private double maxTick;
    private boolean active;

    private record Observation(UUID target, long deadline) { }

    private record Pair(int first, int second) { }

    public void start() { start(List.of()); }

    public void start(Collection<UUID> participants) {
        counts.clear();
        scouts.clear();
        players.clear();
        for (UUID id : participants.stream().sorted().toList()) players.put(id, players.size());
        augments.start(players.keySet());
        playerClaims.clear();
        kills = new int[players.size()];
        deaths = new int[players.size()];
        lastCombat.clear();
        engagements.clear();
        survivors.clear();
        claimDelays.clear();
        synergies.clear();
        evolutions.clear();
        completedSynergies.clear();
        firstPvpSecond = -1;
        Arrays.fill(tickBuckets, 0);
        ticks = slowTicks = 0;
        maxTick = 0;
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
        Integer a = players.get(attacker), b = players.get(victim);
        if (!active || a == null || b == null || a.equals(b)) return;
        long second = time / 1_000_000_000L;
        if (firstPvpSecond < 0) firstPvpSecond = second;
        var pair = new Pair(Math.min(a, b), Math.max(a, b));
        Long previous = lastCombat.put(pair, time);
        if (previous == null || time - previous >= 10_000_000_000L) engagements.merge(second / 300, 1, Integer::sum);
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

    public void supplyClaim(String drop, long millis) { supplyClaim(drop,millis,null); }

    public void supplyClaim(String drop, long millis, UUID player) {
        if (!active || claimDelays.putIfAbsent(drop, millis) != null) return;
        if (player != null && players.containsKey(player)) playerClaims.merge(player,1,Integer::sum);
        count(Counter.OPEN_SUCCESSES);
        add(Counter.CLAIM_DELAY_MS, millis);
    }

    public int kills(UUID id) { return players.containsKey(id) ? kills[players.get(id)] : 0; }
    public int deaths(UUID id) { return players.containsKey(id) ? deaths[players.get(id)] : 0; }
    public List<String> attained(UUID id) {
        var lines = new ArrayList<String>();
        int index = players.getOrDefault(id,-1);
        String prefix = "P" + (index+1) + "/";
        evolutions.forEach((key,time) -> { if (key.startsWith(prefix)) lines.add("진화 · " + key.substring(prefix.length())); });
        String member = index + ":";
        completedSynergies.stream().filter(k -> k.startsWith(member)).forEach(k -> lines.add("시너지 · " + k.substring(member.length())));
        return List.copyOf(lines);
    }

    public void death(UUID victim, UUID killer) {
        if (!active || !players.containsKey(victim)) return;
        deaths[players.get(victim)]++;
        if (!victim.equals(killer) && players.containsKey(killer)) kills[players.get(killer)]++;
    }

    public void synergy(UUID player, String effect) {
        Integer index = players.get(player);
        if (active && index != null && completedSynergies.add(index + ":" + effect)) {
            synergies.merge(effect,1,Integer::sum);
            augments.add(player,effect,AugmentTelemetry.Metric.SYNERGY,1);
        }
    }

    public void evolution(UUID player, String effect, long elapsedSeconds) {
        Integer index = players.get(player);
        if (active && index != null && evolutions.putIfAbsent("P" + (index + 1) + "/" + effect,elapsedSeconds) == null) {
            augments.add(player,effect,AugmentTelemetry.Metric.EVOLVED,1);
            augments.add(player,effect,AugmentTelemetry.Metric.EVOLUTION_SECONDS,elapsedSeconds);
        }
    }

    public void survivors(long observedSecond, int count) {
        if (active) survivors.put(observedSecond, count);
    }

    public void tick(double millis) {
        if (!active || !Double.isFinite(millis) || millis < 0) return;
        ticks++;
        if (millis > 50) slowTicks++;
        tickBuckets[(int) Math.min(1000, Math.ceil(millis))]++;
        maxTick = Math.max(maxTick, millis);
    }

    public String tempo(long gameSeconds, long wallSeconds) {
        var scores = new ArrayList<String>();
        for (int i = 0; i < kills.length; i++) scores.add("P" + (i + 1) + "=" + kills[i] + "/" + deaths[i]);
        int bucket = 0;
        long cumulative = tickBuckets[0];
        long rank = ticks - ticks / 20;
        while (cumulative < rank && bucket + 1 < tickBuckets.length) cumulative += tickBuckets[++bucket];
        String p95 = ticks == 0 ? "NA" : bucket == 1000 ? ">999" : Integer.toString(bucket);
        return "game_s=" + gameSeconds + " wall_s=" + wallSeconds + " first_pvp_s=" + firstPvpSecond
                + " engagements_by_5m=" + engagements + " kills_deaths=" + scores + " survivors_by_game_s=" + survivors
                + " supply_claim_ms=" + claimDelays + " synergies=" + synergies + " evolutions_s=" + evolutions + " tick_samples=" + ticks
                + " tick_p95_ms=" + p95 + " tick_max_ms=" + (ticks == 0 ? "NA" : String.format(Locale.ROOT, "%.3f", maxTick))
                + " tick_over_50=" + slowTicks + "/" + ticks;
    }

    public Map<Counter, Long> finish() {
        if (!active) return Map.of();
        active = false;
        scouts.clear();
        return Map.copyOf(counts);
    }
}
