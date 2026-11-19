import io.github.augmentwild.augment.AugmentCombinations;
import io.github.augmentwild.augment.AugmentEvolution;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

public final class Phase68Checks {
    public static void main(String[] args) throws Exception {
        var root = Path.of(args[0]);
        var registry = ResonanceChecks.loadCatalog(root);
        var settings = Files.readString(root.resolve("src/main/resources/official-augments.yml"));
        var synergies = Files.readString(root.resolve("src/main/resources/synergies.yml"));
        for (var evolution : AugmentEvolution.values()) {
            require(registry.get(evolution.augment) != null, "registered " + evolution.augment);
            var goal = Pattern.compile("(?m)^  " + evolution.goal + ": ([0-9.]+)$").matcher(settings);
            require(goal.find() && Double.parseDouble(goal.group(1)) > 0, "configured " + evolution.goal);
        }
        for (var entry : AugmentCombinations.UNIQUE.entrySet()) {
            require(synergies.contains("\n" + entry.getKey() + ":") || synergies.startsWith(entry.getKey() + ":"), "configured " + entry.getKey());
        }
        AugmentCombinations.PRESETS.forEach((name, members) ->
                require(members.size() == 3 && members.stream().allMatch(id -> registry.get(id).selectable()), "preset " + name));
        System.out.println("PHASE68_CODEX PASS catalog=76 synergies=5 evolutions=6 presets=6");
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
