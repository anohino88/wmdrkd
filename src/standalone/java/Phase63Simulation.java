import io.github.augmentwild.augment.AugmentBalance;
import io.github.augmentwild.augment.AugmentDefinition;
import io.github.augmentwild.augment.AugmentProgress;
import io.github.augmentwild.augment.AugmentRarity;
import io.github.augmentwild.augment.AugmentResonance;
import io.github.augmentwild.augment.AugmentRoller;
import io.github.augmentwild.augment.SynergyDraftWeights;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

public final class Phase63Simulation {
    private static final Set<String> EXCHANGES = Set.of("forbidden_deal", "forbidden_reconstruction");
    private static final Set<String> SOLO = Set.of("magic_missile", "phoenix_heart", "final_judgment",
            "earthquake_mage", "time_rewind", "immortal_berserker", "world_erosion", "ultimate_hydra");
    private enum Model { RANDOM, RARITY, SOLO, SYNERGY, RESONANCE, MIXED }

    public static void main(String[] args) throws Exception {
        var registry = ResonanceChecks.loadCatalog(Path.of(args[0]));
        int count = args.length > 1 ? Integer.parseInt(args[1]) : 100_000;
        var groups = ResonanceChecks.SYNERGIES;
        var weights = new SynergyDraftWeights(groups, 1.5, 2.5);
        System.out.println("model,reroll,matches,steel_king,infinite_barrage,calamity_train,hell_lord,thousand_swords,rerolls,exchanges");
        for (var model : Model.values()) {
            for (boolean reroll : new boolean[]{false, true}) {
                var rng = new Random(63_026L);
                var roller = new AugmentRoller(registry, rng, weights);
                int[] totals = new int[groups.size()];
                int rerolls = 0, exchanges = 0;
                for (int game = 0; game < count; game++) {
                    Set<String> target = groups.get(game % groups.size());
                    var owned = new LinkedHashSet<String>();
                    boolean used = false;
                    int gear = game % 3;
                    for (int round = 1; round <= 6; round++) {
                        final int step = round;
                        java.util.function.Predicate<AugmentDefinition> eligible = option -> !owned.contains(option.id())
                                && (!EXCHANGES.contains(option.id())
                                    || (option.id().equals("forbidden_deal") || owned.stream()
                                        .filter(id -> registry.get(id).rarity() == AugmentRarity.SILVER).count() >= 2)
                                       && registry.selectable().stream().filter(candidate -> !EXCHANGES.contains(candidate.id())
                                           && !owned.contains(candidate.id()) && candidate.rarity() == rewardTier(option.id())).count() >= 2);
                        var options = roller.roll(round * 8, round == 6, owned, Set.of(), eligible);
                        if (options.size() != 3 || round == 6 && options.stream().anyMatch(a -> a.rarity() != AugmentRarity.PRISMATIC)) {
                            throw new AssertionError("draft invariant");
                        }
                        if (reroll && !used && (round == 6 || round >= 2 && options.stream().noneMatch(a ->
                                desirable(model, a, owned, target, gear, step)))) {
                            var previous = new HashSet<String>();
                            options.forEach(a -> previous.add(a.id()));
                            options = roller.roll(round * 8, round == 6, owned, previous, eligible);
                            used = true;
                            rerolls++;
                        }
                        var chosen = choose(options, model, owned, target, gear, round, rng);
                        if (EXCHANGES.contains(chosen.id())) {
                            applyExchange(chosen.id(), registry, model, owned, target, gear, round, rng);
                            exchanges++;
                        }
                        owned.add(chosen.id());
                    }
                    for (int i = 0; i < groups.size(); i++) if (owned.containsAll(groups.get(i))) totals[i]++;
                }
                System.out.printf(Locale.ROOT, "%s,%s,%d,%d,%d,%d,%d,%d,%d,%d%n", model, reroll, count,
                        totals[0], totals[1], totals[2], totals[3], totals[4], rerolls, exchanges);
            }
        }
        growth();
    }

    private static AugmentRarity rewardTier(String id) {
        return id.equals("forbidden_deal") ? AugmentRarity.GOLD : AugmentRarity.PRISMATIC;
    }

    private static void applyExchange(String id, io.github.augmentwild.augment.AugmentRegistry registry, Model model,
                                      Set<String> owned, Set<String> target, int gear, int round, Random rng) {
        var candidates = new ArrayList<>(registry.selectable().stream().filter(option -> option.rarity() == rewardTier(id)
                && !owned.contains(option.id()) && !EXCHANGES.contains(option.id())).toList());
        Collections.shuffle(candidates, rng);
        var displayed = new ArrayList<>(candidates.subList(0, Math.min(4, candidates.size())));
        if (id.equals("forbidden_reconstruction")) {
            var removed = owned.stream().filter(option -> registry.get(option).rarity() == AugmentRarity.SILVER).limit(2).toList();
            owned.removeAll(removed);
        }
        for (int i = 0; i < 2; i++) {
            var option = choose(displayed, model, owned, target, gear, round, rng);
            displayed.remove(option);
            owned.add(option.id());
        }
    }

