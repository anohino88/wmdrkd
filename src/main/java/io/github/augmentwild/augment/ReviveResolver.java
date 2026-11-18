package io.github.augmentwild.augment;

import java.util.List;
import java.util.function.Predicate;

public final class ReviveResolver {
    public record Option(String id, int uses) {
        public Option {
            if (uses < 1) throw new IllegalArgumentException("Invalid revive uses");
        }
    }

    private ReviveResolver() { }

    /** A rejected effect leaves its uses intact; only the first successful effect is consumed. */
    public static String resolve(AugmentProgress progress, List<Option> priority, Predicate<String> apply) {
        for (var option : priority) {
            String key = "revive:" + option.id();
            if (progress.value(key) < option.uses() && apply.test(option.id())) {
                progress.add(key, 1, option.uses());
                return option.id();
            }
        }
        return null;
    }
}
