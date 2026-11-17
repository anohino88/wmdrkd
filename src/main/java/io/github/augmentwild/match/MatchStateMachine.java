package io.github.augmentwild.match;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** State changes are explicit; server integration must invoke this on the main thread. */
public final class MatchStateMachine {
    private static final Map<MatchState, Set<MatchState>> TRANSITIONS = new EnumMap<>(MatchState.class);
    static {
        TRANSITIONS.put(MatchState.WAITING, EnumSet.of(MatchState.PREPARING));
        TRANSITIONS.put(MatchState.PREPARING, EnumSet.of(MatchState.GATHERING, MatchState.CLEANUP));
        TRANSITIONS.put(MatchState.GATHERING, EnumSet.of(MatchState.AUGMENT_SELECTION, MatchState.PVP, MatchState.ENDING));
        TRANSITIONS.put(MatchState.AUGMENT_SELECTION, EnumSet.of(MatchState.GATHERING, MatchState.PVP, MatchState.ENDING));
        TRANSITIONS.put(MatchState.PVP, EnumSet.of(MatchState.SUDDEN_DEATH, MatchState.ENDING));
        TRANSITIONS.put(MatchState.SUDDEN_DEATH, EnumSet.of(MatchState.ENDING));
        TRANSITIONS.put(MatchState.ENDING, EnumSet.of(MatchState.CLEANUP));
        TRANSITIONS.put(MatchState.CLEANUP, EnumSet.of(MatchState.WAITING));
    }
    private MatchState state = MatchState.WAITING;
    public MatchState state() { return state; }
    public void transitionTo(MatchState next) {
        if (!TRANSITIONS.get(state).contains(next)) throw new IllegalStateException("Invalid transition: " + state + " -> " + next);
        state = next;
    }
}
