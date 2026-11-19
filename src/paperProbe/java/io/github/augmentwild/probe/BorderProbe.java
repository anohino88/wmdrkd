package io.github.augmentwild.probe;

import io.github.augmentwild.domain.Arena;
import io.github.augmentwild.augment.DamageChain;
import io.github.augmentwild.augment.ResonanceShield;
import io.github.augmentwild.paper.PaperResonanceShield;
import org.bukkit.event.EventPriority;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
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
    private Pig shieldPig;
    private final ResonanceShield shield = new ResonanceShield();
    private long shieldClock;
    private PaperResonanceShield.Receipt pendingShieldSpend;
    private enum LateDamage { NONE, CANCEL, RESTORE_BASE, ZERO_BASE }
    private LateDamage lateDamage = LateDamage.NONE;
    private boolean sawOutsideBorderDamage;

    @Override public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            @SuppressWarnings("deprecation")
            public void alterAfterShield(EntityDamageEvent event) {
                if (!event.getEntity().equals(shieldPig)) return;
                switch (lateDamage) {
                    case CANCEL -> event.setCancelled(true);
                    case RESTORE_BASE -> event.setDamage(7);
                    case ZERO_BASE -> event.setDamage(0);
                    case NONE -> { }
                }
            }
        }, this);
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
        verifyResonanceShield(spawn);
        new SyntheticCombatLoad(this, spawn).start();
        getLogger().info("AW_RESONANCE_PROBE_PASS: real damage, separate vanilla absorption, exact expiry and cleanup");
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

    private void verifyResonanceShield(Location spawn) {
        shieldPig = spawn.getWorld().spawn(spawn, Pig.class);
        try {
            require(shieldPig.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 0)), "native absorption effect");
            shieldPig.setAbsorptionAmount(2);
            double health = shieldPig.getHealth();
            shieldClock = 1_000_000_000L;
            shield.grant(shieldClock, 4, 6_000_000_000L);
            shieldPig.damage(7);
            require(Math.abs(shieldPig.getHealth() - (health - 1)) < .000001, "virtual shield applies after native absorption");
            require(shield.remaining(shieldClock) < 1e-7 && shieldPig.getAbsorptionAmount() == 0, "both pools spend exactly once");
            shieldPig.setAbsorptionAmount(2);
            shield.grant(shieldClock, 8, 6_000_000_000L);
            shieldClock += 6_000_000_000L;
            require(shield.remaining(shieldClock) == 0, "shield expires at exact game time");
            require(shieldPig.getAbsorptionAmount() == 2, "shield expiry leaves native absorption");
            shield.grant(shieldClock, 4, 6_000_000_000L);
            for (var change : new LateDamage[]{LateDamage.CANCEL, LateDamage.RESTORE_BASE, LateDamage.ZERO_BASE}) {
                shieldPig.setHealth(health);
                shieldPig.setAbsorptionAmount(2);
                shieldPig.setNoDamageTicks(0);
                lateDamage = change;
                shield.grant(shieldClock, 4, 6_000_000_000L);
                shieldPig.damage(7);
                require(shield.remaining(shieldClock) == 4, "late " + change + " must not spend discarded shield");
                if (change == LateDamage.RESTORE_BASE) {
                    require(shieldPig.getHealth() == health - 5 && shieldPig.getAbsorptionAmount() == 0,
                            "replacement keeps native absorption normal");
                } else require(shieldPig.getHealth() == health && shieldPig.getAbsorptionAmount() == 2,
                        "late " + change + " health=" + shieldPig.getHealth() + " nativeAbsorption=" + shieldPig.getAbsorptionAmount());
            }
            shield.clear();
            require(shield.remaining(shieldClock) == 0 && shieldPig.getAbsorptionAmount() == 2, "cleanup leaves native absorption");
        } finally {
            shieldPig.remove();
            shieldPig = null;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void protectProbe(EntityDamageEvent event) {
        if (event.getEntity().equals(shieldPig)) pendingShieldSpend = PaperResonanceShield.protect(event, shield, shieldClock);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void consumeProbe(EntityDamageEvent event) {
        if (event.getEntity().equals(shieldPig) && pendingShieldSpend != null) {
            shield.consume(shieldClock, pendingShieldSpend.confirm(event));
            pendingShieldSpend = null;
        }
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
