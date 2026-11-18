package io.github.augmentwild.paper;

import io.github.augmentwild.augment.AugmentDefinition;
import io.github.augmentwild.augment.AugmentDefinition.Effect;
import io.github.augmentwild.augment.AugmentRarity;
import io.github.augmentwild.augment.AugmentRegistry;
import org.bukkit.configuration.file.FileConfiguration;

final class AugmentCatalog {
    private AugmentCatalog() { }

    static AugmentRegistry load(FileConfiguration config) {
        String lowHealth = String.format(java.util.Locale.ROOT, "%.1f%%", config.getDouble("augments.conditions.low-health-fraction") * 100);
        String strong = String.format(java.util.Locale.ROOT, "%.1f", config.getDouble("augments.conditions.strong-hit-points"));
        var registry = new AugmentRegistry();
        add(registry, config, "xp_silver", AugmentRarity.SILVER, "경험 축적", "정상 경험치 획득", "% 추가", Effect.XP);
        add(registry, config, "miner", AugmentRarity.SILVER, "광부의 기세", "드롭이 허용된 광석 채굴", "% 채굴 속도", Effect.MINING);
        add(registry, config, "speed", AugmentRarity.SILVER, "기동 훈련", "생존 중 상시", "% 이동 속도", Effect.SPEED);
        add(registry, config, "heal", AugmentRarity.SILVER, "응급 회복", "피격 후 체력 " + lowHealth + " 이하", " 체력/초", Effect.HEAL);
        add(registry, config, "xp_gold", AugmentRarity.GOLD, "경험 가속", "정상 경험치 획득", "% 추가", Effect.XP);
        add(registry, config, "rear", AugmentRarity.GOLD, "배후 공격", "경기 상대 뒤에서 근접 명중", "% 추가 피해", Effect.REAR);
        add(registry, config, "fall", AugmentRarity.GOLD, "낙하 강타", "낙하 중 경기 상대 근접 명중", "% 추가 피해", Effect.FALL);
        add(registry, config, "armor", AugmentRarity.GOLD, "전투 적응", "경기 상대의 근접 공격에 피격", " 방어력", Effect.ARMOR);
        add(registry, config, "xp_prism", AugmentRarity.PRISMATIC, "경험 폭발", "정상 경험치 획득", "% 추가", Effect.XP);
        add(registry, config, "berserk", AugmentRarity.PRISMATIC, "광전사의 심장", "체력 " + lowHealth + " 이하에서 경기 상대 근접 명중", "% 공격력", Effect.BERSERK);
        add(registry, config, "defense", AugmentRarity.PRISMATIC, "최후의 저항", "경기 상대에게 기본 피해 " + strong + " 이상 피격", "% 피해 감소", Effect.DEFENSE);
        add(registry, config, "pursuit", AugmentRarity.PRISMATIC, "연쇄 추격", "경기 상대에게 근접 명중", "% 이동 속도", Effect.PURSUIT);
        for (var rarity : AugmentRarity.values()) {
            if (registry.selectable().stream().filter(a -> a.rarity() == rarity).count() < (rarity == AugmentRarity.PRISMATIC ? 4 : 3)) {
                throw new IllegalArgumentException("Insufficient implemented augments in " + rarity);
            }
        }
        return registry;
    }

    private static void add(AugmentRegistry registry, FileConfiguration config, String id, AugmentRarity rarity,
                            String name, String condition, String unit, Effect effect) {
        String path = "augments.effects." + id + ".";
        registry.register(new AugmentDefinition(id, rarity, name, condition, unit, effect,
                config.getDouble(path + "amount"), config.getInt(path + "duration"),
                config.getInt(path + "cooldown"), config.getBoolean(path + "enabled"), true));
    }
}
