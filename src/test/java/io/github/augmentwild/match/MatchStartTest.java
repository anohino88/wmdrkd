package io.github.augmentwild.match;

import io.github.augmentwild.domain.ArenaSearchBounds;
import io.github.augmentwild.domain.GameSession;
import io.github.augmentwild.domain.MatchRules;
import io.github.augmentwild.domain.OceanSampling;
import io.github.augmentwild.domain.StartRoster;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MatchStartTest {
    private static final double WORLD_LIMIT = 29_999_984;
    private static final MatchRules RULES = new MatchRules(1800,1820,20,2,1000,5000,40,
            1000,60,600,1,-1,300,3,1,1,1,2);

    private static List<UUID> online(int count) {
        return IntStream.range(0,count).mapToObj(i -> new UUID(0,i)).toList();
    }

    @Test void selectsOneTwoAndSixteenWithoutSignupAndCapsSeventeen() {
        for (int count : new int[]{1,2,16,17}) {
            var roster = StartRoster.select(List.of(),online(count),true,16);
            assertEquals(Math.min(count,16),roster.automatic());
            assertEquals(Math.min(count,16),roster.players().size());
            assertEquals(Math.max(0,count-16),roster.capacityExcluded());
        }
    }

    @Test void preservesExplicitPriorityAndSkipsIneligibleEntries() {
        var players = online(18);
        var eligible = players.subList(1,18); // opted out or unavailable
        var signup = List.of(players.getLast(),players.getFirst(),players.getLast());
        var roster = StartRoster.select(signup,eligible,true,16);
        assertEquals(players.getLast(),roster.players().getFirst());
        assertFalse(roster.players().contains(players.getFirst()));
        assertEquals(15,roster.automatic());
        assertEquals(1,roster.capacityExcluded());
        assertEquals(List.of(players.getLast()),StartRoster.select(signup,eligible,false,16).players());
        assertEquals(0,StartRoster.select(signup,List.of(),true,16).players().size());
    }

    @Test void practiceNeverWinsAndEndsOnFinalElimination() {
        UUID trainee = new UUID(0,1);
        var session = new GameSession(RULES);
        session.join(trainee);
        session.enablePractice();
        assertTrue(session.winner().isEmpty());
        session.transition(MatchState.PREPARING);
        session.transition(MatchState.GATHERING);
        session.transition(MatchState.PVP);
        assertEquals(GameSession.Outcome.IN_PROGRESS,session.resolveTick(List.of(),0).outcome());
        session.transition(MatchState.SUDDEN_DEATH);
        assertEquals(GameSession.Outcome.IN_PROGRESS,session.resolveTick(List.of(),0).outcome());
        assertEquals(GameSession.Outcome.CANCELLED,session.resolveTick(List.of(trainee),0).outcome());
        assertTrue(session.winner().isEmpty());
    }

    @Test void regularMatchStillAwardsOneWinner() {
        var players = online(2);
        var session = new GameSession(RULES);
        players.forEach(session::join);
        session.transition(MatchState.PREPARING);
        session.transition(MatchState.GATHERING);
        session.transition(MatchState.PVP);
        session.transition(MatchState.SUDDEN_DEATH);
        assertEquals(new GameSession.Result(GameSession.Outcome.WINNER,players.getFirst()),
                session.resolveTick(List.of(players.getLast()),0));
    }

    @Test void borderBoundsHandleExactFitFractionalCenterClearanceAndExpansion() {
        assertTrue(ArenaSearchBounds.fit(0,0,500,WORLD_LIMIT,1000,1000,0).empty());
        assertTrue(ArenaSearchBounds.fit(0.25,0,1000,WORLD_LIMIT,1000,1000,0).empty());
        assertTrue(ArenaSearchBounds.fit(0,0,1000,WORLD_LIMIT,1000,1000,1).empty());
        var exact = ArenaSearchBounds.fit(0,0,1000,WORLD_LIMIT,1000,1000,0);
        var samples = new ArenaSearchBounds.Candidates();
        var random = new Random(7);
        assertEquals(new ArenaSearchBounds.Corner(-500,-500),samples.take(exact,null,random));
        assertNull(samples.take(exact,null,random));

        for (int size : new int[]{2000,5000}) {
            var offset = ArenaSearchBounds.fit(6200,-5000,size,WORLD_LIMIT,1000,1000,1);
            assertTrue(offset.near(0,0,1000,1000,128).empty());
            var partial = offset.near(6200,-5000,1000,1000,4096);
            var tried = new ArenaSearchBounds.Candidates();
            for (int i = 0; i < 120; i++) {
                var corner = tried.take(partial,null,random);
                assertTrue(offset.contains(corner));
            }
        }
        assertFalse(ArenaSearchBounds.fit(0,0,500,WORLD_LIMIT,128,128,0).empty());
    }

    @Test void stratifiedWeightsCoverEvenPartialStrataAndRefineNearFivePercent() {
        assertEquals(1000,Arrays.stream(OceanSampling.axisWeights(1000,32)).sum());
        assertEquals(32,Arrays.stream(OceanSampling.axisWeights(1000,32)).filter(w -> w > 0).count());
        assertEquals(125,Arrays.stream(OceanSampling.axisWeights(1000,8)).filter(w -> w > 0).count());
        assertFalse(OceanSampling.refine(0,0.05,0.02));
        assertTrue(OceanSampling.refine(0.03,0.05,0.02));
        assertTrue(OceanSampling.refine(0.05,0.05,0.02));
        assertFalse(OceanSampling.refine(0.10,0.05,0.02));
        for (int percent : new int[]{0,3,5,10}) {
            assertEquals(percent > 5,OceanSampling.exceeds(percent * 10_000L,1_000_000L,0.05));
        }
    }
}
