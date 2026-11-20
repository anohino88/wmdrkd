package io.github.augmentwild.match;

import io.github.augmentwild.augment.*;
import io.github.augmentwild.domain.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatConvenienceTest {
    @Test void switchingAndMovingBindingsKeepTheFightersLedger() {
        var slots = new SkillSlots();
        var ledger = new AugmentProgress();
        assertTrue(slots.bind(1,"shadow_dash"));
        assertTrue(slots.bind(3,"duel_contract"));
        assertTrue(ledger.cooldown(slots.current(),51,12));
        slots.next();
        assertEquals(3,slots.selected());
        assertTrue(slots.select(1));
        assertEquals(10,ledger.cooldownRemaining(slots.current(),53));
        assertTrue(slots.bind(2,"shadow_dash"));
        assertNull(slots.current());
        assertFalse(slots.bind(4,"shadow_dash"));
        slots.next();
        assertEquals(2,slots.selected());
        assertFalse(ledger.ready(slots.current(),62));
        slots.remove("shadow_dash");
        assertNull(slots.current());
        slots.next();
        assertEquals("duel_contract",slots.current());
        assertFalse(slots.select(0));
    }

    @Test void bearingsFollowViewerYawAndEdgeUsesLiveRectangularGeometry() {
        String[] expected = {"↑","↗","→","↘","↓","↙","←","↖"};
        for (int step=0;step<8;step++) {
            double angle = Math.toRadians(step*45 + 90);
            assertEquals(expected[step],CombatNavigation.bearing(-Math.sin(angle),Math.cos(angle),90));
        }
        assertEquals("",CombatNavigation.bearing(0,0,0));
        var arena = new Arena(10,-10,100,50);
        assertEquals(15,CombatNavigation.edgeDistance(arena,0,0));
        assertEquals(5,CombatNavigation.edgeDistance(arena,63,19));
        assertEquals(0,CombatNavigation.edgeDistance(arena,60,0));
        var finalArea = arena.shrink(20,0,20,30,30);
        assertEquals(15,CombatNavigation.edgeDistance(finalArea,10,-10));
    }

    @Test void recentHitReplacesDirectionAndNeverReturnsAfterItsDeadline() {
        var hud = new HudMessages<String>();
        hud.set(HudMessages.Layer.SKILL,"active");
        hud.set(HudMessages.Layer.AUTO,"automatic");
        assertEquals("active",hud.current(0));
        assertEquals("automatic",hud.current(2_000_000_000L));
        hud.until(HudMessages.Layer.INCOMING,"↑",1_000_000_000L);
        hud.until(HudMessages.Layer.INCOMING,"←",1_500_000_000L);
        assertEquals("←",hud.current(1_499_999_999L));
        hud.until(HudMessages.Layer.CRISIS,"low",2_000_000_000L);
        hud.set(HudMessages.Layer.RESCUE,"saved");
        assertEquals("saved",hud.current(1_500_000_000L));
        hud.remove(HudMessages.Layer.RESCUE);
        assertEquals("low",hud.current(1_500_000_001L));
        assertEquals("automatic",hud.current(2_000_000_000L));
    }

    @Test void statusUsesTheSameExpiryAsShieldAndBuffMechanics() {
        var shield = new ResonanceShield();
        shield.grant(17,5,80);
        shield.consume(37,2);
        assertEquals(60,shield.timeLeft(37));
        shield.consume(38,3);
        assertEquals(0,shield.timeLeft(38));
        var timers = new AugmentTimers();
        timers.activate("defense",3,8);
        assertEquals(1,timers.remainingActive("defense",10));
        assertEquals(0,timers.remainingActive("defense",11));
    }
}
