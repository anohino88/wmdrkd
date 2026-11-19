import io.github.augmentwild.augment.AugmentDefinition;
import io.github.augmentwild.augment.AugmentRoller;
import io.github.augmentwild.augment.SynergyDraftWeights;
import java.nio.file.Path;
import java.util.*;

public final class Phase66DraftStudy {
    private static final Set<String> XP = Set.of("xp_silver", "xp_gold", "xp_prism");
    private static final Set<String> MOBILITY = Set.of("speed", "shadow_dash", "reflex", "berserk_engine", "tank_engine",
            "storm_chaser", "vein_overload", "calamity_giant", "time_thief");
    private static final Set<String> SURVIVAL = Set.of("armor", "defense", "fall", "heal", "blood_record", "overgrowth",
            "steel_heart", "immortal_berserker", "russian_doll", "iron_stomach", "wound_record", "survival_instinct",
            "predator", "counter_shield", "heart_harvester", "blood_crown", "immortal_armor", "phoenix_heart", "time_rewind");
    private static final Set<String> ECONOMY = Set.of("xp_silver", "xp_gold", "xp_prism", "miner", "stack_dinosaur", "mineral_addiction",
            "mineral_devourer", "forbidden_deal", "forbidden_reconstruction", "eureka");
    private enum Role { OFFENSE, SURVIVAL, MOBILITY, ECONOMY }

    public static void main(String[] args) throws Exception {
        var registry = ResonanceChecks.loadCatalog(Path.of(args[0]));
        var roller = new AugmentRoller(registry, new Random(66026), new SynergyDraftWeights(ResonanceChecks.SYNERGIES, 1.5, 2.5));
        var rng = new Random(66027);
        var candidate = new Random(66028);
        int runs = 2_000;
        int[] mono = new int[6], diversified = new int[6], xpOffered = new int[6];
        for (int run = 0; run < runs; run++) {
            Set<String> owned = new LinkedHashSet<>();
            for (int round = 0; round < 6; round++) {
                boolean last = round == 5;
                int level = Math.min(50, (run % 4 + 1) * (round + 1) * 3);
                var options = roller.roll(level, last, owned, Set.of(),
                        a -> !owned.contains(a.id()) && !a.id().startsWith("forbidden_"));
                var alternative = new ArrayList<>(options);
                if (options.stream().anyMatch(a -> XP.contains(a.id()))) xpOffered[round]++;
                if (last) {
                    for (int i = 0; i < alternative.size(); i++) {
                        if (XP.contains(alternative.get(i).id())) replace(i, alternative, registry.selectable(), owned, candidate, false, true);
                    }
                }
                if (monochrome(options)) mono[round]++;
                if (monochrome(alternative)) {
                    replace(2, alternative, registry.selectable(), owned, candidate, true, last);
                }
                if (monochrome(alternative)) diversified[round]++;
                if (last && alternative.stream().anyMatch(a -> XP.contains(a.id()))) throw new AssertionError("Late XP replacement failed");
                for (int i = 0; i < options.size(); i++) {
                    if (options.get(i).rarity() != alternative.get(i).rarity()) throw new AssertionError("Rarity mix changed");
                }
                owned.add(options.get(rng.nextInt(options.size())).id());
            }
        }
        for (int round = 0; round < 6; round++) {
            System.out.printf("SYNTHETIC_DRAFT round=%d n=%d one_role=%d candidate_one_role=%d xp_offered=%d candidate_final_xp=%s%n",
                    round + 1, runs, mono[round], diversified[round], xpOffered[round], round == 5 ? "0" : "NA");
        }
    }

    private static boolean monochrome(List<AugmentDefinition> options) {
        return options.stream().map(option -> role(option.id())).distinct().count() == 1;
    }

    private static void replace(int index, List<AugmentDefinition> options, List<AugmentDefinition> pool, Set<String> owned,
                                Random random, boolean differentRole, boolean excludeXp) {
        var old = options.get(index);
        var peers = pool.stream().filter(a -> a.rarity() == old.rarity() && !owned.contains(a.id()) && !options.contains(a)
                && !a.id().startsWith("forbidden_") && (!excludeXp || !XP.contains(a.id()))
                && (!differentRole || role(a.id()) != role(old.id()))).toList();
        if (!peers.isEmpty()) options.set(index, peers.get(random.nextInt(peers.size())));
    }

    private static Role role(String id) {
        if (MOBILITY.contains(id)) return Role.MOBILITY;
        if (SURVIVAL.contains(id)) return Role.SURVIVAL;
        if (ECONOMY.contains(id)) return Role.ECONOMY;
        return Role.OFFENSE;
    }
}
