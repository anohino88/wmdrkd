import io.github.augmentwild.domain.*;
import io.github.augmentwild.domain.MatchTelemetry.Counter;
import io.github.augmentwild.match.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

public final class Phase66Checks {
    private static final long SECOND = 1_000_000_000L;

    public static void main(String[] args) {
        for (int size : new int[]{2, 4, 8, 16}) match(size);
        var stats = new MatchTelemetry();
        UUID a = new UUID(0, 1), b = new UUID(0, 2), stranger = new UUID(0, 3);
        stats.start(List.of(b, a));
        stats.engage(stranger, a, SECOND);
        stats.engage(a, a, SECOND);
        stats.engage(a, b, 290 * SECOND);
        stats.engage(b, a, 299 * SECOND);
        stats.engage(b, a, 301 * SECOND);
        stats.engage(a, b, 311 * SECOND);
        stats.death(a, b);
        stats.death(b, null);
        stats.synergy(a, "steel_king");
        stats.synergy(a, "steel_king");
        stats.synergy(b, "steel_king");
        stats.evolution(a, "steel_heart", 750);
        stats.evolution(a, "steel_heart", 760);
        stats.survivors(1820, 2);
        stats.supplyClaim("normal-supply-1", 3200);
        stats.supplyClaim("normal-supply-1", 4100);
        stats.supplyClaim("premium-supply", 5000);
        stats.count(Counter.DRAFTS);
        stats.count(Counter.MANUAL_PICKS);
        stats.count(Counter.REROLLS);
        for (int i = 0; i < 18; i++) stats.tick(10.25);
        stats.tick(50);
        stats.tick(73.25);
        stats.tick(Double.NaN);
        String report = stats.tempo(2420, 2495);
        for (String expected : List.of("first_pvp_s=290", "engagements_by_5m={0=1, 1=1}", "kills_deaths=[P1=0/1, P2=1/1]",
                "synergies={steel_king=2}", "evolutions_s={P1/steel_heart=750}", "survivors_by_game_s={1820=2}",
                "supply_claim_ms={normal-supply-1=3200, premium-supply=5000}", "tick_samples=20", "tick_p95_ms=50", "tick_max_ms=73.250", "tick_over_50=1/20")) {
            require(report.contains(expected), expected + " in " + report);
        }
        require(!report.contains(a.toString()), "no personal identifier in aggregate");
        require(stats.finish().equals(Map.of(Counter.DRAFTS, 1L, Counter.MANUAL_PICKS, 1L, Counter.REROLLS, 1L,
                Counter.OPEN_SUCCESSES, 2L, Counter.CLAIM_DELAY_MS, 8200L)), "draft and supply counts");
        stats.start(List.of(b));
        require(stats.tempo(0, 0).contains("first_pvp_s=-1 engagements_by_5m={} kills_deaths=[P1=0/0]")
                && stats.tempo(0, 0).contains("tick_p95_ms=NA"), "next match starts clean");
        System.out.println("SYNTHETIC_TEMPO " + report);
    }

    private static void match(int size) {
        var players = IntStream.rangeClosed(1, size).mapToObj(i -> new UUID(0, i)).toList();
        var rules = MatchSimulation.rules();
        var game = MatchSimulation.create(players.toArray(UUID[]::new));
        var wall = new AtomicLong();
        var drops = SupplyPlan.draw(List.of(new SupplyPlan.Window(360, 600), new SupplyPlan.Window(840, 1200),
                new SupplyPlan.Window(1380, 1680)), new Random(66));
        var timer = new MatchSimulationClock(game, rules, wall::get, 15, drops.stream()
                .map(drop -> new Timeline.Event(drop.id(), drop.second(), 1, false)).toList());
        var normal = offers("normal");
        var prism = offers("prism");
        timer.setOfferProvider((match, round, ids) -> {
            var choices = new HashMap<UUID, List<SelectionRound.Offer>>();
            for (UUID id : ids) choices.put(id, round == 6 ? prism : normal);
            return choices;
        });

        UUID survivor = players.getFirst(), defeated = players.getLast();
        require(game.death(defeated, 0), "farming death");
        game.connected(defeated, false);
        require(!game.respawn(defeated, 20 * SECOND), "offline respawn waits");
        game.connected(defeated, true);
        require(!game.respawn(defeated, 19 * SECOND) && game.respawn(defeated, 20 * SECOND)
                && game.player(defeated).life().lives() == 2, "farming respawn preserves lives");

        long pausedSeconds = 0;
        for (int round = 1; round <= 6; round++) {
            long elapsed = round * 300L;
            wall.set((elapsed + pausedSeconds) * SECOND);
            timer.tick();
            var selection = timer.selection().orElseThrow();
            require(selection.choices().size() == size && selection.prismaticOnly() == (round == 6), "six rounds and final tier");
            if (round == 1) require(timer.reroll(survivor, round, offers("replacement")), "first reroll");
            if (round == 2) require(!timer.reroll(survivor, round, offers("replacement")), "second reroll refused");
            var chosen = selection.choices().get(survivor).offers().getFirst();
            require(timer.choose(survivor, round, chosen.id()), "manual choice");
            wall.addAndGet(14 * SECOND);
            timer.tick();
            require(timer.isPaused() && timer.elapsedSeconds() == elapsed, "match time frozen");
            wall.addAndGet(SECOND);
            pausedSeconds += 15;
            timer.tick();
            require(!timer.isPaused() && timer.elapsedSeconds() == elapsed
                    && timer.completedSelections().getLast().get(defeated).status() == SelectionRound.Status.AUTOMATIC, "timeout");
        }
        require(drops.stream().allMatch(drop -> Collections.frequency(timer.events(), drop.id()) == 1), "three logical deliveries");
        wall.addAndGet(20 * SECOND);
        timer.tick();
        require(game.state() == MatchState.PVP && timer.elapsedSeconds() == 1820, "30:20 life limit");
        var border = new MatchBorder(new Arena(0, 0, 1000, 1000), rules, 1);
        require(border.area(1820).width() == 1000 && border.area(2120).width() == 530 && border.area(2420).width() == 60, "shrink");
        require(game.death(defeated, timer.elapsedNanos()) && game.player(defeated).life().lives() == 1, "first limited life");
        wall.addAndGet(20 * SECOND);
        timer.tick();
        require(game.respawn(defeated, timer.elapsedNanos()), "limited respawn");
        wall.addAndGet(580 * SECOND);
        timer.tick();
        require(game.state() == MatchState.SUDDEN_DEATH && timer.elapsedSeconds() == 2420, "40:20 sudden death");
        var result = game.resolveTick(players.subList(1, size), timer.elapsedNanos());
        require(survivor.equals(result.winner()), "single winner after simultaneous sudden death");
        System.out.printf("SYNTHETIC_MATCH participants=%d rounds=6 supplies=3 game_s=%d virtual_wall_s=%d winner=P1%n",
                size, timer.elapsedSeconds(), wall.get() / SECOND);
    }

    private static List<SelectionRound.Offer> offers(String prefix) {
        return IntStream.range(0, 3).mapToObj(i -> new SelectionRound.Offer(prefix + i, prefix.equals("prism"), true)).toList();
    }

    private static void require(boolean expected, String label) {
        if (!expected) throw new AssertionError(label);
    }
}
