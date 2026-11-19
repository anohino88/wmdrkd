package io.github.augmentwild.augment;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** The five unique synergies and the administrator's revival-family preset. */
public final class AugmentCombinations {
    public static final Map<String, Set<String>> UNIQUE = Map.of(
            "steel_king", Set.of("steel_heart", "stack_dinosaur", "heavy_fist"),
            "calamity_train", Set.of("tank_engine", "calamity_giant", "berserk_engine"),
            "infinite_barrage", Set.of("magic_missile", "eureka", "infinite_repeat"),
            "hell_lord", Set.of("infernal_conductor", "center_of_universe", "jeweled_gauntlet"),
            "thousand_swords", Set.of("sword_wind", "ultimate_hydra", "dual_wielder"));
    public static final Map<String, Set<String>> PRESETS;

    static {
        var presets = new TreeMap<>(UNIQUE);
        presets.put("life_echo", AugmentResonance.DEATH_PREVENTION);
        PRESETS = Collections.unmodifiableMap(presets);
    }

    private AugmentCombinations() { }
}
