package io.github.augmentwild.domain;

import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;

public final class SupplyPlan {
    private SupplyPlan() { }

    public record Window(int first, int last) {
        public Window {
            if (first < 0 || last < first || last == Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid supply window");
        }

        int pick(RandomGenerator random) { return random.nextInt(first, last + 1); }
    }

    public record Drop(String id, boolean premium, int second) {
        public long announceAt(long lead) { return second - lead; }
        public long publishAt(long lead) { return second - lead; }
    }

    public static List<Drop> draw(List<Window> windows, RandomGenerator random) {
        if (windows.size() != 3) throw new IllegalArgumentException("Three supply windows required");
        return List.of(new Drop("normal-supply-1", false, windows.get(0).pick(random)),
                new Drop("normal-supply-2", false, windows.get(1).pick(random)),
                new Drop("premium-supply", true, windows.get(2).pick(random)));
    }

    /** One match-thread claim. A failed inventory grant releases the reservation. */
    public static final class Claim {
        private UUID reserved;
        private boolean consumed;

        public boolean reserve(UUID player) {
            if (consumed || reserved != null) return false;
            reserved = player;
            return true;
        }

        public void finish(UUID player, boolean delivered) {
            if (!player.equals(reserved)) throw new IllegalStateException("Supply claim belongs to a different player");
            consumed = delivered;
            reserved = null;
        }
    }
}
