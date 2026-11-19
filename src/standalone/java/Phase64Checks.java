import io.github.augmentwild.domain.CombatIntel;
import io.github.augmentwild.domain.SupplyPlan;
import io.github.augmentwild.domain.Tracker;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class Phase64Checks {
    private static int count;
    private static final long SECOND = 1_000_000_000L;

    private static void require(boolean result, String label) {
        if (!result) throw new AssertionError(label);
        count++;
    }

    public static void main(String[] args) {
        UUID match = new UUID(1, 0);
        var origin = new Tracker.Player(new UUID(0, 0), "origin", match, "world", null, Tracker.Status.ACTIVE, 0, 64, 0);
        var near = new Tracker.Player(new UUID(0, 1), "near", match, "world", null, Tracker.Status.ACTIVE, 3, 64, 0);
        var tie = new Tracker.Player(new UUID(0, 2), "tie", match, "world", null, Tracker.Status.ACTIVE, -3, 64, 0);
        var high = new Tracker.Player(new UUID(0, 3), "high", match, "world", null, Tracker.Status.ACTIVE, 0, 100, 0);
        var tracker = new Tracker();
        require(tracker.nearest(origin, List.of(high, tie, origin, near)).orElseThrow().id().equals(near.id()), "distance and deterministic tie");
        require(tracker.nearest(origin, List.of(origin, high)).filter(target -> target.distance() <= 35).isEmpty(),
                "exclude self and out-of-range");
        require(Tracker.direction(3, 0).equals("E") && Tracker.direction(-3, 0).equals("W")
                && Tracker.direction(0, 0).equals("HERE"), "only directional output");
        var track = new CombatIntel.Tracking();
        var debits = new AtomicInteger();
        java.util.function.BooleanSupplier pay = () -> debits.incrementAndGet() > 0;
        require(track.start(null, 0, 10 * SECOND, 60 * SECOND, pay) == CombatIntel.Tracking.Start.NO_TARGET
                && debits.get() == 0, "no valid enemy no debit");
        require(track.start(near.id(), 0, 10 * SECOND, 60 * SECOND, () -> false) == CombatIntel.Tracking.Start.NO_FUNDS
                && !track.busy(0), "insufficient funds no cooldown");
        require(track.start(near.id(), 0, 10 * SECOND, 60 * SECOND, pay) == CombatIntel.Tracking.Start.STARTED
                && debits.get() == 1, "one debit");
        require(track.start(tie.id(), SECOND, 10 * SECOND, 60 * SECOND, pay) == CombatIntel.Tracking.Start.COOLDOWN
                && debits.get() == 1, "duplicate start rejected");
        require(track.target(10 * SECOND - 1).equals(near.id()) && track.target(10 * SECOND) == null, "exact duration");
        track.stop();
        require(track.target(SECOND) == null && track.busy(60 * SECOND - 1) && !track.busy(60 * SECOND), "target invalidation preserves cooldown");

        var gaze = new CombatIntel.Dwell();
        require(!gaze.observe(near.id(), 0, 2 * SECOND), "first sight");
        require(!gaze.observe(near.id(), 2 * SECOND - 1, 2 * SECOND), "continuous time required");
        require(gaze.observe(near.id(), 2 * SECOND, 2 * SECOND), "two second threshold");
        require(!gaze.observe(tie.id(), 3 * SECOND, 2 * SECOND), "new target resets");
        require(!gaze.observe(null, 4 * SECOND, 2 * SECOND), "blocked sight or released item resets");
        require(!gaze.observe(tie.id(), 5 * SECOND, 2 * SECOND), "reacquire starts over");
        var cards = new ArrayList<>(List.of("기존 증강"));
        var snapshot = new CombatIntel.TimedReport<>(cards, 15 * SECOND);
        cards.set(0, "교환 후 증강");
        require(snapshot.values().equals(List.of("기존 증강")) && snapshot.valid(15 * SECOND - 1)
                && !snapshot.valid(15 * SECOND), "immutable timed snapshot");

        var history = new CombatIntel.History(3, 10 * SECOND);
        UUID originHit = UUID.randomUUID();
        history.add(new CombatIntel.Damage(0, near.id(), "A", CombatIntel.Kind.MELEE, "ENTITY_ATTACK", null, originHit, 3, 1));
        history.add(new CombatIntel.Damage(SECOND, near.id(), "A", CombatIntel.Kind.AUGMENT, "ENTITY_ATTACK", "clone_hand", originHit, 2, 0));
        history.add(new CombatIntel.Damage(2 * SECOND, tie.id(), "B", CombatIntel.Kind.PROJECTILE, "PROJECTILE", null, null, 4, 2));
        var report = history.snapshot(10 * SECOND);
        require(report.size() == 3 && report.stream().mapToDouble(CombatIntel.Damage::health).sum() == 9
                && report.stream().mapToDouble(CombatIntel.Damage::shield).sum() == 3, "only distinct delivered events, separated shield");
        require(report.stream().map(CombatIntel.Damage::attacker).distinct().count() == 2, "multi-attacker attribution");
        require(history.snapshot(10 * SECOND + 1).size() == 2 && history.snapshot(13 * SECOND).isEmpty(), "history expiry");

        var bounds = List.of(new SupplyPlan.Window(360, 600), new SupplyPlan.Window(840, 1200), new SupplyPlan.Window(1380, 1680));
        var rng = new Random(64);
        var seen = new java.util.HashSet<Integer>();
        boolean valid = true;
        for (int trial = 0; trial < 1000; trial++) {
            var plans = SupplyPlan.draw(bounds, rng);
            seen.add(plans.getFirst().second());
            for (int i = 0; i < 3; i++) {
                valid &= plans.get(i).premium() == (i == 2)
                        && plans.get(i).second() >= bounds.get(i).first() && plans.get(i).second() <= bounds.get(i).last();
            }
        }
        require(valid && seen.size() > 200, "exact reward grades and random timing within all windows");
        var fixed = new SupplyPlan.Drop("normal-supply-1", false, 480);
        require(fixed.announceAt(120) == 360 && fixed.publishAt(60) == 420, "exact announce and coordinates boundaries");
        var claim = new SupplyPlan.Claim();
        require(claim.reserve(near.id()) && !claim.reserve(tie.id()), "concurrent opener cannot reserve");
        claim.finish(near.id(), false);
        require(claim.reserve(tie.id()), "failed delivery releases claim");
        claim.finish(tie.id(), true);
        require(!claim.reserve(near.id()), "successful delivery consumes once");
        for (int size : new int[]{2, 8, 16}) load(size, match);
        System.out.println(count + " Phase 6.4 domain assertions passed");
    }

    private static void load(int size, UUID match) {
        var players = new ArrayList<Tracker.Player>();
        var histories = new ArrayList<CombatIntel.History>();
        var tracker = new Tracker();
        for (int index = 0; index < size; index++) {
            players.add(new Tracker.Player(new UUID(1, index), "probe", match, "fixture", null, Tracker.Status.ACTIVE,
                    index * 3, 64, index * 4));
            histories.add(new CombatIntel.History(128, 10 * SECOND));
        }
        int cycles = 2_000;
        long started = System.nanoTime();
        int samples = 0;
        for (int tick = 0; tick < cycles; tick++) {
            long now = tick * 50_000_000L;
            for (int index = 0; index < size; index++) {
                var next = tracker.nearest(players.get(index), players).orElseThrow();
                histories.get(index).add(new CombatIntel.Damage(now, next.id(), "probe", CombatIntel.Kind.MELEE,
                        "ENTITY_ATTACK", null, null, 1, 0));
                samples++;
            }
        }
        double millis = (System.nanoTime() - started) / 1_000_000.0;
        require(histories.stream().allMatch(history -> history.snapshot((cycles - 1) * 50_000_000L).size() == 128),
                "bounded histories at " + size);
        System.out.printf(java.util.Locale.ROOT, "SYNTHETIC_INTEL_DOMAIN players=%d cycles=%d samples=%d elapsed_ms=%.3f%n",
                size, cycles, samples, millis);
    }
}
