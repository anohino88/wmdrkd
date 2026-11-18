import io.github.augmentwild.domain.Arena;
import io.github.augmentwild.domain.ArenaCoordinates;
import io.github.augmentwild.domain.GameSession;
import io.github.augmentwild.domain.MatchBorder;
import io.github.augmentwild.domain.MatchSimulationClock;
import io.github.augmentwild.match.MatchState;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class BorderChecks {
    private static int checks;

    private static void check(String id, boolean condition) {
        if (!condition) throw new AssertionError(id);
        checks++;
    }

    private static long nanos(long seconds) { return seconds * 1_000_000_000L; }

    public static void main(String[] args) {
        var rules = MatchSimulation.rules();
        var initial = new Arena(125, -45, 1000, 1000);
        var border = new MatchBorder(initial, rules, 1);
        check("B01 unchanged before start", border.area(1819).equals(initial));
        var start = border.update(nanos(1820), false);
        check("B02 exact start and one notification", start.area().equals(initial)
                && start.notices().contains(MatchBorder.Notice.SHRINK_STARTED)
                && border.update(nanos(1820), false).notices().isEmpty());
        check("B03 midpoint", border.area(2120).equals(new Arena(125, -45, 530, 530)));
        check("B04 exact final size", border.area(2420).equals(new Arena(125, -45, 60, 60)));
        check("B05 final size remains fixed", border.area(2600).equals(border.area(2420)));
        var finalUpdate = border.update(nanos(2420), false);
        check("Final notifications distinct and once", finalUpdate.notices().equals(List.of(
                MatchBorder.Notice.FINAL_SIZE, MatchBorder.Notice.SUDDEN_DEATH)));
        var warnings = new MatchBorder(initial, rules, 1);
        check("Farming warnings gated", warnings.update(nanos(1679), false).notices().isEmpty()
                && warnings.update(nanos(1680), false).notices().equals(List.of(MatchBorder.Notice.FARMING_TWO_MINUTES))
                && warnings.update(nanos(1770), false).notices().equals(List.of(MatchBorder.Notice.FARMING_THIRTY_SECONDS))
                && warnings.update(nanos(1800), true).notices().equals(List.of(MatchBorder.Notice.FARMING_ENDED)));

        UUID a = new UUID(0, 1), b = new UUID(0, 2);
        var session = MatchSimulation.create(a, b);
        var watch = new AtomicLong();
        var clock = new MatchSimulationClock(session, rules, watch::get, 15, 900, 1500);
        watch.set(nanos(300));
        clock.tick();
        var frozen = border.area(clock.elapsedSeconds());
        watch.addAndGet(nanos(12));
        clock.tick();
        check("B06 select pause freezes geometry", clock.isPaused() && clock.elapsedSeconds() == 300
                && border.area(clock.elapsedSeconds()).equals(frozen));

        var damage = new MatchBorder(initial, rules, 1);
        check("B07 pause freezes damage", !damage.update(nanos(1821), true).damagePulse());
        var pulse = damage.update(nanos(1821), false);
        check("B08 outside eligible", pulse.damages(true, true, false, 624.5, -45));
        check("B09 inside", !pulse.damages(true, true, false, 125, -45));
        check("B10 reentry cancels next hit", !damage.update(nanos(1822), false).damages(true, true, false, 125, -45));
        check("B11 nonparticipant and wrong world", !pulse.damages(false, true, false, 624.5, -45)
                && !pulse.damages(true, false, false, 624.5, -45));
        session.transition(MatchState.PVP);
        if (pulse.damages(true, true, false, 624.5, -45)) session.death(a, nanos(1821));
        check("B12 simulation uses existing death reservation", session.deadline(a).orElseThrow() == nanos(1841)
                && session.player(a).life().lives() == 1);
        check("B13 stale candidate rejected by updated boundary",
                damage.area(1820).contains(612, -45, 3) && !damage.area(1841).contains(612, -45, 3));
        var end = damage.area(2420);
        var column = ArenaCoordinates.sample(end, 3, new Random(44)).orElseThrow();
        check("B14 final-size spawn sample", end.contains(column.x() + .5, column.z() + .5, 3));
        check("B15 protection", !pulse.damages(true, true, true, 624.5, -45));
        session.cancel();
        check("B16 domain reservation cleanup", session.reservation(a).isEmpty());
        check("B18 a new match starts clean", new MatchBorder(initial, rules, 1).update(nanos(1821), false).damagePulse());
        check("B20 delayed tick gives one pulse", damage.update(nanos(2000), false).damagePulse()
                && !damage.update(nanos(2000), false).damagePulse()
                && !damage.update(nanos(2000) + 500_000_000, false).damagePulse());
        var configured = new MatchBorder(initial, rules, 2);
        check("Configured interval", !configured.update(nanos(1821), false).damagePulse()
                && configured.update(nanos(1822), false).damagePulse());
        System.out.println(checks + " standalone border checks passed");
    }
}
