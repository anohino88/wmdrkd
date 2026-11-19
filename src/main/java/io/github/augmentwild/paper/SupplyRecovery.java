package io.github.augmentwild.paper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

/** Write-ahead journal for world markers only. Loot and opening attempts never survive a restart. */
public final class SupplyRecovery {
    public enum State { PLANNED, PLACED, RESTORING }
    public record Entry(UUID match, UUID world, String worldName, int x, int y, int z, String previous,
                        UUID token, State state) {
        Entry in(State next) { return new Entry(match, world, worldName, x, y, z, previous, token, next); }
    }

    private final JavaPlugin plugin;
    private final Path folder;
    private final NamespacedKey key;

    public SupplyRecovery(JavaPlugin plugin) {
        this.plugin = plugin;
        folder = plugin.getDataFolder().toPath().resolve("supply-recovery");
        key = new NamespacedKey(plugin, "supply_delivery");
    }

    public Entry plan(UUID match, Block block) throws IOException {
        if (!block.getType().isAir()) throw new IllegalArgumentException("Supply marker requires air");
        World world = block.getWorld();
        var entry = new Entry(match, world.getUID(), world.getName(), block.getX(), block.getY(), block.getZ(),
                block.getBlockData().getAsString(), UUID.randomUUID(), State.PLANNED);
        save(entry);
        return entry;
    }

    public SupplyBlock place(Entry entry, Block block) throws IOException {
        if (entry.state() != State.PLANNED || !entry.world().equals(block.getWorld().getUID())
                || entry.x() != block.getX() || entry.y() != block.getY() || entry.z() != block.getZ()
                || !entry.previous().equals(block.getBlockData().getAsString())) {
            throw new IllegalArgumentException("Supply marker no longer matches its plan");
        }
        SupplyBlock marker = SupplyBlock.place(block, key, entry.token().toString());
        if (marker != null) save(entry.in(State.PLACED));
        return marker;
    }

    private Path path(UUID token) { return folder.resolve(token + ".yml"); }

    private void save(Entry entry) throws IOException {
        var yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("match", entry.match().toString());
        yaml.set("world", entry.world().toString());
        yaml.set("world-name", entry.worldName());
        yaml.set("x", entry.x());
        yaml.set("y", entry.y());
        yaml.set("z", entry.z());
        yaml.set("previous", entry.previous());
        yaml.set("token", entry.token().toString());
        yaml.set("state", entry.state().name());
        MatchFiles.save(path(entry.token()), yaml);
    }

    private Entry read(Path path) throws IOException, org.bukkit.configuration.InvalidConfigurationException {
        var yaml = MatchFiles.load(path);
        if (!yaml.isInt("x") || !yaml.isInt("y") || !yaml.isInt("z")) {
            throw new IllegalArgumentException("Incomplete marker coordinates");
        }
        UUID token = UUID.fromString(yaml.getString("token", ""));
        if (!path(token).equals(path)) throw new IllegalArgumentException("Marker token differs from file name");
        String name = yaml.getString("world-name", "");
        if (name.isBlank()) throw new IllegalArgumentException("Missing world name");
        return new Entry(UUID.fromString(yaml.getString("match", "")), UUID.fromString(yaml.getString("world", "")),
                name, yaml.getInt("x"), yaml.getInt("y"), yaml.getInt("z"), yaml.getString("previous", ""),
                token, State.valueOf(yaml.getString("state", "")));
    }

    /** A successful flush must precede journal deletion, or a crash can resurrect a saved chest. */
    public boolean recover() {
        if (!Files.exists(folder)) return true;
        Map<World, List<Entry>> ready = new LinkedHashMap<>();
        boolean clean = true;
        try (var entries = Files.list(folder)) {
            for (Path path : entries.filter(file -> file.getFileName().toString().endsWith(".yml")).sorted().toList()) {
                try {
                    var entry = read(path);
                    World world = plugin.getServer().getWorld(entry.world());
                    if (world == null || entry.y() < world.getMinHeight() || entry.y() >= world.getMaxHeight()
                            || !world.loadChunk(entry.x() >> 4, entry.z() >> 4, false)) {
                        throw new IllegalStateException("World or existing chunk unavailable: " + entry.worldName());
                    }
                    Block block = world.getBlockAt(entry.x(), entry.y(), entry.z());
                    var previous = plugin.getServer().createBlockData(entry.previous());
                    if (!previous.getMaterial().isAir()) throw new IllegalStateException("Original block was not air");
                    if (!block.getType().isAir()) {
                        if (!SupplyBlock.recoverable(block, key, entry.token().toString())) {
                            throw new IllegalStateException("Block is not an empty, owned single supply chest");
                        }
                        save(entry.in(State.RESTORING));
                        block.setBlockData(previous, false);
                        if (!block.getType().isAir()) throw new IllegalStateException("Block restoration was rejected");
                    }
                    ready.computeIfAbsent(world, ignored -> new ArrayList<>()).add(entry);
                } catch (IOException | org.bukkit.configuration.InvalidConfigurationException | RuntimeException failure) {
                    clean = false;
                    plugin.getLogger().log(Level.WARNING, "Supply recovery retained " + path.getFileName(), failure);
                }
            }
        } catch (IOException | RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "Could not read supply recovery journal", failure);
            return false;
        }
        for (var batch : ready.entrySet()) {
            try {
                batch.getKey().save(true);
                for (Entry entry : batch.getValue()) Files.deleteIfExists(path(entry.token()));
            } catch (IOException | RuntimeException failure) {
                clean = false;
                plugin.getLogger().log(Level.WARNING, "Could not commit supply recovery for " + batch.getKey().getName(), failure);
            }
        }
        return clean;
    }
}
