package io.github.augmentwild.domain;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SupplyVisualTest {
    @Test void openingFollowsAuthoritativeTimerAndReturnsToLockedOnCancellation() {
        long total = 6_000_000_000L;
        assertEquals(0,SupplyVisual.step(total,total));
        assertEquals(3,SupplyVisual.step(total/2,total));
        assertEquals(5,SupplyVisual.step(1,total));
        assertEquals(6,SupplyVisual.step(0,total));
        assertEquals(0,SupplyVisual.degrees(0,false));
        assertEquals(0,SupplyVisual.degrees(0,true));
        assertTrue(SupplyVisual.degrees(5,true) > SupplyVisual.degrees(5,false));
        assertEquals(3,SupplyVisual.step(total/2,total),"a paused game clock gives the same step");
    }
}
