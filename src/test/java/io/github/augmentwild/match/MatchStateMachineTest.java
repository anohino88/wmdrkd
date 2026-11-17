package io.github.augmentwild.match;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MatchStateMachineTest {
    @Test void rejectsSkippingPreparation() {
        var machine = new MatchStateMachine();
        assertThrows(IllegalStateException.class, () -> machine.transitionTo(MatchState.PVP));
        assertEquals(MatchState.WAITING, machine.state());
    }
    @Test void allowsPreparationToBeCancelled() {
        var machine = new MatchStateMachine();
        machine.transitionTo(MatchState.PREPARING);
        machine.transitionTo(MatchState.CLEANUP);
        machine.transitionTo(MatchState.WAITING);
        assertEquals(MatchState.WAITING, machine.state());
    }
}
