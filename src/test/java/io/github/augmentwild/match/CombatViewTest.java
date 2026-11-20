package io.github.augmentwild.match;

import io.github.augmentwild.domain.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatViewTest {
    private final UUID first = UUID.randomUUID(), second = UUID.randomUUID();

    @Test void snapshotFreezesUntilDeadlineWithoutRefreshingHiddenHealth() {
        var observed = new CombatView.Observation(second,"적",26,60,4,10);
        assertTrue(observed.visible(500_000_009));
        assertFalse(observed.visible(500_000_010));
        assertEquals("적 · 26.0 / 60.0 HP · 흡수 +4.0",observed.title());
        for (int maximum : List.of(20,40,100,240)) assertEquals(.25f,CombatView.ratio(maximum/4,maximum));
        assertTrue(CombatView.inRange(576));
        assertFalse(CombatView.inRange(576.01));
    }

    @Test void reportOnlyMatchingHealthLossAndSeparateNativeAbsorption() {
        assertEquals(0,CombatView.confirmed(0,12));
        assertEquals(0,CombatView.confirmed(6,12));
        assertEquals(0,CombatView.confirmed(-2,4));
        assertEquals(6,CombatView.confirmed(6,6));
        assertEquals(2,CombatView.confirmed(2,2));
        var receipt = new CombatView.Receipt(new CombatView.Vitals(20,4),new CombatView.Vitals(5,4));
        assertEquals(new CombatView.Vitals(5,4),receipt.confirm(new CombatView.Vitals(15,0),false));
        assertEquals(new CombatView.Vitals(0,0),receipt.confirm(new CombatView.Vitals(15,0),true));
        assertEquals(new CombatView.Vitals(0,0),receipt.confirm(new CombatView.Vitals(20,4),false));
        UUID root = UUID.randomUUID();
        assertEquals(new CombatView.OriginTarget(first,second,root),new CombatView.OriginTarget(first,second,root));
        assertNotEquals(new CombatView.OriginTarget(first,second,root),new CombatView.OriginTarget(first,UUID.randomUUID(),root));
    }

    @Test void expiredDamageNeverReappearsAfterSupplyOrSelection() {
        var layers = new HudMessages<String>();
        layers.set(HudMessages.Layer.SKILL,"준비");
        layers.set(HudMessages.Layer.TRACK,"북");
        layers.until(HudMessages.Layer.DAMAGE,"피해",CombatView.DAMAGE_VISIBLE);
        assertEquals("피해",layers.current(1));
        layers.set(HudMessages.Layer.SUPPLY,"개봉");
        assertEquals("개봉",layers.current(2));
        layers.remove(HudMessages.Layer.SUPPLY);
        assertEquals("북",layers.current(CombatView.DAMAGE_VISIBLE));
    }

    @Test void publicSupplyAndSelectionUseOnlyPublishedStateAndGameTime() {
        assertEquals("공개 대기",CombatView.supplyStatus(false,false,false,false));
        assertEquals("획득 완료",CombatView.supplyStatus(true,true,true,false));
        assertEquals("취소",CombatView.supplyStatus(true,false,true,false));
        assertEquals("개봉 가능",CombatView.supplyStatus(true,false,true,true));
        assertEquals(78,CombatView.nextPick(1122,300,1800));
        assertEquals(-1,CombatView.nextPick(1800,300,1800));
    }

    @Test void deathFeedAndFinishedScoresFollowDomainOutcome() {
        var telemetry = new MatchTelemetry();
        telemetry.start(List.of(first,second));
        telemetry.death(second,first);
        telemetry.synergy(first,"steel_king");
        telemetry.evolution(first,"steel_heart",118);
        var snapshot = telemetry.attained(first);
        assertEquals(1,telemetry.kills(first));
        assertEquals(1,telemetry.deaths(second));
        assertTrue(CombatView.feed("A ⚔ B",true).endsWith("최종 탈락"));
        assertTrue(CombatView.feed("환경 피해 · B",false).endsWith("사망"));
        telemetry.start(List.of(first,second));
        assertEquals(0,telemetry.kills(first));
        assertEquals(2,snapshot.size());
    }
}
