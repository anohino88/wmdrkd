package io.github.augmentwild.augment;

import java.util.HashMap;
import java.util.Map;

/** Primary draft role by registered augment ID. An unknown ID is neutral in draft weighting. */
public enum AugmentRole {
    ATTACK("공격형"), SURVIVAL("생존형"), GROWTH("성장형"), MOBILITY("기동형"),
    ECONOMY("경제형"), SPECIAL("특수형"), UNKNOWN("미분류");

    private final String label;
    private static final Map<String, AugmentRole> ROLES = roles();

    AugmentRole(String label) { this.label = label; }
    public String label() { return label; }
    public static AugmentRole of(String id) { return ROLES.getOrDefault(id, UNKNOWN); }

    private static Map<String, AugmentRole> roles() {
        Map<String, AugmentRole> roles = new HashMap<>();
        add(roles, ATTACK, """
                kinetic_reversal ricochet_arrow frost_bind delayed_blast triad_reactor heavy_fist sword_wind ultimate_hydra infernal_conductor magic_missile phantom_weapon giant_slayer
                split_attack storm_chaser jeweled_gauntlet dual_wielder earthquake_mage center_of_universe final_judgment
                chain_impact blood_trace frenzy_rhythm explosive_arrow storm_blade venom_swarm bounty_hunter split_bow
                bomb_alchemy duel_contract void_lord rear berserk
                """);
        add(roles, SURVIVAL, """
                ore_ward last_bastion immortal_berserker russian_doll survival_instinct predator falling_shockwave counter_shield
                blood_crown immortal_armor phoenix_heart last_demon heal fall armor defense
                """);
        add(roles, GROWTH, """
                stack_dinosaur blood_record mineral_addiction overgrowth steel_heart tank_engine draw_the_sword
                muscle_memory iron_stomach wound_record vein_overload heart_harvester mineral_devourer endless_evolution blood_god
                """);
        add(roles, MOBILITY, "berserk_engine reflex shadow_dash time_thief afterimage_stride speed pursuit");
        add(roles, ECONOMY, "xp_silver xp_gold xp_prism miner");
        add(roles, SPECIAL, """
                eureka calamity_giant infinite_repeat mad_scientist forbidden_reconstruction time_rewind clone_hand
                gigantification forbidden_deal world_erosion
                """);
        return Map.copyOf(roles);
    }

    private static void add(Map<String, AugmentRole> roles, AugmentRole role, String ids) {
        for (String id : ids.strip().split("\\s+")) {
            if (roles.putIfAbsent(id, role) != null) throw new IllegalStateException("Duplicate augment role: " + id);
        }
    }
}
