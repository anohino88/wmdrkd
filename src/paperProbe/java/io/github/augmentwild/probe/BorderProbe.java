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
import io.github.augmentwild.paper.MatchInventory;
import io.github.augmentwild.paper.SupplyBlock;
import java.util.List;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
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
    private SyntheticCombatLoad combatLoad;
    private Pig shieldPig;
    private final ResonanceShield shield = new ResonanceShield();
    private long shieldClock;
    private PaperResonanceShield.Receipt pendingShieldSpend;
    private enum LateDamage { NONE, CANCEL, RESTORE_BASE, ZERO_BASE }
    private LateDamage lateDamage = LateDamage.NONE;
    private boolean sawOutsideBorderDamage;

    @Override public void onEnable() {
        String recoveryStage = System.getenv("AW_SUPPLY_CRASH_PHASE");
        if (recoveryStage != null) {
            getServer().getScheduler().runTask(this, () -> {
                try {
                    SupplyCrashProbe.run(this, recoveryStage);
                } catch (Exception | AssertionError failure) {
                    getLogger().log(java.util.logging.Level.SEVERE, "Supply crash fixture failed", failure);
                }
            });
            return;
        }
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

    @Override public void onDisable() {
        if (combatLoad != null) combatLoad.close();
    }

    private void verify() {
        verifyProtectedMenus();
        var console = getServer().getConsoleSender();
        var permission = console.addAttachment(this, "augmentwild.admin.test", false);
        try {
            require(!console.hasPermission("augmentwild.admin.test"), "Paper honours explicit lab permission denial");
            require(getServer().dispatchCommand(console, "aw admin test status"), "lab command routes to permission gate");
        } finally {
            console.removeAttachment(permission);
        }
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
            HudProbe.run(testPig);
            verifyAugmentCatalog();
            verifySyntheticDamage(testPig);
            verifyRicochet(spawn, testPig);
            getLogger().info("AW_AUGMENT_PROBE_PASS: real transient modifiers, official catalog and bounded synthetic health delta");
        } finally {
            testPig.remove();
            testPig = null;
        }
        verifyResonanceShield(spawn);
        verifyPhase64(spawn);
        getLogger().info("AW_PHASE64_PROBE_PASS: diamond debit, lossless reward preflight, marked chest lifecycle and obstructed ray");
        combatLoad = new SyntheticCombatLoad(this, spawn);
        combatLoad.start();
        getLogger().info("AW_RESONANCE_PROBE_PASS: real damage, separate vanilla absorption, exact expiry and cleanup");
        getLogger().info("AW_BORDER_PROBE_PASS: virtual border, unchanged global border, Paper damage event and health delta");
    }

    private void verifyProtectedMenus() {
        class Holder implements io.github.augmentwild.paper.PaperMenu {
            org.bukkit.inventory.Inventory inventory;
            @Override public org.bukkit.inventory.Inventory getInventory() { return inventory; }
        }
        var holder = new Holder();
        holder.inventory = getServer().createInventory(holder, 54);
        require(io.github.augmentwild.paper.PaperMenu.protectedInventory(holder.inventory)
                && !io.github.augmentwild.paper.PaperMenu.protectedInventory(getServer().createInventory(null, 54)),
                "protected plugin inventory recognized by drag guard");
        for (var click : org.bukkit.event.inventory.ClickType.values()) {
            int read = io.github.augmentwild.paper.PaperMenu.controlSlot(click, 53, 54, false);
            require((read == 53) == (click == org.bukkit.event.inventory.ClickType.LEFT), "codex click " + click);
            int choice = io.github.augmentwild.paper.PaperMenu.controlSlot(click, 10, 27, true);
            require((choice == 10) == (click == org.bukkit.event.inventory.ClickType.LEFT
                    || click == org.bukkit.event.inventory.ClickType.RIGHT), "choice click " + click);
        }
        var left = org.bukkit.event.inventory.ClickType.LEFT;
        require(io.github.augmentwild.paper.PaperMenu.controlSlot(left, 54, 54, true) == -1
                && io.github.augmentwild.paper.PaperMenu.controlSlot(left, -999, 54, true) == -1,
                "bottom inventory and outside cannot invoke controls");
        getLogger().info("AW_PHASE68_MENU_PASS");
    }

    private void verifyPhase64(Location spawn) {
        var diamond = new ItemStack(Material.DIAMOND, 2);
        var compass = new ItemStack(Material.COMPASS);
        var original = new ItemStack[]{diamond, compass, null};
        var debit = MatchInventory.remove(original, new ItemStack(Material.AIR), Material.DIAMOND, 1);
        require(debit != null && debit.storage()[0].getAmount() == 1 && diamond.getAmount() == 2
                && debit.storage()[1].equals(compass), "exact cost, compass unchanged");
        require(MatchInventory.remove(original, new ItemStack(Material.AIR), Material.DIAMOND, 3) == null
                && diamond.getAmount() == 2, "failed cost leaves inventory intact");
        var hand = new ItemStack(Material.DIAMOND, 1);
        var offhand = MatchInventory.remove(new ItemStack[]{compass}, hand, Material.DIAMOND, 1);
        require(offhand != null && offhand.offhand().getAmount() == 0 && hand.getAmount() == 1, "offhand debit");
        var arrows = new ItemStack(Material.ARROW, 63);
        var nearlyFull = new ItemStack[]{arrows, compass};
        require(MatchInventory.fit(nearlyFull, 64, List.of(new ItemStack(Material.ARROW, 2))) == null
                && arrows.getAmount() == 63, "failed supplies grant no partial items");
        var fitted = MatchInventory.fit(new ItemStack[]{arrows, null}, 64, List.of(new ItemStack(Material.ARROW, 2)));
        require(fitted != null && fitted[0].getAmount() == 64 && fitted[1].getAmount() == 1
                && arrows.getAmount() == 63, "full reward fits without touching source");

        var stored = new java.util.concurrent.atomic.AtomicReference<ItemStack[]>(nearlyFull);
        var writes = new java.util.concurrent.atomic.AtomicInteger();
        require(MatchInventory.apply(nearlyFull, fitted, contents -> {
            stored.set(contents);
            if (writes.getAndIncrement() == 0) throw new IllegalStateException("partial write fixture");
        }, ignored -> { }) == MatchInventory.Write.ROLLED_BACK && stored.get() == nearlyFull, "retry after known rollback");
        require(MatchInventory.apply(nearlyFull, fitted, contents -> {
            throw new IllegalStateException("write and rollback fixture");
        }, ignored -> { }) == MatchInventory.Write.UNCERTAIN, "unknown write outcome is quarantined");

        var point = new Location(spawn.getWorld(), spawn.getBlockX() + .5, spawn.getBlockY() + 24, spawn.getBlockZ() + .5);
        var target = point.clone().add(5, 0, 0);
        var obstacle = point.clone().add(2, 0, 0).getBlock();
        var marker = point.clone().add(0, 3, 0).getBlock();
        int anchorX = (marker.getX() & 15) == 15 ? marker.getX() - 1 : marker.getX() + 1;
        var anchor = point.getWorld().getBlockAt(anchorX, marker.getY(), marker.getZ());
        var oldAnchor = anchor.getBlockData();
        var oldObstacle = obstacle.getBlockData();
        var oldMarker = marker.getBlockData();
        Pig pig = null;
        try {
            // Keep the chunk section allocated so Paper retains the distinct cave-air state.
            anchor.setType(Material.STONE, false);
            marker.setType(Material.CAVE_AIR, false);
            var expectedAir = marker.getBlockData();
            var block = SupplyBlock.place(marker, new NamespacedKey(this, "phase64"), "probe-chest");
            require(block != null && block.present() && ((Chest) marker.getState()).getBlockInventory().isEmpty(),
                    "physical marker contains no native loot");
            block.remove();
            require(marker.getBlockData().equals(expectedAir) && !block.present(),
                    "marker cleanup expected=" + expectedAir + " actual=" + marker.getBlockData());
            var replacement = SupplyBlock.place(marker, new NamespacedKey(this, "phase64"), "replacement");
            require(replacement != null, "second marker placed");
            block.remove();
            require(replacement.present(), "cleanup preserves a different owner token");
            replacement.remove();

            obstacle.setType(Material.AIR, false);
            pig = point.getWorld().spawn(target, Pig.class);
            pig.setGravity(false);
            Pig visible = pig;
            var eye = point.clone().add(0, .5, 0);
            var east = new Vector(1, 0, 0);
            var hit = point.getWorld().rayTrace(eye, east, 48, FluidCollisionMode.NEVER, true, 0, entity -> entity.equals(visible));
            require(hit != null && visible.equals(hit.getHitEntity()), "unobstructed Paper ray");
            require(io.github.augmentwild.paper.TargetSight.trace(eye,east,visible::equals).getHitEntity() == visible,
                    "24-block target HUD ray");
            var far = eye.clone().subtract(25,0,0);
            var farHit = io.github.augmentwild.paper.TargetSight.trace(far,east,visible::equals);
            require(farHit == null || farHit.getHitEntity() == null, "HUD range excludes distant target");
            var shortRay = point.getWorld().rayTrace(eye, east, 3, FluidCollisionMode.NEVER, true, 0, entity -> entity.equals(visible));
            require(shortRay == null, "distance cap");
            obstacle.setType(Material.STONE, false);
            var blocked = point.getWorld().rayTrace(eye, east, 48, FluidCollisionMode.NEVER, true, 0, entity -> entity.equals(visible));
            require(blocked != null && obstacle.equals(blocked.getHitBlock()) && blocked.getHitEntity() == null, "wall blocks sight");
            require(io.github.augmentwild.paper.TargetSight.trace(eye,east,visible::equals).getHitEntity()==null,"HUD wall occlusion");
        } finally {
            if (pig != null) pig.remove();
            obstacle.setBlockData(oldObstacle, false);
            marker.setBlockData(oldMarker, false);
            anchor.setBlockData(oldAnchor, false);
        }
    }

    private void verifyAugmentCatalog() {
        var main = getServer().getPluginManager().getPlugin("AugmentWild");
        require(main != null && main.isEnabled(), "AugmentWild is enabled");
        var yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(
                main.getResource("official-augments.yml"), StandardCharsets.UTF_8));
        var section = yaml.getConfigurationSection("augments");
        require(section != null && section.getKeys(false).size() == 72, "official config loaded");
        require(section.getKeys(false).stream().allMatch(id -> section.getBoolean(id + ".implemented")
                && section.getBoolean(id + ".enabled")), "configured official effects");
    }

    private void verifyRicochet(Location spawn, Pig target) {
        var source = spawn.getWorld().spawn(spawn, Pig.class);
        try {
            double before = target.getHealth();
            var arrow = org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.ARROW)
                    .withCausingEntity(source).withDirectEntity(source).build();
            target.setNoDamageTicks(0);
            target.damage(io.github.augmentwild.augment.ReactionRules.ricochet(8, .35, 4), arrow);
            require(Math.abs(before - target.getHealth() - 2.8) < 1e-5, "server-owned ricochet health delta");
            var damage = target.getLastDamageCause();
            require(damage != null && source.equals(damage.getDamageSource().getCausingEntity())
                    && damage.getDamageSource().getDamageType() == org.bukkit.damage.DamageType.ARROW, "ricochet provenance");
            getLogger().info("AW_REACTION_PROBE_PASS: arrow damage type, owner and health delta on live entities");
        } finally {
            source.remove();
        }
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
            verifyMitigatedShield(health);
        } finally {
            shieldPig.remove();
            shieldPig = null;
        }
    }

    private void verifyMitigatedShield(double maxHealth) {
        lateDamage = LateDamage.NONE;
        shieldPig.removePotionEffect(PotionEffectType.ABSORPTION);
        shieldPig.setAbsorptionAmount(0);
        var armor = shieldPig.getAttribute(Attribute.ARMOR);
        require(armor != null, "native armor attribute");
        armor.setBaseValue(8);
        shieldPig.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 0));
        shieldPig.setHealth(maxHealth);
        shieldPig.setNoDamageTicks(0);
        shieldPig.damage(7);
        double mitigated = maxHealth - shieldPig.getHealth();
        require(mitigated > 2 && mitigated < 7, "native armor and resistance reduce damage");

        shieldPig.setHealth(maxHealth);
        shieldPig.setNoDamageTicks(0);
        shield.grant(shieldClock, 2, 6_000_000_000L);
        shieldPig.damage(7);
        require(Math.abs(maxHealth - shieldPig.getHealth() - (mitigated - 2)) < .00001,
                "shield reduces final health damage by two points with armor and resistance");
        require(shield.remaining(shieldClock) < 1e-7, "mitigated shield debited once");
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
