package io.github.augmentwild.match;

import io.github.augmentwild.augment.AugmentProgress;
import io.github.augmentwild.augment.LinkedDamageBudget;
import io.github.augmentwild.augment.ResonanceShield;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AugmentResonanceTest {
    @Test
    void separatesRemaindersAndCarriesThemThroughTierChanges() {
        var current = new AugmentProgress();
        assertEquals(1, current.addPermanent("steel_heart", 1, 17500, 10));
        assertEquals(1, current.addPermanent("blood_record", 1, 12500, 10));
        assertEquals(3, current.addPermanent("steel_heart", 1, 12500, 10));
        assertEquals(7, current.addPermanent("steel_heart", 2, 22500, 10));
        assertEquals(10, current.addPermanent("steel_heart", 2, 22500, 10));
        current.reset("steel_heart");
        assertEquals(1, current.addPermanent("steel_heart", 1, 12500, 10));
    }

    @Test
    void shieldPreviewIsIdempotentUntilEventCommits() {
        var shield = new ResonanceShield();
        shield.grant(100, 8, 600);
        assertEquals(3, shield.preview(200, 3));
        assertEquals(3, shield.preview(200, 3));
        assertEquals(8, shield.remaining(200));
        shield.consume(200, 3);
        assertEquals(5, shield.remaining(699));
        assertEquals(0, shield.remaining(700));
    }

    @Test
    void rejectedLinkedHitReleasesOnlyTheEnforcedReservation() {
        UUID victim = UUID.randomUUID();
        var budget = new LinkedDamageBudget(16, true, victim, 4);
        assertEquals(10, budget.offer(victim, 10).allowed());
        budget.refund(victim, 10);
        var next = budget.offer(victim, 20);
        assertTrue(next.overBudget());
        assertEquals(12, next.allowed());
    }
}
