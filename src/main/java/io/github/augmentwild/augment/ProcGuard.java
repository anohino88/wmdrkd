package io.github.augmentwild.augment;

import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

/** Main-thread event deduplication and synchronous chain-depth guard. */
public final class ProcGuard {
    private final int maximum;
    private final WeakHashMap<Object, Set<Object>> processed = new WeakHashMap<>();
    private int depth;

    public ProcGuard(int maximum) {
        if (maximum < 0) throw new IllegalArgumentException();
        this.maximum = maximum;
    }

    public boolean run(Object event, Object key, Runnable action) {
        if (depth >= maximum || !processed.computeIfAbsent(event, ignored -> new HashSet<>()).add(key)) return false;
        depth++;
        try {
            action.run();
            return true;
        } finally {
            depth--;
        }
    }
}
