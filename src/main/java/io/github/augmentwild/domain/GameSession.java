package io.github.augmentwild.domain;

import io.github.augmentwild.match.*;
import java.util.*;

/** Single-threaded domain model; all times are monotonic nanoseconds supplied by caller. */
public final class GameSession {
    public record Participant(UUID id, LifeState life, boolean connected) { }
    private final MatchStateMachine machine = new MatchStateMachine();
    private final MatchRules rules;
    private final Map<UUID, Participant> players = new LinkedHashMap<>();
    private final Map<UUID, Long> deadlines = new HashMap<>();
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
        if (life.status()==LifeState.Status.RESPAWNING) deadlines.put(id,deadline);
        return true;
    }
    public OptionalLong deadline(UUID id) { Long d=deadlines.get(id); return d==null ? OptionalLong.empty() : OptionalLong.of(d); }
    public boolean respawn(UUID id, long now) {
        var p=player(id); Long d=deadlines.get(id);
        if (state()==MatchState.ENDING || state()==MatchState.CLEANUP || d==null || now<d || !p.connected()) return false;
        deadlines.remove(id); return p.life().respawn();
    }
    public Optional<UUID> winner() {
        var eligible=players.values().stream().filter(p -> p.life().status()!=LifeState.Status.ELIMINATED).map(Participant::id).toList();
        return eligible.size()==1 ? Optional.of(eligible.getFirst()) : Optional.empty();
    }
}
