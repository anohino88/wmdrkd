package io.github.augmentwild.probe;

import io.github.augmentwild.domain.Arena;
import io.github.augmentwild.paper.PaperMatchBorder;
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
