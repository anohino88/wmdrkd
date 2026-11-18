import io.github.augmentwild.augment.AugmentExchange;
import io.github.augmentwild.augment.AugmentProgress;
import io.github.augmentwild.augment.DamageChain;
import io.github.augmentwild.augment.MovementProgress;
import io.github.augmentwild.augment.NaturalOreTracker;
import io.github.augmentwild.augment.ReviveResolver;
import java.util.List;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.regex.Pattern;
import java.util.UUID;

public class OfficialAugmentChecks {
    private static int checks;

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks++;
    }

    public static void main(String[] args) throws Exception {
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
        var exchange = new AugmentExchange(List.of("silver_a", "silver_b"), List.of("prism_a", "prism_b", "prism_c"), 2);
        var owned = new HashSet<>(Set.of("silver_a", "silver_b"));
        check(exchange.reserves("prism_a"), "bonus offers reserved from ordinary roll");
        check(exchange.select("prism_a"), "first reward");
        check(!exchange.select("prism_a"), "duplicate reward rejected");
        check(exchange.finalizeAgainst(owned).isEmpty(), "partial selection cannot spend sacrifices");
        check(exchange.select("prism_b"), "second reward");
        check(exchange.finalizeAgainst(Set.of("silver_a")).isEmpty(), "missing sacrifice aborts exchange");
        check(exchange.finalizeAgainst(Set.of("silver_a", "silver_b", "prism_a")).isEmpty(), "conflicting reward aborts exchange");
        var committed = exchange.finalizeAgainst(owned).orElseThrow();
        check(committed.removed().equals(List.of("silver_a", "silver_b")) && committed.awarded().equals(List.of("prism_a", "prism_b")), "atomic changes returned");
        check(exchange.finalizeAgainst(owned).isEmpty() && owned.size() == 2, "single commit; exchange does not mutate the input");

        var async = new DamageChain(2, 1);
        DamageChain.Root root = new DamageChain.Root();
        async.root(root, () -> check(async.hit(match, attacker, victim, "missile", 3, false, () -> true), "first missile"));
        async.root(root, () -> check(!async.hit(match, attacker, victim, "missile", 3, false, () -> true), "preserves cap across ticks"));
        checkCatalog(Path.of(args[0]));
        System.out.println(checks + " official augment engine checks passed");
    }

    private static void checkCatalog(Path root) throws Exception {
        String spec = Files.readString(root.resolve("docs/specification-ko.md"));
        String official = spec.substring(spec.indexOf("AG. 최신 핵심 증강 30종"), spec.indexOf("AI.", spec.indexOf("AH. 추가 증강 34종")));
        var documented = new HashSet<String>();
        var ids = Pattern.compile("(?m)^[a-z][a-z_]+$").matcher(official);
        while (ids.find()) documented.add(ids.group());
        String yaml = Files.readString(root.resolve("src/main/resources/official-augments.yml"));
        var declared = new HashSet<String>();
        var declarations = Pattern.compile("(?m)^  ([a-z_]+):$").matcher(yaml.substring(0, yaml.indexOf("settings:")));
        while (declarations.find()) declared.add(declarations.group(1));
        check(documented.size() == 64 && declared.equals(documented), "catalog exactly matches AG and AH IDs");
        check(yaml.lines().filter(line -> line.equals("    implemented: true")).count() == 64, "all official entries bind effects");
        String synergies = Files.readString(root.resolve("src/main/resources/synergies.yml"));
        check(synergies.lines().filter(line -> line.equals("  enabled: true")).count() == 5, "five default synergies");
    }
}
