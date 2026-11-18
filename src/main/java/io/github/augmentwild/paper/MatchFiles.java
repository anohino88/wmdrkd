package io.github.augmentwild.paper;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

final class MatchFiles {
    private MatchFiles() { }

    static YamlConfiguration load(Path path) throws IOException, InvalidConfigurationException {
        var yaml = new YamlConfiguration();
        if (Files.exists(path)) yaml.load(path.toFile());
        return yaml;
    }

    static void save(Path path, YamlConfiguration yaml) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), ".augmentwild-", ".tmp");
        try {
            yaml.save(temporary.toFile());
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