    private static boolean desirable(Model model, AugmentDefinition option, Set<String> owned, Set<String> target,
                                      int gear, int round) {
        return switch (model) {
            case RANDOM -> round == 6;
            case RARITY -> option.rarity() == AugmentRarity.PRISMATIC;
            case SOLO -> SOLO.contains(option.id());
            case SYNERGY -> target.contains(option.id());
            case RESONANCE -> AugmentResonance.PERMANENT.contains(option.id()) || AugmentResonance.DEATH_PREVENTION.contains(option.id());
            case MIXED -> score(model, option, owned, target, gear, round) >= 8;
        };
    }

    private static AugmentDefinition choose(List<AugmentDefinition> options, Model model, Set<String> owned, Set<String> target,
                                             int gear, int round, Random rng) {
        if (model == Model.RANDOM) return options.get(rng.nextInt(options.size()));
        return options.stream().max(java.util.Comparator.comparingInt(option -> score(model, option, owned, target, gear, round)))
                .orElseThrow();
    }

    private static int score(Model model, AugmentDefinition option, Set<String> owned, Set<String> target, int gear, int round) {
        String id = option.id();
        int rarity = option.rarity().ordinal();
        boolean permanent = AugmentResonance.PERMANENT.contains(id), revival = AugmentResonance.DEATH_PREVENTION.contains(id);
        return switch (model) {
            case RARITY, RANDOM -> rarity;
            case SOLO -> (SOLO.contains(id) ? 10 : 0) + rarity;
            case SYNERGY -> (target.contains(id) ? 10 : 0) + rarity;
            case RESONANCE -> (permanent ? 8 : revival ? 9 : 0) + rarity
                    + (permanent && owned.stream().anyMatch(AugmentResonance.PERMANENT::contains) ? 3 : 0)
                    + (revival && owned.stream().anyMatch(AugmentResonance.DEATH_PREVENTION::contains) ? 3 : 0);
            case MIXED -> rarity + (target.contains(id) ? 4 : 0) + (permanent && round <= 3 ? 6 : 0)
                    + (revival && round >= 4 ? 8 : 0) + (SOLO.contains(id) ? 3 : 0)
                    + (gear == 0 && Set.of("heavy_fist", "dual_wielder", "sword_wind").contains(id) ? 5 : 0)
                    + (gear == 1 && Set.of("magic_missile", "split_bow", "explosive_arrow").contains(id) ? 5 : 0)
                    + (gear == 2 && Set.of("steel_heart", "blood_record", "tank_engine").contains(id) ? 5 : 0);
        };
    }

    private static void growth() {
        var curves = List.of(new AugmentBalance(20, 50, .8, .2, 8), new AugmentBalance(20, 50, 1.2, .4, 8),
                new AugmentBalance(20, 50, 1.5, .6, 8));
        String[] builds = {"steel", "steel+dinosaur", "steel+blood", "steel+overgrowth", "steel+fist",
                "king", "king+blood"};
        int[] rates = {10000, 20000, 12500, 10000, 10000, 20000, 22500};
        System.out.println("build,minute,stacks,evolved_minute,max_hp_a,max_hp_b,max_hp_c,ttk_a,ttk_b,ttk_c,heavy_fist_a");
        for (int i = 0; i < builds.length; i++) {
            var progress = new AugmentProgress();
            double evolutionMinute = 0;
            for (int minute = 10; minute <= 30; minute += 5) {
                for (int step = 1; step <= 6; step++) {
                    int before = progress.value("steel");
                    int stacks = progress.addPermanent("steel", before >= 20 ? 2 : 1, rates[i], 200);
                    if (before < 20 && stacks >= 20) evolutionMinute = minute - 5 + step * 5.0 / 6;
                }
                int stacks = progress.value("steel");
                double companion = i == 3 ? 16 + ((minute - 5) / 5) * 8 : (i == 2 || i == 6) ? (minute - 5) / 5 * 2 : 0;
                var hp = curves.stream().map(curve -> 20 + companion + curve.steelHealth(stacks, 2)).toList();
                System.out.printf(Locale.ROOT, "%s,%d,%d,%.2f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.2f%n", builds[i], minute, stacks,
                        evolutionMinute, hp.get(0), hp.get(1), hp.get(2), Math.ceil(hp.get(0) / 4),
                        Math.ceil(hp.get(1) / 4), Math.ceil(hp.get(2) / 4),
                        i >= 4 ? curves.getFirst().heavyFist(hp.getFirst(), .08, true) : 0);
            }
        }
    }
}
