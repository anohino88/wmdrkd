import io.github.augmentwild.augment.AugmentBalance;
import io.github.augmentwild.augment.AugmentDefinition;
import io.github.augmentwild.augment.AugmentProgress;
import io.github.augmentwild.augment.AugmentRarity;
import io.github.augmentwild.augment.AugmentRegistry;
import io.github.augmentwild.augment.AugmentResonance;
import io.github.augmentwild.augment.AugmentRoller;
import io.github.augmentwild.augment.LinkedDamageBudget;
import io.github.augmentwild.augment.ResonanceShield;
import io.github.augmentwild.augment.ReviveResolver;
import io.github.augmentwild.augment.SynergyDraftWeights;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class ResonanceChecks {
    private static final long SECOND = 1_000_000_000L;
    private static final List<String> GROWTH = List.of("steel_heart", "blood_record", "mineral_addiction", "tank_engine", "wound_record");
    private static final List<Set<String>> SYNERGIES = List.of(
            Set.of("steel_heart", "stack_dinosaur", "heavy_fist"),
            Set.of("magic_missile", "eureka", "infinite_repeat"),
            Set.of("tank_engine", "calamity_giant", "berserk_engine"),
            Set.of("infernal_conductor", "center_of_universe", "jeweled_gauntlet"),
            Set.of("sword_wind", "ultimate_hydra", "dual_wielder"));
    private static int checks;

    private static void require(boolean result, String label) {
        if (!result) throw new AssertionError(label);
        checks++;
    }

    public static void main(String[] args) throws Exception {
        var config = new AugmentResonance(true, new AugmentResonance.Stacking(true, "누적의 공명", AugmentResonance.PERMANENT,
                List.of(10000, 12500, 15000, 17500), 27500),
                new AugmentResonance.Revival(true, "생명의 잔향", AugmentResonance.DEATH_PREVENTION, 6, 4, 8, 8));
        var owned = new HashSet<String>();
        int[] tiers = {10000, 10000, 12500, 15000, 17500, 17500};
        for (int i = 0; i < tiers.length; i++) {
            owned.addAll(GROWTH.subList(0, i));
            require(config.state(owned, 20000).familyMultiplier() == tiers[i], "family ownership tier " + i);
            owned.add("stack_dinosaur");
            require(config.state(owned, 20000).totalMultiplier() == tiers[i] + 10000, "additive dinosaur tier " + i);
            owned.remove("stack_dinosaur");
        }
        owned.removeAll(GROWTH.subList(2, 5));
        require(config.state(owned, 20000).totalMultiplier() == 12500, "removal recalculates without touching earned stacks");
        var king = new HashSet<>(SYNERGIES.getFirst());
        require(config.state(king, 20000).familyMultiplier() == 10000, "king ownership does not inflate growth count");
        king.add("blood_record");
        require(king.containsAll(SYNERGIES.getFirst()) && config.state(king, 20000).totalMultiplier() == 22500,
                "unique synergy and resonance coexist");

        var progress = new AugmentProgress();
        String steel = "steel_heart";
        for (int expected : new int[]{1, 2, 3, 5}) require(progress.addPermanent(steel, 1, 12500, 100) == expected, "125% carry");
        for (int expected : new int[]{1, 3}) require(progress.addPermanent("mineral_addiction", 1, 15000, 100) == expected, "150% carry");
        for (int expected : new int[]{1, 3, 5, 7}) require(progress.addPermanent("blood_record", 1, 17500, 100) == expected, "175% carry");
        require(progress.addPermanent(steel, 2, 27500, 8) == 8, "evolution raw gain before bonus and hard cap");
        require(progress.addPermanent(steel, 2, 27500, 8) == 8, "at cap");
        progress.reset(steel);
        require(progress.addPermanent(steel, 1, 12500, 100) == 1, "removed augment remainder cleared");
        var replayProgress = progress;
        require(replayProgress.addPermanent(steel, 1, 12500, 100) == 2, "same match state on reconnect");
        require(new AugmentProgress().value(steel) == 0, "new match ledger");

        var shield = new ResonanceShield();
        var reviveOwned = new LinkedHashSet<String>();
        require(shield.remaining(0) == 0, "normal death or respawn does not arm the shield");
        for (String id : List.of("immortal_berserker", "phoenix_heart", "russian_doll")) {
            reviveOwned.add(id);
            require(config.state(reviveOwned, 20000).shieldPoints() == (reviveOwned.size() - 1) * 4, "shield capacity");
        }
        var revives = List.of(new ReviveResolver.Option("immortal_berserker", 1), new ReviveResolver.Option("phoenix_heart", 1));
        String success = ReviveResolver.resolve(progress, revives, id -> true);
        require(success.equals("immortal_berserker") && progress.value("revive:phoenix_heart") == 0, "one revive used");
        require(config.state(reviveOwned, 20000).shieldPoints() == 8, "used revive still counts as owned");
        shield.grant(2 * SECOND, 8, 6 * SECOND);
        shield.consume(3 * SECOND, shield.preview(3 * SECOND, 3));
        require(shield.remaining(3 * SECOND) == 5, "post-native shield spends actual points");
        shield.grant(4 * SECOND, 8, 6 * SECOND);
        require(shield.remaining(4 * SECOND) == 8, "refresh replaces capacity");
        require(shield.remaining(10 * SECOND - 1) == 8 && shield.remaining(10 * SECOND) == 0, "exact game deadline");
        shield.grant(11 * SECOND, 4, 6 * SECOND);
        shield.clear();
        require(shield.remaining(11 * SECOND) == 0, "cleanup clears only virtual shield");

        var balance = new AugmentBalance(20, 50, .8, .2, 8);
        require(balance.steelHealth(20, 2) == 40 && balance.steelHealth(50, 2) == 64
                && balance.steelHealth(80, 2) == 70, "soft cap continuity");
        require(balance.heavyFist(200, .08, true) == 8 && balance.heavyFist(200, .08, false) == 16, "PvP-only flat cap");
        var split = AugmentBalance.attackParts(10, 3, 8, 400, .75);
        require(split.original() == 17.75 && split.repeat() == 9.75, "heavy fist bonus paid once across split hits");
        UUID primary = UUID.randomUUID(), secondary = UUID.randomUUID();
        var measured = new LinkedDamageBudget(12, false, primary, 2);
        require(measured.offer(primary, 14).allowed() == 14 && measured.offer(primary, 1).overBudget(), "measurement does not cap");
        var enforced = new LinkedDamageBudget(12, true, primary, 2);
        require(enforced.offer(primary, 8).allowed() == 8 && enforced.offer(primary, 8).allowed() == 2, "shared target budget");
        enforced.refund(primary, 2);
        require(enforced.offer(primary, 5).allowed() == 2 && enforced.offer(secondary, 12).allowed() == 12, "failed damage refunded, targets separate");

        simulate(Path.of(args[0]));
        System.out.println(checks + " resonance and balance checks passed");
    }

    private static void simulate(Path root) throws Exception {
        var registry = loadCatalog(root);
        var weights = new SynergyDraftWeights(SYNERGIES, 1.5, 2.5);
        require(weights.applyAsDouble("steel_heart", Set.of("heavy_fist")) == 1.5, "one member weight");
        require(weights.applyAsDouble("steel_heart", Set.of("heavy_fist", "stack_dinosaur")) == 2.5, "two member weight");
        require(weights.applyAsDouble("speed", Set.of("heavy_fist", "stack_dinosaur")) == 1, "unrelated weight");
        require(weights.applyAsDouble("steel_heart", SYNERGIES.getFirst()) == 1, "owned candidate not weighted");
        var fixedOdds = new Random(100) {
            private int nextTier;
            @Override public double nextDouble(double bound) {
                if (bound == 100) return new double[]{1, 81, 99}[nextTier++];
                return 0;
            }
        };
        var verified = new AugmentRoller(registry, fixedOdds, weights);
        require(verified.roll(0, false, Set.of("heavy_fist"), Set.of()).stream().map(AugmentDefinition::rarity).toList()
                .equals(List.of(AugmentRarity.SILVER, AugmentRarity.GOLD, AugmentRarity.PRISMATIC)),
                "synergy weights are confined to the drawn rarity");
        require(verified.roll(50, true, SYNERGIES.getFirst(), Set.of()).stream()
                .allMatch(offer -> offer.rarity() == AugmentRarity.PRISMATIC), "last round stays prismatic");

        var neutral = new AugmentRoller(registry, new Random(100));
        var weighted = new AugmentRoller(registry, new Random(100), weights);
        int draws = 1800;
        long started = System.nanoTime();
        for (int index = 0; index < SYNERGIES.size(); index++) {
            int before = completion(neutral, SYNERGIES.get(index), draws);
            int after = completion(weighted, SYNERGIES.get(index), draws);
            require(after > before && after < draws, "weights improve chance without guarantee for " + index);
            System.out.printf(Locale.ROOT, "SIMULATED synergy %d: %d/%d (%.2f%%) -> %d/%d (%.2f%%)%n", index + 1,
                    before, draws, before * 100.0 / draws, after, draws, after * 100.0 / draws);
        }
        System.out.printf(Locale.ROOT, "SIMULATED drafts elapsed %.2f seconds%n", (System.nanoTime() - started) / 1e9);
        int[] fixedRates = {10000, 12500, 15000, 17500, 20000, 22500, 25000, 27500};
        for (int rate : fixedRates) {
            var ledger = new AugmentProgress();
            var gains = new ArrayList<Integer>();
            for (int interval = 0; interval < 5; interval++) {
                int before = ledger.value("steel_heart");
                for (int proc = 0; proc < 6; proc++) {
                    ledger.addPermanent("steel_heart", ledger.value("steel_heart") >= 20 ? 2 : 1, rate, 200);
                }
                gains.add(ledger.value("steel_heart") - before);
            }
            System.out.printf("SIMULATED steel %d%%: five-minute gains %s; 5m vs 25m acquisition %d vs %d stacks%n",
                    rate / 100, gains, ledger.value("steel_heart"), gains.getFirst());
        }
    }

    private static int completion(AugmentRoller roller, Set<String> synergy, int draws) {
        int complete = 0;
        for (int index = 0; index < draws; index++) {
            var owned = new HashSet<String>();
            for (int round = 1; round <= 6; round++) {
                var options = roller.roll(round * 8, round == 6, owned, Set.of());
                var chosen = options.stream().filter(option -> synergy.contains(option.id()) && !owned.contains(option.id()))
                        .findFirst().orElse(options.getFirst());
                owned.add(chosen.id());
            }
            if (owned.containsAll(synergy)) complete++;
        }
        return complete;
    }

    private static AugmentRegistry loadCatalog(Path root) throws Exception {
        var registry = new AugmentRegistry();
        String official = Files.readString(root.resolve("src/main/resources/official-augments.yml"));
        var entries = Pattern.compile("(?m)^  ([a-z_]+):\\n    group: [^\\n]+\\n    rarity: ([A-Z]+)").matcher(official);
        while (entries.find()) register(registry, entries.group(1), entries.group(2));
        String extensions = Files.readString(root.resolve("src/main/java/io/github/augmentwild/paper/AugmentCatalog.java"));
        entries = Pattern.compile("add\\(registry, config, \"([a-z_]+)\", AugmentRarity\\.([A-Z]+)").matcher(extensions);
        while (entries.find()) register(registry, entries.group(1), entries.group(2));
        require(registry.all().size() == 76, "production catalog inputs");
        return registry;
    }

    private static void register(AugmentRegistry registry, String id, String rarity) {
        registry.register(new AugmentDefinition(id, AugmentRarity.valueOf(rarity), id, id, "points",
                AugmentDefinition.Effect.OFFICIAL, 1, 0, 0, true, true));
    }
}
