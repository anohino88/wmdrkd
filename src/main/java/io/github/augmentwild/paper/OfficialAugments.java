package io.github.augmentwild.paper;

import io.github.augmentwild.augment.AugmentDefinition;
import io.github.augmentwild.augment.AugmentRarity;
import io.github.augmentwild.augment.AugmentRegistry;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

final class OfficialAugments {
    private static final Set<String> BOUND_EFFECTS = Set.of(
            "stack_dinosaur", "heavy_fist", "blood_record", "mineral_addiction", "sword_wind",
            "berserk_engine", "overgrowth", "steel_heart", "eureka", "giant_slayer",
            "jeweled_gauntlet", "russian_doll", "muscle_memory", "predator", "mineral_devourer",
            "phoenix_heart", "gigantification", "clone_hand", "tank_engine", "storm_chaser",
            "calamity_giant", "chain_impact", "explosive_arrow", "immortal_berserker", "iron_stomach", "ultimate_hydra", "magic_missile", "infinite_repeat", "infernal_conductor", "center_of_universe",
            "phantom_weapon", "split_attack", "dual_wielder", "wound_record", "frenzy_rhythm", "survival_instinct",
            "heart_harvester", "blood_crown", "storm_blade", "endless_evolution", "blood_god", "mad_scientist", "draw_the_sword", "last_demon",
            "falling_shockwave", "vein_overload", "blood_trace", "counter_shield", "immortal_armor", "venom_swarm",
            "bounty_hunter", "split_bow", "time_thief", "reflex", "earthquake_mage", "final_judgment",
            "shadow_dash", "time_rewind", "void_lord", "bomb_alchemy", "duel_contract", "world_erosion", "forbidden_reconstruction", "forbidden_deal",
            "kinetic_reversal", "ore_ward", "afterimage_stride", "ricochet_arrow", "frost_bind", "delayed_blast", "triad_reactor", "last_bastion");

    record Rule(AugmentDefinition definition, String group, int threshold, int cap) {
        double amount() { return definition.amount(); }
        int duration() { return definition.duration(); }
        int cooldown() { return definition.cooldown(); }
    }

    final Map<String, Rule> rules = new LinkedHashMap<>();
    final YamlConfiguration config;
    final YamlConfiguration synergies;

