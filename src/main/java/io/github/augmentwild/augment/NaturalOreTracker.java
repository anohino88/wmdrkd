package io.github.augmentwild.augment;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class NaturalOreTracker {
    public record Site(UUID world, int x, int y, int z) { }

    private final Set<Site> disturbed = new HashSet<>();
    private final int capacity;
    private boolean exhausted;

    public NaturalOreTracker(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException();
        this.capacity = capacity;
    }

    public boolean consume(Site site) {
        if (exhausted || disturbed.contains(site)) return false;
        invalidate(site);
        return !exhausted;
    }

    public void invalidate(Site site) {
        if (exhausted || disturbed.contains(site)) return;
        if (disturbed.size() >= capacity) {
            exhausted = true;
            return;
        }
        disturbed.add(site);
    }

    public void clear() {
        disturbed.clear();
        exhausted = false;
    }
}
