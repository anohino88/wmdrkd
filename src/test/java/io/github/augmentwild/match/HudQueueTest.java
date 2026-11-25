package io.github.augmentwild.match;

import io.github.augmentwild.domain.HudMessages;
import io.github.augmentwild.domain.HudMessages.Kind;
import io.github.augmentwild.domain.HudMessages.Layer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class HudQueueTest {
    private static long ms(long value) { return value*1_000_000L; }

    private static void enqueue(HudMessages<String> hud,Kind kind,String name,long time) {
        hud.offer(kind,name,ms(time),count -> name + (count == 1 ? "" : " ×" + count));
    }

    @Test void distinctProcsGetTheirOwnReadTimeAndRepeatedProcUsesOneSlot() {
        var metrics = new HudMessages.Metrics();
        var hud = new HudMessages<String>(metrics);
        enqueue(hud,Kind.PROC,"steel",0);
        assertEquals("steel",hud.current(0));
        enqueue(hud,Kind.PROC,"steel",100);
        enqueue(hud,Kind.PROC,"shield",100);
        enqueue(hud,Kind.PROC,"magic",100);
        assertEquals("steel ×2",hud.current(ms(1000)));
        assertEquals("shield",hud.current(ms(1400)));
        assertEquals("magic",hud.current(ms(2800)));
        assertNull(hud.current(ms(4200)));
        assertEquals(3,metrics.completed(Kind.PROC));
        assertEquals(ms(4200),metrics.visibleNanos(Kind.PROC));
        assertEquals(1,metrics.merged());
    }

    @Test void urgentWarningPreemptsAndResumesOnlyTheUnreadPart() {
        var hud = new HudMessages<String>();
        enqueue(hud,Kind.COMBAT,"damage",0);
        assertEquals("damage",hud.current(0));
        hud.set(Layer.DANGER,"burst");
        assertEquals("burst",hud.current(ms(400),"border"));
        hud.remove(Layer.DANGER);
        assertEquals("border",hud.current(ms(700),"border"));
        assertEquals("damage",hud.current(ms(900)));
        assertEquals("damage",hud.current(ms(1499)));
        assertNull(hud.current(ms(1500)));
    }

    @Test void fullQueueRetainsVisibleMessageAndCountsDisplacementAndTimeout() {
        var metrics = new HudMessages.Metrics();
        var hud = new HudMessages<String>(metrics);
        enqueue(hud,Kind.PROC,"visible",0);
        hud.current(0);
        for (int i=0;i<7;i++) enqueue(hud,Kind.PROC,"other"+i,1);
        enqueue(hud,Kind.INPUT,"lock",2);
        assertEquals(HudMessages.CAPACITY,hud.queued());
        assertEquals(3,metrics.budget());
        assertEquals("visible",hud.current(ms(500)));
        assertEquals("lock",hud.current(ms(1400)));
        assertEquals("danger",hud.current(ms(1700),"danger"));
        assertNull(hud.current(ms(6600)));
        assertEquals(5,metrics.expired());
    }

    @Test void repeatedFailedInputStaysOneAlertWhileHealAndDamageStaySeparate() {
        var metrics = new HudMessages.Metrics();
        var hud = new HudMessages<String>(metrics);
        enqueue(hud,Kind.COMBAT,"heal",0);
        enqueue(hud,Kind.COMBAT,"hit by other",0);
        enqueue(hud,Kind.INPUT,"locked",0);
        enqueue(hud,Kind.INPUT,"locked",200);
        assertEquals("locked",hud.current(ms(200)));
        assertEquals("heal",hud.current(ms(2000)));
        assertEquals("hit by other",hud.current(ms(3000)));
        assertNull(hud.current(ms(4000)));
        assertEquals(1,metrics.merged());
        assertEquals(1,metrics.expired());
    }
}
