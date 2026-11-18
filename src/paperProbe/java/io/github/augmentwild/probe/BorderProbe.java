package io.github.augmentwild.probe;

import io.github.augmentwild.domain.Arena;
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
            getLogger().info("AW_AUGMENT_PROBE_PASS: real transient attribute applied once and removed");
        } finally {
            testPig.remove();
            testPig = null;
        }
        getLogger().info("AW_BORDER_PROBE_PASS: virtual border, unchanged global border, Paper damage event and health delta");
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
