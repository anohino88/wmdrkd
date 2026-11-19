package io.github.augmentwild.match;

import io.github.augmentwild.augment.AugmentCombinations;
import io.github.augmentwild.augment.AugmentEvolution;
import io.github.augmentwild.augment.AugmentProgress;
import io.github.augmentwild.augment.AugmentResonance;
import io.github.augmentwild.domain.HudMessages;
import io.github.augmentwild.domain.HudMessages.Layer;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CodexHudTest {
    @Test void higherPriorityLayersRestoreTheCurrentLowerLayerAndNeverReplayExpiredProcs() {
        var hud = new HudMessages<String>();
        hud.set(Layer.TRACK, "north");
        assertTrue(hud.offerProc("first", 10, 20));
        assertFalse(hud.offerProc("repeat", 11, 20));
        hud.set(Layer.SCOUT, "aim");
        hud.set(Layer.SUPPLY, "opening");
        hud.set(Layer.SHIELD, "shield");
        hud.set(Layer.SELECTION, "draft");
        assertEquals("draft", hud.current(20));
        hud.remove(Layer.SELECTION);
        assertEquals("shield", hud.current(20));
        hud.remove(Layer.SHIELD);
        assertEquals("opening", hud.current(20));
        hud.remove(Layer.SUPPLY);
        assertEquals("aim", hud.current(20));
        hud.remove(Layer.SCOUT);
        assertEquals("first", hud.current(29));
        hud.set(Layer.TRACK, "south");
        assertEquals("south", hud.current(30));
        hud.remove(Layer.TRACK);
        assertNull(hud.current(31));
        assertTrue(hud.offerProc("new", 31, 20));
    }

    @Test void codexReadsTheCountersUsedByAllSixEvolutions() {
        for (var evolution : AugmentEvolution.values()) {
            var progress = new AugmentProgress();
            double value;
            if (evolution == AugmentEvolution.BURN) value = evolution.progress(progress, 29.5);
            else {
                progress.add(evolution.counter, 29, 30);
                value = evolution.progress(progress, 50);
            }
            assertFalse(evolution.complete(value, 30), evolution.augment);
            if (evolution != AugmentEvolution.BURN) progress.add(evolution.counter, 1, 30);
            assertTrue(evolution.complete(evolution.progress(progress, 30), 30), evolution.augment);
            assertEquals(evolution, AugmentEvolution.of(evolution.augment));
        }
        assertEquals(6, AugmentEvolution.values().length);
    }

    @Test void presetMembershipMatchesTheSynergyAndRevivalRules() {
        assertEquals(5, AugmentCombinations.UNIQUE.size());
        assertEquals(6, AugmentCombinations.PRESETS.size());
        assertEquals(AugmentResonance.DEATH_PREVENTION, AugmentCombinations.PRESETS.get("life_echo"));
        var owned = new HashSet<String>();
        for (var entry : AugmentCombinations.UNIQUE.entrySet()) {
            var preset = AugmentCombinations.PRESETS.get(entry.getKey());
            assertEquals(entry.getValue(), preset);
            assertEquals(3, preset.size());
            owned.clear();
            assertTrue(owned.addAll(preset));
            assertFalse(owned.addAll(preset));
        }
    }
}
