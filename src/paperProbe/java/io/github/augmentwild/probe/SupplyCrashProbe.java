package io.github.augmentwild.probe;

import io.github.augmentwild.domain.SupplyPlan;
import io.github.augmentwild.paper.MatchInventory;
import io.github.augmentwild.paper.SupplyBlock;
import io.github.augmentwild.paper.SupplyRecovery;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Used exclusively by the isolated restart test. */
final class SupplyCrashProbe {
    private SupplyCrashProbe() { }

    static void run(JavaPlugin probe, String stage) throws Exception {
        var main = (JavaPlugin) probe.getServer().getPluginManager().getPlugin("AugmentWild");
        require(main != null && main.isEnabled(), "main plugin is enabled");
        var world = probe.getServer().getWorlds().getFirst();
        require(world.getName().equals("supply-crash-fixture"), "disposable world is required");
        var recovery = new SupplyRecovery(main);
        var tag = new NamespacedKey(main, "supply_delivery");
        if (stage.equals("seed")) {
            UUID match = UUID.randomUUID();
            // Validate a clean journal round-trip before preparing the crash fixtures.
            var restored = recovery.plan(match, site(world, 0));
            require(recovery.place(restored, site(world, 0)) != null && recovery.recover(), "normal recovery");

            recovery.plan(match, site(world, 1));
            var placed = recovery.plan(match, site(world, 2));
            require(recovery.place(placed, site(world, 2)) != null, "saved placed marker");
            var between = recovery.plan(match, site(world, 3));
            require(SupplyBlock.place(site(world, 3), tag, between.token().toString()) != null, "planned marker with correct token");

            recovery.plan(match, site(world, 4));
            require(SupplyBlock.place(site(world, 4), tag, UUID.randomUUID().toString()) != null, "different owner marker");
            var occupied = recovery.plan(match, site(world, 5));
            require(recovery.place(occupied, site(world, 5)) != null, "occupied fixture marker");
            ((Chest) site(world, 5).getState()).getBlockInventory().addItem(new ItemStack(Material.DIAMOND));

            var removed = recovery.plan(match, site(world, 6));
            var removedMarker = recovery.place(removed, site(world, 6));
            require(removedMarker != null, "claimed marker fixture");
            world.save(true);
            removedMarker.remove(); // Simulates removal after grant before the modified chunk is flushed.
            var volatileEntry = recovery.plan(match, site(world, 7));
            require(recovery.place(volatileEntry, site(world, 7)) != null, "placement immediately before shutdown");
            // Detached Paper inventories model separate player save files without logging in fake players.
            // Save one copy after the write and leave the other at its pre-claim snapshot.
            payout(probe, "durable-payout", true);
            payout(probe, "volatile-payout", false);
            probe.getLogger().info("AW_SUPPLY_CRASH_SEEDED");
        } else if (stage.equals("restart") || stage.equals("repeat")) {
            for (int index : new int[]{0, 1, 2, 3, 6, 7}) {
                require(site(world, index).getType().isAir(), "recovered air at fixture " + index);
            }
            require(site(world, 4).getType() == Material.CHEST, "foreign token preserved");
            require(site(world, 5).getState() instanceof Chest chest && chest.getBlockInventory().contains(Material.DIAMOND),
                    "matching chest with items preserved");
            var journal = main.getDataFolder().toPath().resolve("supply-recovery");
            try (var files = Files.list(journal)) {
                require(files.filter(path -> path.toString().endsWith(".yml")).count() == 2, "only unresolved entries retained");
            }
            require(!recovery.recover(), "unresolved journal blocks match start and retry is idempotent");
            var durable = savedItem(probe, "durable-payout");
            require(durable.getType() == Material.DIAMOND && durable.getAmount() == 1, "saved reward survives once");
            require(savedItem(probe, "volatile-payout").getType() == Material.COBBLESTONE, "unsaved reward lost, not replayed");
            probe.getLogger().info("AW_PAYOUT_SAVE_BOUNDARIES_PASS " + stage + " durable=1 volatile=0 clients=0");
            probe.getLogger().info("AW_SUPPLY_CRASH_PASS " + stage);
        } else throw new IllegalArgumentException("Unknown recovery fixture phase");
    }

    private static void payout(JavaPlugin probe, String key, boolean saveAfter) throws Exception {
        var inventory = probe.getServer().createInventory(null, 9);
        inventory.setItem(0, new ItemStack(Material.COBBLESTONE, 64));
        var file = probe.getDataFolder().toPath().resolve(key);
        Files.createDirectories(file.getParent());
        Files.write(file, inventory.getItem(0).serializeAsBytes());
        var before = inventory.getStorageContents();
        var reward = new ItemStack(Material.DIAMOND);
        var after = MatchInventory.fit(before, inventory.getMaxStackSize(), List.of(reward));
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var claim = new SupplyPlan.Claim();
        require(after != null && claim.reserve(a) && !claim.reserve(b), "detached payout preflight and contention");
        require(MatchInventory.apply(before, after, inventory::setStorageContents, failure -> {
            throw failure;
        }) == MatchInventory.Write.DELIVERED, "payout reached memory");
        claim.finish(a, true);
        require(!claim.reserve(b) && inventory.contains(Material.DIAMOND, 1), "only one in-memory payout");
        if (saveAfter) Files.write(file, inventory.getItem(1).serializeAsBytes());
    }

    private static ItemStack savedItem(JavaPlugin probe, String key) throws Exception {
        return ItemStack.deserializeBytes(Files.readAllBytes(probe.getDataFolder().toPath().resolve(key)));
    }

    private static Block site(World world, int index) {
        var spawn = world.getSpawnLocation();
        return world.getBlockAt((spawn.getBlockX() & ~15) + (index % 4) * 3 + 1, spawn.getBlockY() + 24,
                (spawn.getBlockZ() & ~15) + (index / 4) * 4 + 2);
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
