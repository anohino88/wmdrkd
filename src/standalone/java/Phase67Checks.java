import io.github.augmentwild.augment.*;
import io.github.augmentwild.domain.CombatLab;
import java.nio.file.Path;
import java.util.*;

public final class Phase67Checks {
    private static final Set<String> EXCHANGES = Set.of("forbidden_deal", "forbidden_reconstruction");
    private static final int[] LEVELS = {0, 10, 25, 40, 50};
    private static final int MATCHES = 20_000;

    public static void main(String[] args) throws Exception {
        var registry = ResonanceChecks.loadCatalog(Path.of(args[0]));
        require(registry.all().stream().noneMatch(a -> AugmentRole.of(a.id()) == AugmentRole.UNKNOWN), "all 76 roles classified");
        require(Arrays.stream(AugmentRole.values()).filter(role -> role != AugmentRole.UNKNOWN)
                .allMatch(role -> registry.all().stream().anyMatch(a -> AugmentRole.of(a.id()) == role)), "all six roles used");
        require(AugmentRole.of("future_id") == AugmentRole.UNKNOWN, "unknown augment has neutral fallback");
        verifyLab();
        verifyFallback(registry);
        verifyOdds();
        var weights = new SynergyDraftWeights(ResonanceChecks.SYNERGIES, 1.5, 2.5);
        var legacy = new Study("legacy");
        var improved = new Study("improved");
        for (int match = 0; match < MATCHES; match++) {
            var before = simulate(match, registry, weights, AugmentRoller.Quality.LEGACY, legacy);
            var after = simulate(match, registry, weights, AugmentRoller.Quality.DEFAULT, improved);
            require(before.equals(after), "non-final rolls and rerolls identical at equal seed " + match);
        }
        require(legacy.sameRole[5] > improved.sameRole[5] && legacy.finalXp > 0 && improved.finalXp == 0, "final candidate quality");
        require(Arrays.deepEquals(legacy.tiers, improved.tiers), "paired runs retain every displayed rarity count");
        legacy.report();
        improved.report();
        for (var a : registry.all()) {
            String id = a.id();
            System.out.printf(Locale.ROOT, "PHASE67_AUGMENT id=%s role=%s offer_before=%.4f%% offer_after=%.4f%% pick_before=%.4f%% pick_after=%.4f%%%n",
                    id, AugmentRole.of(id), rate(legacy.offers, id), rate(improved.offers, id),
                    rate(legacy.picks, id), rate(improved.picks, id));
        }
    }

