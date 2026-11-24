package io.github.augmentwild.domain;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class AugmentTelemetryTest {
    @Test void snapshotsAreAnonymizedImmutableAndMatchScoped() {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var observer = UUID.randomUUID();
        var telemetry = new MatchTelemetry();
        telemetry.start(Set.of(first,second));
        var stats = telemetry.augments();
        stats.offer(first,"steel_heart","GOLD",3);
        stats.offer(observer,"steel_heart","GOLD",3);
        stats.own(first,"steel_heart");
        stats.own(first,"steel_heart");
        stats.add(first,"steel_heart",AugmentTelemetry.Metric.PICKED,1);
        telemetry.evolution(first,"steel_heart",761);
        telemetry.evolution(first,"steel_heart",800);
        telemetry.supplyClaim("gold",100,first);
        telemetry.supplyClaim("gold",100,second);
        var snapshot = stats.snapshot(first);
        var heart = snapshot.rows().get("steel_heart");

        assertEquals(1d,heart.get(AugmentTelemetry.Metric.OWNED));
        assertEquals(1d,heart.get(AugmentTelemetry.Metric.WON));
        assertEquals(761d,heart.get(AugmentTelemetry.Metric.EVOLUTION_SECONDS));
        assertEquals(1L,snapshot.offersByRound().get("steel_heart/GOLD/3"));
        assertEquals(1,telemetry.supplyClaims(first));
        assertEquals(0,telemetry.supplyClaims(second));
        assertFalse(snapshot.toString().contains(first.toString()));
        assertThrows(UnsupportedOperationException.class,heart::clear);

        telemetry.start(Set.of(second,observer));
        assertTrue(stats.snapshot(second).rows().isEmpty());
        assertEquals(1d,snapshot.rows().get("steel_heart").get(AugmentTelemetry.Metric.OWNED));
    }

    @Test void visualMilestonesUseBoundariesWithoutChangingTheProgress() {
        assertEquals(0,GrowthVisual.stage(24.99,100));
        assertEquals(1,GrowthVisual.stage(25,100));
        assertEquals(2,GrowthVisual.stage(50,100));
        assertEquals(3,GrowthVisual.stage(75,100));
        assertEquals(4,GrowthVisual.stage(180,100));
        assertEquals(0,GrowthVisual.stage(25,0));
        assertEquals(0,GrowthVisual.stage(Double.NaN,50));
    }

    @Test void persistentLayersCanBeReadBehindUnexpiredCombatAlerts() {
        var hud = new HudMessages<String>();
        hud.set(HudMessages.Layer.SKILL,"스킬 준비");
        hud.until(HudMessages.Layer.DAMAGE,"피해",10);
        assertEquals("피해",hud.current(5));
        assertEquals("스킬 준비",hud.layer(HudMessages.Layer.SKILL,5));
        assertNull(hud.layer(HudMessages.Layer.DAMAGE,10));
        assertEquals("스킬 준비",hud.current(10));
    }
}