    OfficialAugments(JavaPlugin plugin, AugmentRegistry registry) {
        config = load(plugin, "official-augments.yml");
        synergies = load(plugin, "synergies.yml");
        var section = Objects.requireNonNull(config.getConfigurationSection("augments"));
        for (String id : section.getKeys(false)) {
            var rarity = AugmentRarity.valueOf(section.getString(id + ".rarity", ""));
            String group = section.getString(id + ".group", "");
            if (!group.equals("CORE") && !group.equals("ADDITIONAL")) throw new IllegalArgumentException(id);
            boolean bound = BOUND_EFFECTS.contains(id) && section.getBoolean(id + ".implemented");
            var definition = new AugmentDefinition(id, rarity, section.getString(id + ".name", id),
                    section.getString(id + ".condition", "미구현"), " (기본 수치)",
                    AugmentDefinition.Effect.OFFICIAL, section.getDouble(id + ".amount"),
                    section.getInt(id + ".duration"), section.getInt(id + ".cooldown"),
                    bound && section.getBoolean(id + ".enabled"), bound);
            int threshold = section.getInt(id + ".threshold");
            int cap = section.getInt(id + ".cap");
            if (threshold < 0 || cap < 0 || cap > 100_000) throw new IllegalArgumentException(id);
            if (Set.of("blood_record", "mineral_addiction", "sword_wind", "overgrowth", "eureka",
                    "russian_doll", "muscle_memory", "predator", "mineral_devourer", "phoenix_heart", "clone_hand", "tank_engine", "storm_chaser", "chain_impact", "explosive_arrow",
                    "immortal_berserker", "iron_stomach", "ultimate_hydra", "magic_missile", "infinite_repeat", "split_attack", "wound_record", "heart_harvester", "storm_blade",
                    "endless_evolution", "blood_god", "falling_shockwave", "vein_overload", "split_bow", "time_rewind")
                    .contains(id) && threshold < 1) throw new IllegalArgumentException("Invalid threshold: " + id);
            if (Set.of("blood_record", "mineral_addiction", "berserk_engine", "steel_heart", "eureka",
                    "muscle_memory", "mineral_devourer", "tank_engine", "iron_stomach", "immortal_berserker", "wound_record", "frenzy_rhythm",
                    "heart_harvester", "endless_evolution", "blood_god", "draw_the_sword", "vein_overload").contains(id) && cap < 1) {
                throw new IllegalArgumentException("Invalid cap: " + id);
            }
            if ((id.equals("phoenix_heart") || id.equals("russian_doll")) && definition.amount() >= 1) {
                throw new IllegalArgumentException("Invalid fraction: " + id);
            }
            rules.put(id, new Rule(definition, group, threshold, cap));
            registry.register(definition);
        }
        validateReactions();
        if (rules.size() != 72 || rules.values().stream().filter(r -> r.group().equals("CORE")).count() != 30
                || !rules.keySet().containsAll(BOUND_EFFECTS)) throw new IllegalArgumentException("Official catalog incomplete");
        if (count("missile-active-cap") > 40 || count("particles-per-second") > 150
                || count("missile-evolved-count") > count("missile-active-cap") || setting("missile-speed") <= 0
                || setting("hydra-radius") <= 0 || setting("hydra-shockwave-radius") <= 0
                || synergies.getInt("infinite_barrage.missile-multiplier") < 1
                || synergies.getInt("infinite_barrage.missile-multiplier") > 3
                || synergies.getInt("infinite_barrage.bounces") < 0
                || synergies.getInt("infinite_barrage.bounces") > 1
                || synergies.getDouble("infinite_barrage.bounce-radius") <= 0) {
            throw new IllegalArgumentException("Invalid missile settings");
        }
        if (rule("center_of_universe").threshold() < 1 || rule("center_of_universe").threshold() > 12
                || rule("infernal_conductor").cap() < 1 || rule("infernal_conductor").duration() < 1
                || count("burn-evolved-cap") < rule("infernal_conductor").cap()
                || setting("burn-evolution-damage") <= 0 || setting("orb-hit-radius") <= 0
                || setting("orb-radius") <= 0 || synergies.getInt("hell_lord.orb-burn-stacks") < 1) {
            throw new IllegalArgumentException("Invalid orb and burn settings");
        }
        if (count("split-extra-hits") > 3 || !Set.of("steel_heart", "predator", "chain_impact")
                .containsAll(config.getStringList("settings.phantom-melee-effects"))
                || synergies.getDouble("thousand_swords.reach") <= 0
                || synergies.getDouble("thousand_swords.damage") <= 0
                || synergies.getDouble("thousand_swords.side-angle-radians") <= 0) {
            throw new IllegalArgumentException("Invalid linked melee settings");
        }
        if (!(setting("wound-low-health") > 0 && setting("wound-low-health") < 1
                && setting("instinct-low-health") > 0 && setting("instinct-low-health") < 1)
                || setting("storm-blade-radius") <= 0 || setting("blood-crown-absorption") <= 0
                || setting("heart-harvester-player-heal") <= 0) throw new IllegalArgumentException("Invalid reactive effects");
        if (rule("mad_scientist").duration() < 1 || setting("draw-sword-reach") <= 0
                || !(setting("mad-small-dodge-chance") > 0 && setting("mad-small-dodge-chance") <= 1)
                || !(setting("last-demon-low-health") > 0 && setting("last-demon-low-health") < 1)) {
            throw new IllegalArgumentException("Invalid transformation effects");
        }
        if (!(setting("judgment-low-health") > 0 && setting("judgment-low-health") < 1)
                || rule("void_lord").duration() < 1 || rule("earthquake_mage").duration() < 1
                || setting("judgment-radius") <= 0 || setting("void-radius") <= 0
                || setting("reflex-max-distance") <= 0 || setting("reflex-max-distance") > 20
                || setting("quake-dash-distance") <= 0 || setting("quake-dash-distance") > 20
                || setting("shadow-dash-distance") <= 0 || setting("shadow-dash-distance") > 20) {
            throw new IllegalArgumentException("Invalid active augment settings");
        }
        if (rule("forbidden_reconstruction").threshold() < 1 || rule("forbidden_deal").threshold() < 1
                || count("exchange-choice-options") < Math.max(rule("forbidden_reconstruction").threshold(), rule("forbidden_deal").threshold())
                || count("exchange-choice-options") > 9 || setting("forbidden-deal-health-cost") <= 0) {
            throw new IllegalArgumentException("Invalid exchange settings");
        }
        double chance = setting("crit-chance");
        if (chance < 0 || chance > 1 || setting("sword-reach") <= 0 || setting("sword-ray-size") < 0
                || setting("overgrowth-growth") <= 0 || setting("overgrowth-late-health") <= 0
                || setting("giant-relative-scaling") <= 0 || setting("revive-knockback-range") <= 0
                || setting("revive-knockback-strength") < 0 || setting("gigantification-scale") <= 0
                || setting("gigantification-speed") < -.9 || setting("gigantification-speed") > 0
                || setting("gigantification-damage") <= 0 || count("berserk-evolved-cap") < rule("berserk_engine").cap()
                || synergies.getDouble("steel_king.minimum-health") <= 0 || synergies.getDouble("steel_king.radius") <= 0
                || synergies.getDouble("steel_king.damage") <= 0) throw new IllegalArgumentException("Invalid official settings");
    }