    private static List<List<String>> simulate(int match, AugmentRegistry registry, SynergyDraftWeights weights,
                                                AugmentRoller.Quality quality, Study result) {
        var seed = 670_026L + match;
        var roller = new AugmentRoller(registry, new Random(seed), weights, quality);
        var rng = new Random(~seed);
        var owned = new LinkedHashSet<String>();
        var target = ResonanceChecks.SYNERGIES.get(match / 5 % 5);
        target.stream().sorted().limit(match / 25 % 3).forEach(owned::add);
        boolean reroll = match / 75 % 2 == 1;
        int rerollAt = match / 150 % 6;
        int level = LEVELS[match % LEVELS.length];
        var early = new ArrayList<List<String>>();
        for (int round = 0; round < 6; round++) {
            boolean last = round == 5;
            var eligible = (java.util.function.Predicate<AugmentDefinition>) a -> canOffer(a.id(), owned, registry);
            var choices = roller.roll(level, last, owned, Set.of(), eligible);
            if (!last) early.add(choices.stream().map(AugmentDefinition::id).toList());
            if (reroll && round == rerollAt) {
                var previous = new HashSet<>(choices.stream().map(AugmentDefinition::id).toList());
                choices = roller.roll(level, last, owned, previous, eligible);
                require(!previous.equals(new HashSet<>(choices.stream().map(AugmentDefinition::id).toList())), "reroll changes options");
            }
            require(choices.size() == 3 && choices.stream().map(AugmentDefinition::id).distinct().count() == 3, "distinct choices");
            require(choices.stream().allMatch(eligible), "exchange eligibility remains in force");
            if (!last) early.add(choices.stream().map(AugmentDefinition::id).toList());
            else require(choices.stream().allMatch(a -> a.rarity() == AugmentRarity.PRISMATIC), "final prisms");
            if (choices.stream().map(a -> AugmentRole.of(a.id())).distinct().count() == 1) result.sameRole[round]++;
            if (last && choices.stream().anyMatch(a -> a.id().equals("xp_prism"))) result.finalXp++;
            for (var choice : choices) {
                result.tiers[round][choice.rarity().ordinal()]++;
                result.offers.merge(choice.id(), 1, Integer::sum);
            }
            var preferred = choices.stream().filter(a -> target.contains(a.id()) && !owned.contains(a.id())).findFirst();
            var chosen = (preferred.isPresent() ? preferred.orElseThrow() : choices.get(rng.nextInt(choices.size()))).id();
            result.picks.merge(chosen, 1, Integer::sum);
            if (EXCHANGES.contains(chosen)) {
                var rewards = new ArrayList<>(rewards(chosen, owned, registry));
                Collections.shuffle(rewards, rng);
                var sacrifices = chosen.equals("forbidden_reconstruction")
                        ? owned.stream().filter(id -> registry.get(id).rarity() == AugmentRarity.SILVER).limit(2).toList()
                        : List.<String>of();
                var exchange = new AugmentExchange(sacrifices, rewards.subList(0, Math.min(4, rewards.size())), 2);
                var ordered = new ArrayList<>(exchange.candidates());
                ordered.sort(Comparator.comparing(id -> !target.contains(id)));
                require(exchange.select(ordered.get(0)) && exchange.select(ordered.get(1)), "exchange accepted two distinct rewards");
                var commit = exchange.finalizeAgainst(owned).orElseThrow();
                owned.removeAll(commit.removed());
                owned.addAll(commit.awarded());
            }
            owned.add(chosen);
        }
        int i = 0;
        for (var group : ResonanceChecks.SYNERGIES) {
            if (owned.containsAll(group)) result.completed[i]++;
            i++;
        }
        return early;
    }

    private static List<String> rewards(String exchange, Set<String> owned, AugmentRegistry registry) {
        var rarity = exchange.equals("forbidden_deal") ? AugmentRarity.GOLD : AugmentRarity.PRISMATIC;
        return registry.selectable().stream().filter(a -> a.rarity() == rarity && !owned.contains(a.id())
                && !EXCHANGES.contains(a.id())).map(AugmentDefinition::id).toList();
    }

    private static boolean canOffer(String id, Set<String> owned, AugmentRegistry registry) {
        if (!EXCHANGES.contains(id)) return true;
        return !owned.contains(id) && (!id.equals("forbidden_reconstruction") || owned.stream()
                .filter(item -> registry.get(item).rarity() == AugmentRarity.SILVER).count() >= 2)
                && rewards(id, owned, registry).size() >= 2;
    }

