package io.github.augmentwild.probe;

import io.github.augmentwild.domain.Arena;
import io.github.augmentwild.augment.DamageChain;
import java.util.UUID;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import io.github.augmentwild.paper.PaperMatchBorder;
import io.github.augmentwild.paper.PaperAugmentAttribute;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.Location;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Pig;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Test-only Paper plugin. This probe does not create or simulate a player connection. */
public final class BorderProbe extends JavaPlugin implements Listener {
    private Pig testPig;
    private boolean sawOutsideBorderDamage;

    @Override public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTask(this, this::verify);
    }

    private void verify() {
        var world = getServer().getWorlds().getFirst();
        var global = world.getWorldBorder();
        double originalSize = global.getSize();
        var originalCenter = global.getCenter();
        var visual = PaperMatchBorder.createDisplay(getServer(), new Arena(41, -37, 1000, 1000));
        require(visual.getWorld() == null && visual.getSize() == 1000, "virtual border");
        require(visual.getCenter().getX() == 41 && visual.getCenter().getZ() == -37, "virtual center");
        visual.setSize(60);
        require(global.getSize() == originalSize && global.getCenter().equals(originalCenter), "global border");
        Location spawn = world.getSpawnLocation();
        testPig = world.spawn(spawn, Pig.class);
        try {
            double before = testPig.getHealth();
            PaperMatchBorder.applyDamage(testPig, 2);
            require(sawOutsideBorderDamage, "Paper outside-border damage event");
            require(testPig.getHealth() == before - 2, "actual health delta");
            var speed = testPig.getAttribute(Attribute.MOVEMENT_SPEED);
            require(speed != null, "movement attribute");
            double initialSpeed = speed.getValue();
            var key = new NamespacedKey(this, "probe_speed");
            PaperAugmentAttribute.update(speed, key, .08, AttributeModifier.Operation.ADD_SCALAR, true);
            PaperAugmentAttribute.update(speed, key, .08, AttributeModifier.Operation.ADD_SCALAR, true);
            require(Math.abs(speed.getValue() - initialSpeed * 1.08) < .000001, "actual gain with no duplicate");
            PaperAugmentAttribute.update(speed, key, .16, AttributeModifier.Operation.ADD_SCALAR, true);
            require(Math.abs(speed.getValue() - initialSpeed * 1.16) < .000001, "stack growth replaces existing modifier");
            PaperAugmentAttribute.update(speed, key, .16, AttributeModifier.Operation.ADD_SCALAR, false);
            require(speed.getModifier(key) == null && speed.getValue() == initialSpeed, "modifier restore");
            verifyAugmentCatalog();
            verifySyntheticDamage(testPig);
            getLogger().info("AW_AUGMENT_PROBE_PASS: real transient modifiers, official catalog and bounded synthetic health delta");
        } finally {
            testPig.remove();
            testPig = null;
        }
        getLogger().info("AW_BORDER_PROBE_PASS: virtual border, unchanged global border, Paper damage event and health delta");
    }

    private void verifyAugmentCatalog() {
        var main = getServer().getPluginManager().getPlugin("AugmentWild");
        require(main != null && main.isEnabled(), "AugmentWild is enabled");
        var yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(
                main.getResource("official-augments.yml"), StandardCharsets.UTF_8));
        var section = yaml.getConfigurationSection("augments");
        require(section != null && section.getKeys(false).size() == 64, "official config loaded");
        require(section.getKeys(false).stream().allMatch(id -> section.getBoolean(id + ".implemented")
                && section.getBoolean(id + ".enabled")), "configured official effects");
    }

    private void verifySyntheticDamage(Pig pig) {
        var chain = new DamageChain(2, 1);
        var match = UUID.randomUUID();
        var source = UUID.randomUUID();
        double before = pig.getHealth();
        chain.root(() -> {
            require(chain.hit(match, source, pig.getUniqueId(), "probe", 2, false, () -> {
                pig.setNoDamageTicks(0);
                pig.damage(2);
                return pig.getHealth() < before;
            }), "real synthetic effect damage");
            require(!chain.hit(match, source, pig.getUniqueId(), "probe", 2, false, () -> {
                throw new AssertionError("repeated victim cannot be damaged");
            }), "hit limit across event callbacks");
        });
        require(Math.abs(pig.getHealth() - (before - 2)) < .000001, "single real health delta");
    }

    @EventHandler
    public void observeDamage(EntityDamageEvent event) {
        if (event.getEntity().equals(testPig) && event.getDamageSource().getDamageType() == DamageType.OUTSIDE_BORDER) {
            sawOutsideBorderDamage = true;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
