import io.github.augmentwild.augment.AugmentProgress;
import io.github.augmentwild.augment.AugmentResonance;
import io.github.augmentwild.augment.CombatAudit;
import io.github.augmentwild.augment.DamageChain;
import io.github.augmentwild.augment.SynergyDraftWeights;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Phase63Checks {
    private static int count;

    private static void check(boolean result, String label) {
        if (!result) throw new AssertionError(label);
        count++;
    }

    public static void main(String[] args) throws Exception {
        var root = Path.of(args[0]);
        var registry = ResonanceChecks.loadCatalog(root);
        var records = new AugmentProgress();
        var config = Files.readString(root.resolve("src/main/resources/official-augments.yml"));
        check(AugmentResonance.PERMANENT.size() == 13, "permanent group size");
        for (String id : AugmentResonance.PERMANENT) {
            check(registry.get(id) != null, id + " has a catalog ID");
            int[] expected = {1, 3, 5, 7};
            for (int stack : expected) check(records.addPermanent(id, 1, 17_500, 200) == stack, id + " independent fractional state");
            records.addPermanent(id, 1, 12_500, 8);
            records.reset(id);
            check(records.addPermanent(id, 1, 12_500, 200) == 1, id + " removal clears carry");
        }
        var evolution = Map.of("steel-evolution-stacks", 20, "missile-evolution-shots", 100,
                "sword-evolution-hits", 30, "burn-evolution-damage", 200,
                "tank-evolution-stacks", 15, "berserk-evolution-seconds", 30);
        for (var rule : evolution.entrySet()) {
            check(config.lines().anyMatch(line -> line.trim().equals(rule.getKey() + ": " + rule.getValue())
                    || line.trim().equals(rule.getKey() + ": " + rule.getValue() + ".0")), "live config evolution " + rule.getKey());
            int threshold = rule.getValue();
            check(records.add(rule.getKey(), threshold - 1, threshold) == threshold - 1, "before boundary");
            check(records.add(rule.getKey(), 1, threshold) == threshold, "exact boundary");
            check(records.add(rule.getKey(), 1, threshold) == threshold, "saturates once");
        }
        var weights = new SynergyDraftWeights(ResonanceChecks.SYNERGIES, 1.5, 2.5);
        for (var group : ResonanceChecks.SYNERGIES) {
            var pieces = group.stream().sorted().toList();
            check(pieces.size() == 3 && pieces.stream().allMatch(id -> registry.get(id) != null), "registered synergy members");
            check(weights.applyAsDouble(pieces.getLast(), Set.copyOf(pieces.subList(0, 2))) == 2.5, "completion candidate");
            check(weights.applyAsDouble(pieces.getLast(), Set.of(pieces.getFirst())) == 1.5, "exchange removes a member");
        }

        UUID source = UUID.randomUUID(), victim = UUID.randomUUID(), match = UUID.randomUUID();
        var chain = new DamageChain(2, 1);
        var trace = new DamageChain.Root();
        chain.root(trace, () -> check(chain.hit(match, source, victim, "first", 4, false, () -> {
            check(chain.current().original().equals(trace.id()) && !chain.current().mayLifesteal(),
                    "linked trace has original ID without lifesteal");
            return true;
        }), "first hit"));
        chain.root(trace, () -> check(!chain.hit(match, source, victim, "second", 4, false, () -> true), "deferred hit keeps target cap"));

        var logs = new ArrayList<String>();
        var audit = new CombatAudit(1, logs::add);
        audit.sample(2, "disabled");
        audit.start(match);
        check(audit.actor(source).equals("actor-1") && audit.actor(source).equals("actor-1")
                && audit.actor(victim).equals("actor-2"), "match-local aliases");
        audit.sample(5, audit.actor(source));
        audit.sample(6, "discard");
        audit.add("test", CombatAudit.Metric.STACKS, 3);
        audit.add("test", CombatAudit.Metric.STACKS, 2);
        audit.finish();
        check(logs.size() == 2 && logs.getLast().contains("STACKS=5.0"), "bounded diagnostic and exact summary");
        check(logs.stream().noneMatch(line -> line.contains(source.toString()) || line.contains(victim.toString())), "no player IDs");
        audit.start(UUID.randomUUID());
        check(audit.actor(victim).equals("actor-1"), "match reset clears attribution");
        check(new AugmentProgress().value("steel_heart") == 0, "new match starts with empty progression");
        System.out.println(count + " Phase 6.3 domain assertions passed");
    }
}