    private void validateReactions() {
        for (String id : Set.of("kinetic_reversal", "ore_ward", "afterimage_stride", "ricochet_arrow",
                "frost_bind", "delayed_blast", "triad_reactor", "last_bastion")) {
            var rule = rule(id);
            if (rule.cooldown() <= 0 || rule.cooldown() > 3600 || rule.duration() > 300)
                throw new IllegalArgumentException("Invalid reaction timing: " + id);
            var section = Objects.requireNonNull(config.getConfigurationSection("augments." + id));
            for (var key : section.getKeys(false)) {
                if (!Set.of("window", "speed-ticks", "radius", "push", "splash", "pulse").contains(key)) continue;
                positive(section, key, key.equals("radius") ? 32 : 1200);
            }
            if (Set.of("ore_ward", "ricochet_arrow").contains(id) && rule.cap() <= 0
                    || !Set.of("ricochet_arrow", "triad_reactor").contains(id) && rule.threshold() < 1
                    || !Set.of("ore_ward", "ricochet_arrow").contains(id) && rule.duration() < 1
                    || id.equals("last_bastion") && rule.amount() > 1)
                throw new IllegalArgumentException("Invalid reaction threshold or cap: " + id);
        }
        var followups = Objects.requireNonNull(config.getConfigurationSection("followups"));
        for (String id : followups.getKeys(false)) {
            var section = Objects.requireNonNull(followups.getConfigurationSection(id));
            for (String key : section.getKeys(false)) {
                if (!Set.of("enabled", "sound", "particles", "description").contains(key))
                    positive(section, key, key.equals("radius") ? 32 : 1200);
            }
        }
        for (String id : io.github.augmentwild.augment.AugmentCombinations.UNIQUE.keySet()) {
            int color = synergies.getInt(id + ".color", -1);
            if (color < 0 || color > 0xffffff || synergies.getString(id + ".sound-key", "").isBlank())
                throw new IllegalArgumentException("Invalid synergy appearance: " + id);
        }
    }

    private static void positive(org.bukkit.configuration.ConfigurationSection section, String key, double max) {
        double value = section.getDouble(key);
        if (!Double.isFinite(value) || value <= 0 || value > max)
            throw new IllegalArgumentException("Invalid reaction setting " + section.getCurrentPath() + "." + key);
    }

    Rule rule(String id) { return Objects.requireNonNull(rules.get(id), id); }
    int count(String key) {
        int value = config.getInt("settings." + key);
        if (value < 1) throw new IllegalArgumentException(key);
        return value;
    }
    double setting(String key) {
        double value = config.getDouble("settings." + key);
        if (!Double.isFinite(value)) throw new IllegalArgumentException(key);
        return value;
    }

    static YamlConfiguration load(JavaPlugin plugin, String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.isFile()) plugin.saveResource(name, false);
        var yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (java.io.IOException | org.bukkit.configuration.InvalidConfigurationException failure) {
            throw new IllegalArgumentException("Could not load " + name, failure);
        }
        yaml.options().copyDefaults(true);
        yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(plugin.getResource(name)), StandardCharsets.UTF_8)));
        return yaml;
    }
}
