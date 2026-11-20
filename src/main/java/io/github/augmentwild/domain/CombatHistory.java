package io.github.augmentwild.domain;

import java.util.*;

/** Private, match-scoped journal; rows contain text only, never server entities. */
public final class CombatHistory {
    private static final int CAPACITY = 30;
    private final Map<UUID,Deque<String>> rows = new HashMap<>();
    private final Set<Object> observed = Collections.newSetFromMap(new WeakHashMap<>());

    public boolean beginObservation(Object event) { return observed.add(event); }

    public void record(UUID id, String row) {
        var recent = rows.computeIfAbsent(id, unused -> new ArrayDeque<>());
        if (recent.size() == CAPACITY) recent.removeLast();
        recent.addFirst(row);
    }

    public List<String> recent(UUID id) {
        return rows.containsKey(id) ? List.copyOf(rows.get(id)) : List.of();
    }

    public void clear() {
        rows.clear();
        observed.clear();
    }
}
