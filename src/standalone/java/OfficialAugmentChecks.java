import io.github.augmentwild.augment.AugmentProgress;
import io.github.augmentwild.augment.DamageChain;
import io.github.augmentwild.augment.MovementProgress;
import io.github.augmentwild.augment.NaturalOreTracker;
import io.github.augmentwild.augment.ReviveResolver;
import java.util.List;
import java.util.UUID;

public class OfficialAugmentChecks {
    private static int checks;

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks++;
    }

    public static void main(String[] args) {
        var progress = new AugmentProgress();
        check(progress.add("steel", Integer.MAX_VALUE, 64) == 64, "first clamp");
        check(progress.add("steel", Integer.MAX_VALUE, 64) == 64, "no overflow");
        try {
            progress.add("steel", -1, 64);
            throw new AssertionError("negative gain");
        } catch (IllegalArgumentException expected) {
            check(progress.value("steel") == 64, "negative gain is rejected");
        }
        check(progress.cooldown("steel", 2, 10), "initial proc");
        check(!progress.cooldown("steel", 11, 10), "still cooling");
        check(progress.cooldown("steel", 12, 10), "exact boundary");

        var options = List.of(new ReviveResolver.Option("phoenix", 1), new ReviveResolver.Option("doll", 3));
        check("doll".equals(ReviveResolver.resolve(progress, options, id -> id.equals("doll"))), "fall through on failure");
        check(progress.value("revive:phoenix") == 0, "failed option unconsumed");
        check("phoenix".equals(ReviveResolver.resolve(progress, options, id -> true)), "successful priority");
        check(progress.value("revive:doll") == 1, "one consumed per lethal hit");
        check("doll".equals(ReviveResolver.resolve(progress, options, id -> true)), "fallback when exhausted");
        check("doll".equals(ReviveResolver.resolve(progress, options, id -> true)), "third doll use");
        check(ReviveResolver.resolve(progress, options, id -> true) == null, "no remaining revive");

        var world = UUID.randomUUID();
        var match = UUID.randomUUID();
        var attacker = UUID.randomUUID();
        var victim = UUID.randomUUID();
        var another = UUID.randomUUID();
        var chain = new DamageChain(2, 2);
        check(!chain.hit(match, attacker, victim, "sword", 8, false, () -> true), "root required");
        chain.root(() -> {
            check(!chain.hit(match, attacker, attacker, "sword", 8, false, () -> true), "self-hit denied");
            check(!chain.hit(match, attacker, victim, "sword", Double.NaN, false, () -> true), "invalid damage denied");
            check(chain.hit(match, attacker, victim, "sword", 8, true, () -> {
                var root = chain.current();
                check(root.depth() == 1 && root.critical() && !root.mayLifesteal(), "synthetic provenance");
                check(!chain.hit(match, attacker, victim, "sword", 8, false, () -> true), "same proc denied");
                return chain.hit(match, attacker, victim, "clone", 2, false, () -> {
                    check(chain.current().original().equals(root.original()), "shared chain id");
                    check(!chain.hit(match, attacker, another, "steel", 3, false, () -> true), "depth enforced");
                    return true;
                });
            }), "bounded nested synthetic damage");
            check(!chain.hit(match, attacker, victim, "clone", 2, false, () -> true), "target cap enforced");
        });
        check(chain.current() == null, "context cleaned");
        chain.root(() -> check(chain.hit(match, attacker, victim, "sword", 8, false, () -> true), "budget reset"));

        var ore = new NaturalOreTracker(2);
        var placed = new NaturalOreTracker.Site(world, 1, 2, 3);
        var natural = new NaturalOreTracker.Site(world, 1, 3, 3);
        var remote = new NaturalOreTracker.Site(another, 1, 3, 3);
        ore.invalidate(placed);
        check(!ore.consume(placed), "placed ore excluded");
        check(ore.consume(natural), "natural ore credited");
        check(!ore.consume(natural), "same coordinate excluded");
        check(!ore.consume(remote), "capacity fails closed");
        ore.clear();
        check(ore.consume(remote), "new match resets tracking");

        var movement = new MovementProgress();
        check(MovementProgress.horizontal(0, 0, 1) == 0, "zero movement ignored");
        check(MovementProgress.horizontal(10, .1, 1) == 0, "corrections above cap ignored");
        check(MovementProgress.horizontal(Double.POSITIVE_INFINITY, .1, 1) == 0, "invalid coordinates");
        check(movement.add(MovementProgress.horizontal(.3, .4, 1), 1.25) == 0, "tracks horizontal distance");
        check(movement.add(1, 1.25) == 1, "awards crossed threshold");
        check(movement.add(1, 1.25) == 1, "retains partial movement");
        System.out.println(checks + " official augment engine checks passed");
    }
}
