import io.github.augmentwild.augment.*;
import io.github.augmentwild.domain.SelectionRound;
import io.github.augmentwild.domain.MatchSimulationClock;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class AugmentChecks {
    private static int checks;

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }

    private static boolean near(double a, double b) { return Math.abs(a - b) < 1e-8; }

    private static AugmentRegistry registry() {
        var registry = new AugmentRegistry();
        for (var tier : AugmentRarity.values()) {
            for (int index = 0; index < 5; index++) {
                String id = tier.name().toLowerCase(Locale.ROOT) + "_" + index;
                registry.register(new AugmentDefinition(id, tier, id, "test", "%", AugmentDefinition.Effect.XP,
                        .5, 0, 0, true, index != 4));
            }
        }
        return registry;
    }

    private static List<SelectionRound.Offer> offers(AugmentRoller roller, int level, boolean last, Set<String> previous) {
        return roller.roll(level, last, Set.of(), previous).stream()
                .map(a -> new SelectionRound.Offer(a.id(), a.rarity() == AugmentRarity.PRISMATIC, a.implemented())).toList();
    }

    public static void main(String[] args) {
        var odds = AugmentRarity.odds(0);
        require(odds.silver() == 80 && odds.gold() == 18 && odds.prismatic() == 2, "level zero");
        odds = AugmentRarity.odds(10);
        require(near(odds.silver(), 65.4) && near(odds.gold(), 23) && near(odds.prismatic(), 11.6), "level ten");
        odds = AugmentRarity.odds(25);
        require(near(odds.silver(), 43.5) && near(odds.gold(), 30.5) && near(odds.prismatic(), 26), "level twenty five");
        odds = AugmentRarity.odds(50);
        require(near(odds.silver(), 7) && near(odds.gold(), 43) && near(odds.prismatic(), 50)
                && odds.equals(AugmentRarity.odds(100)) && AugmentRarity.odds(-2).equals(AugmentRarity.odds(0)), "clamps");
        for (int i = 0; i <= 50; i++) {
            odds = AugmentRarity.odds(i);
            require(near(odds.silver() + odds.gold() + odds.prismatic(), 100), "sum at " + i);
        }
        var registry = registry();
        var roller = new AugmentRoller(registry, new Random(13));
        require(roller.roll(20, false, Set.of(), Set.of())
                .equals(new AugmentRoller(registry, new Random(13)).roll(20, false, Set.of(), Set.of())), "seed reproducible");
        var predetermined = new Random(1) {
            private int slot;
            @Override public double nextDouble(double bound) { return new double[]{5, 40, 98}[slot++]; }
        };
        require(new AugmentRoller(registry, predetermined).roll(25, false, Set.of(), Set.of()).stream()
                .map(AugmentDefinition::rarity).toList().equals(List.of(AugmentRarity.SILVER,
                        AugmentRarity.SILVER, AugmentRarity.PRISMATIC)), "one rarity draw per slot");
        var scarce = new AugmentRegistry();
        for (var a : registry.selectable()) if (a.rarity() != AugmentRarity.SILVER) scarce.register(a);
        require(new AugmentRoller(scarce, new Random(13)).roll(0, false, Set.of(), Set.of()).size() == 3, "missing tier fallback");

        UUID a = new UUID(0, 11), b = new UUID(0, 12), stranger = new UUID(0, 13);
        var rules = MatchSimulation.rules();
        var session = MatchSimulation.create(a, b);
        var wall = new AtomicLong();
        var clock = new MatchSimulationClock(session, rules, wall::get, 15, 900, 1500);
        clock.setOfferProvider((id, round, players) -> Map.of(
                a, offers(roller, 0, clock.isFinalRound(round), Set.of()),
                b, offers(roller, 50, clock.isFinalRound(round), Set.of())));
        for (int index = 1; index <= 6; index++) {
            MatchSimulation.advance(wall, index * 300L - clock.elapsedSeconds());
            clock.tick();
            var round = clock.selection().orElseThrow();
            require(round.round() == index && clock.isPaused() && clock.selectionRemainingSeconds() == 15, "schedule " + index);
            for (var choice : round.choices().values()) {
                require(choice.offers().size() == 3 && choice.offers().stream().allMatch(SelectionRound.Offer::implemented)
                        && new HashSet<>(choice.offers()).size() == 3, "unique implemented choices");
            }
            var first = round.choices().get(a).offers();
            var previous = first.stream().map(SelectionRound.Offer::id).collect(java.util.stream.Collectors.toSet());
            var replacement = offers(roller, 0, round.prismaticOnly(), previous);
            require(!clock.choose(stranger, index, first.getFirst().id()), "other player rejected");
            require(!clock.choose(a, index - 1, first.getFirst().id()), "old round rejected");
            if (index == 1) {
                MatchSimulation.advance(wall, 5);
                require(clock.reroll(a, index, replacement) && clock.selectionRemainingSeconds() == 10, "reroll keeps wall deadline");
                require(!clock.reroll(a, index, first), "duplicate reroll rejected");
                first = replacement;
            } else {
                require(!clock.reroll(a, index, replacement), "reroll persisted across round");
            }
            if (index == 6) {
                require(first.stream().allMatch(SelectionRound.Offer::prismatic), "final guaranteed");
                var original = round.choices().get(b).offers();
                var old = original.stream().map(SelectionRound.Offer::id).collect(java.util.stream.Collectors.toSet());
                var lastReroll = offers(roller, 50, true, old);
                require(clock.reroll(b, index, lastReroll) && lastReroll.stream().allMatch(SelectionRound.Offer::prismatic)
                        && lastReroll.stream().anyMatch(o -> !old.contains(o.id())), "final reroll guaranteed and changed");
            }
            require(clock.choose(a, index, first.getFirst().id()) && !clock.choose(a, index, first.getFirst().id()),
                    "single choice accepted");
            require(!clock.reroll(a, index, replacement), "no reroll after selection");
            if (index == 1) {
                require(clock.choose(b, index, round.choices().get(b).offers().getFirst().id())
                        && clock.selection().isEmpty(), "all chosen ends early");
            } else {
                MatchSimulation.advance(wall, 15);
                clock.tick();
                require(clock.completedSelections().get(index - 1).get(b).status() == SelectionRound.Status.AUTOMATIC,
                        "deadline auto selection");
            }
        }
        session.cancel();
        require(!clock.choose(a, 6, "silver_0"), "cannot select after cancellation");
        var fresh = new MatchSimulationClock(MatchSimulation.create(a,b), rules, wall::get, 15, 900, 1500);
        require(!fresh.hasRerolled(a), "new match allowance");

        require(AugmentMath.experience(10, .5) == 15 && AugmentMath.experience(10, 1) == 20
                && AugmentMath.experience(10, 2) == 30 && AugmentMath.experience(0, 2) == 0, "XP amounts");
        require(near(AugmentMath.bonusHit(10, 10, .2), 12) && near(AugmentMath.mitigate(10, .35), 6.5), "damage math");
        var timers = new AugmentTimers();
        timers.cooldown("speed", 10, 12);
        timers.activate("speed", 10, 4);
        require(timers.active("speed", 13) && !timers.active("speed", 14), "buff expiration");
        require(timers.periodic("speed", 11) && !timers.periodic("speed", 11), "one periodic tick at paused time");
        timers.clearActive();
        require(!timers.active("speed", 12) && !timers.ready("speed", 21) && timers.ready("speed", 22), "cooldown survives buff cleanup");
        var guard = new ProcGuard(2);
        var event = new Object();
        require(guard.run(event, "effect", () -> require(guard.run(new Object(), "nested",
                () -> require(!guard.run(new Object(), "too deep", () -> { throw new AssertionError(); }), "depth")), "nested")),
                "root proc");
        require(!guard.run(event, "effect", () -> { throw new AssertionError(); }), "same event deduped");
        require(guard.run(new Object(), "effect", () -> { }), "depth released");
        System.out.println(checks + " augment checks passed");
    }
}