    private static void verifyFallback(AugmentRegistry full) {
        var small = new AugmentRegistry();
        for (String id : List.of("xp_prism", "defense", "phoenix_heart")) small.register(full.get(id));
        var tiny = new AugmentRoller(small, new Random(30), (id, owned) -> 1, AugmentRoller.Quality.DEFAULT);
        require(tiny.roll(50, true, Set.of(), Set.of()).stream().anyMatch(a -> a.id().equals("xp_prism")), "XP fallback yields three prisms");
        require(tiny.roll(50, true, Set.of(), Set.of(), a -> !a.id().equals("defense")).size() == 2,
                "insufficient prisms never add invalid or silver choices");
        Set<String> owned = Set.of("xp_prism", "defense", "phoenix_heart");
        require(tiny.roll(50, true, owned, owned).stream().map(AugmentDefinition::id).distinct().count() == 3, "owned fallback unique");
        small.register(full.get("berserk"));
        var first = tiny.roll(50, true, Set.of(), Set.of()).stream().map(AugmentDefinition::id).toList();
        require(first.size() == 3 && !first.contains("xp_prism"), "three non-XP prisms suffice");
        require(tiny.roll(50, true, Set.of(), Set.copyOf(first)).stream().anyMatch(a -> a.id().equals("xp_prism")),
                "XP fallback preserves a reroll on a minimal pool");
        var optional = new AugmentRoller(full, new Random(30), (id, set) -> 1, new AugmentRoller.Quality(true, true, false));
        for (int i = 0; i < 1_000; i++) {
            require(optional.roll(25, false, Set.of(), Set.of(), a -> !EXCHANGES.contains(a.id())).stream()
                    .allMatch(a -> !EXCHANGES.contains(a.id())), "role diversity cannot bypass eligibility");
        }
        var silver = full.selectable().stream().filter(a -> a.rarity() == AugmentRarity.SILVER).limit(2).map(AugmentDefinition::id).toList();
        require(!canOffer("forbidden_reconstruction", Set.of(), full)
                && canOffer("forbidden_reconstruction", Set.copyOf(silver), full), "exchange requires sacrifices");
    }

    private static void verifyOdds() {
        for (int level : LEVELS) {
            int[] slots = new int[3];
            for (int i = 0; i < 10_000; i++) slots[AugmentRarity.roll(level, (i + .5) / 100).ordinal()]++;
            require(Arrays.equals(slots, new int[]{8000 - 146 * level, 1800 + 50 * level, 200 + 96 * level}), "unchanged XP odds");
        }
    }

    private static void verifyLab() {
        var lab = new CombatLab(true, "fixture", "real");
        require(!lab.allows(false, true, false) && !lab.allows(true, false, false) && !lab.allows(true, true, true)
                && lab.allows(true, true, false), "operator, active experiment and nonpaused gates");
        require(!new CombatLab(false, "fixture", "real").allows(true, true, false)
                && !new CombatLab(true, "REAL", "real").allows(true, true, false)
                && !new CombatLab(true, "", "real").allows(true, true, false), "disabled and production world gates");
        var progress = new AugmentProgress();
        progress.addPermanent("steel_heart", 1, 12_500, 200);
        require(progress.replace("steel_heart", 20, 200) == 20, "assigned stacks absolute");
        require(progress.addPermanent("steel_heart", 3, 12_500, 200) == 23, "fractional carry reset; gain uses resonance");
        require(progress.replace("steel_heart", 10_000, 200) == 200, "stack setting honors cap");
        require(progress.cooldown("steel_heart", 4, 8) && progress.cooldownRemaining("steel_heart", 5) == 7, "cooldown report");
        progress.reset("steel_heart");
        progress.resetCooldown("steel_heart");
        require(progress.value("steel_heart") == 0 && progress.ready("steel_heart", 5), "removed ledger and cooldown start fresh");
        var balance = new AugmentBalance(20, 50, .8, .2, 8);
        require(balance.steelHealth(20, 2) == 40 && balance.heavyFist(120, .08, true) == 8, "steel and heavy fist formulas");
    }

    private static double rate(Map<String, Integer> counts, String id) {
        return 100.0 * counts.getOrDefault(id, 0) / (MATCHES * 6);
    }

    private static void require(boolean expected, String label) {
        if (!expected) throw new AssertionError(label);
    }

    private static final class Study {
        final String name;
        final int[] sameRole = new int[6], completed = new int[5];
        final int[][] tiers = new int[6][3];
        final Map<String, Integer> offers = new TreeMap<>(), picks = new TreeMap<>();
        int finalXp;

        Study(String name) { this.name = name; }

        void report() {
            System.out.printf("PHASE67_DRAFT %s matches=%d drafts=%d mono=%s final_xp=%d synergies=%s tiers=%s%n",
                    name, MATCHES, MATCHES * 6, Arrays.toString(sameRole), finalXp, Arrays.toString(completed),
                    Arrays.deepToString(tiers));
        }
    }
}
