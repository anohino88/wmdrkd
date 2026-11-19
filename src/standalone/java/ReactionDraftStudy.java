import io.github.augmentwild.augment.*;
import java.nio.file.Path;
import java.util.*;

public final class ReactionDraftStudy {
    private static final Set<String> NEW = Set.of("kinetic_reversal", "ore_ward", "afterimage_stride",
            "ricochet_arrow", "frost_bind", "delayed_blast", "triad_reactor", "last_bastion");

    public static void main(String[] args) throws Exception {
        var all = ResonanceChecks.loadCatalog(Path.of(args[0]));
        var original = new AugmentRegistry();
        all.all().stream().filter(def -> !NEW.contains(def.id())).forEach(original::register);
        for (var catalog : List.of(original, all)) {
            var roller = new AugmentRoller(catalog, new Random(68084),
                    new SynergyDraftWeights(ResonanceChecks.SYNERGIES, 1.5, 2.5), AugmentRoller.Quality.DEFAULT);
            for (boolean last : List.of(false, true)) {
                int[] tiers = new int[3];
                int fresh = 0;
                for (int i = 0; i < 20000; i++) {
                    var choices = roller.roll(25, last, Set.of(), Set.of(), a -> !a.id().equals("forbidden_reconstruction"));
                    for (var def : choices) {
                        tiers[def.rarity().ordinal()]++;
                        if (NEW.contains(def.id())) fresh++;
                        if (last && def.rarity() != AugmentRarity.PRISMATIC) throw new AssertionError(def);
                    }
                }
                var counts = Arrays.stream(AugmentRarity.values()).map(r -> catalog.all().stream().filter(a -> a.rarity() == r).count()).toList();
                System.out.printf("REACTION_DRAFT catalog=%d counts=%s final=%b slots=%s new_slots=%d/60000%n",
                        catalog.all().size(), counts, last, Arrays.toString(tiers), fresh);
            }
        }
    }
}
