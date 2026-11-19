import io.github.augmentwild.domain.CombatIntel;
import io.github.augmentwild.domain.MatchTelemetry;
import io.github.augmentwild.domain.MatchTelemetry.Counter;
import io.github.augmentwild.domain.SupplyPlan;
import java.util.UUID;

public final class Phase65Checks {
    private static int passed;

    private static void check(boolean expected, String why) {
        if (!expected) throw new AssertionError(why);
        passed++;
    }

    public static void main(String[] args) {
        UUID a = new UUID(0, 1), b = new UUID(0, 2), stranger = new UUID(0, 3);
        var stats = new MatchTelemetry();
        stats.count(Counter.CREATED);
        check(stats.finish().isEmpty(), "outside-match samples ignored");
        stats.start();
        stats.count(Counter.CREATED);
        stats.add(Counter.CLAIM_DELAY_MS, 4300);
        stats.count(Counter.SCOUT_ATTEMPTS);
        stats.count(Counter.SCOUT_ATTEMPTS);
        stats.scout(a, b, 100);
        stats.engage(a, stranger, 2);
        stats.engage(b, a, 10);
        stats.engage(a, b, 11);
        stats.scout(a, b, 200);
        stats.engage(a, b, 200);
        var counts = stats.finish();
        check(counts.get(Counter.CREATED) == 1 && counts.get(Counter.CLAIM_DELAY_MS) == 4300, "match-only aggregate");
        check(counts.get(Counter.SCOUT_ATTEMPTS) == 2 && counts.get(Counter.SCOUT_SUCCESSES) == 2
                && counts.get(Counter.SCOUT_ENGAGEMENTS) == 1, "only matched, in-window, first engagement");
        check(!counts.toString().contains(a.toString()), "output omits participant IDs");
        stats.start();
        check(stats.finish().isEmpty(), "no prior match counters");

        var aim = new CombatIntel.Dwell();
        aim.observe(a, 0, 200);
        check(aim.progress(120, 200) == 60, "aim percentage");
        aim.observe(b, 120, 200);
        check(aim.progress(120, 200) == 0, "changed target resets percentage");
        aim.observe(null, 130, 200);
        check(aim.progress(180, 200) == 0, "release resets percentage");
        var tracking = new CombatIntel.Tracking();
        tracking.start(b, 5, 10, 60, () -> true);
        check(tracking.remaining(8) == 7, "tracking countdown");
        check(tracking.remaining(15) == 0, "countdown expiry");

        var claim = new SupplyPlan.Claim();
        check(claim.reserve(a) && !claim.reserve(b), "same-tick opener held out");
        claim.finish(a, false);
        check(claim.reserve(b), "known rollback permits retry");
        claim.finish(b, true);
        check(!claim.reserve(a), "delivered or uncertain outcome cannot replay");

        var damage = new CombatIntel.Damage(0, a, "A", CombatIntel.Kind.MELEE, "ENTITY_ATTACK", null, null, 5, 3, 2);
        check(damage.health() == 5 && damage.shield() == 3 && damage.absorption() == 2, "three independent damage channels");
        System.out.println(passed + " Phase 6.5 domain assertions passed");
    }
}
