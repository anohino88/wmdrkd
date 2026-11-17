package io.github.augmentwild.domain;

import io.github.augmentwild.match.*;
import java.util.*;

/** Single-threaded domain model; all times are monotonic nanoseconds supplied by caller. */
public final class GameSession {
    public record Participant(UUID id, LifeState life, boolean connected) { }
    private final MatchStateMachine machine = new MatchStateMachine();
    private final MatchRules rules;
    private final Map<UUID, Participant> players = new LinkedHashMap<>();
    public record RespawnReservation(UUID matchId, UUID playerId, long deathSequence, UUID reservationId,
                                     long scheduledAt, long deadline) { }
    private final UUID matchId = UUID.randomUUID();
    private final Map<UUID, Long> sequences = new HashMap<>();
    private final Map<UUID, RespawnReservation> deadlines = new HashMap<>();
    public UUID matchId() { return matchId; }
    public Optional<RespawnReservation> reservation(UUID id) { return Optional.ofNullable(deadlines.get(id)); }
    public GameSession(MatchRules rules) { this.rules=Objects.requireNonNull(rules); }
    public MatchState state() { return machine.state(); }
    public void transition(MatchState next) {
        machine.transitionTo(next);
        if (next==MatchState.ENDING || next==MatchState.CLEANUP) deadlines.clear();
    }
    public void join(UUID id) {
        if (state()!=MatchState.WAITING) throw new IllegalStateException();
        Objects.requireNonNull(id);
        if (players.putIfAbsent(id,new Participant(id,new LifeState(rules.lives()),true))!=null) throw new IllegalStateException();
    }
    public void leave(UUID id) { deadlines.remove(id); players.remove(id); }
    public void connected(UUID id, boolean connected) { var p=player(id); players.put(id,new Participant(id,p.life(),connected)); }
    public Participant player(UUID id) { return Objects.requireNonNull(players.get(id),"unknown participant"); }
    public boolean death(UUID id, long now) {
        var phase = switch (state()) {
            case GATHERING -> LifeState.Phase.GATHERING;
            case PVP -> LifeState.Phase.PVP;
            case SUDDEN_DEATH -> LifeState.Phase.SUDDEN_DEATH;
            default -> throw new IllegalStateException();
        };
        var life=player(id).life();
        // Compute before mutation so overflow cannot leave a half-applied death.
        long deadline=Math.addExact(now,Math.multiplyExact(rules.respawnSeconds(),1_000_000_000L));
        if (!life.resolveDeath(phase)) return false;
        if (life.status()==LifeState.Status.RESPAWNING) {
            long sequence = sequences.merge(id, 1L, Math::addExact);
            deadlines.put(id,new RespawnReservation(matchId,id,sequence,UUID.randomUUID(),now,deadline));
        }
        return true;
    }
    public OptionalLong deadline(UUID id) { var d=deadlines.get(id); return d==null ? OptionalLong.empty() : OptionalLong.of(d.deadline()); }
    public boolean respawn(UUID id, long now) {
        var p=player(id); var d=deadlines.get(id);
        if (state()==MatchState.ENDING || state()==MatchState.CLEANUP || d==null || now<d.deadline() || !p.connected()) return false;
        deadlines.remove(id); return p.life().respawn();
    }
    /** Caller supplies one atomic position snapshot; session eligibility overrides snapshot status. */
    public Optional<Tracker.Result> locate(UUID source, Collection<Tracker.Player> snapshot) {
        var byId = new HashMap<UUID, Tracker.Player>();
        for (var position : snapshot) {
            if (byId.putIfAbsent(position.id(), position) != null) throw new IllegalArgumentException("duplicate position");
        }
        var origin = byId.get(source);
        if (origin == null || !players.containsKey(source) || !player(source).connected()
                || player(source).life().status()!=LifeState.Status.ACTIVE) return Optional.empty();
        var eligible = byId.values().stream().filter(p -> players.containsKey(p.id()))
                .filter(p -> player(p.id()).connected() && player(p.id()).life().status()==LifeState.Status.ACTIVE)
                .toList();
        return new Tracker().nearest(origin, eligible);
    }
    public enum Outcome { IN_PROGRESS, WINNER, DRAW, CANCELLED }
    public record Result(Outcome outcome, UUID winner) {
        public Result {
            Objects.requireNonNull(outcome);
            if ((outcome == Outcome.WINNER) != (winner != null)) throw new IllegalArgumentException();
        }
    }
    /** Resolve an entire logical tick before evaluating victory. Caller serializes ticks. */
    public Result resolveTick(Collection<UUID> deaths, long now) {
        Objects.requireNonNull(deaths);
        if (state()==MatchState.ENDING || state()==MatchState.CLEANUP) return result;
        if (state()!=MatchState.PVP && state()!=MatchState.SUDDEN_DEATH) throw new IllegalStateException();
        var unique = new LinkedHashSet<>(deaths);
        for (UUID id : unique) player(id); // Validate the whole batch before mutation.
        Math.addExact(now, Math.multiplyExact(rules.respawnSeconds(), 1_000_000_000L));
        for (UUID id : unique) death(id, now);
        var remaining = players.values().stream().filter(p -> p.life().status()!=LifeState.Status.ELIMINATED).map(Participant::id).toList();
        if (remaining.size() <= 1) {
            result = remaining.isEmpty() ? new Result(Outcome.DRAW, null) : new Result(Outcome.WINNER, remaining.getFirst());
            transition(MatchState.ENDING);
        }
        return result;
    }
    private Result result = new Result(Outcome.IN_PROGRESS, null);
    public Result result() { return result; }
    public Result cancel() {
        if (state()==MatchState.ENDING || state()==MatchState.CLEANUP) return result;
        if (state()==MatchState.WAITING) throw new IllegalStateException();
        result = new Result(Outcome.CANCELLED, null);
        if (state()==MatchState.PREPARING) transition(MatchState.CLEANUP); else transition(MatchState.ENDING);
        return result;
    }
    public Optional<UUID> winner() {
        var eligible=players.values().stream().filter(p -> p.life().status()!=LifeState.Status.ELIMINATED).map(Participant::id).toList();
        return eligible.size()==1 ? Optional.of(eligible.getFirst()) : Optional.empty();
    }
}
