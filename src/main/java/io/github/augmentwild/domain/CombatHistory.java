package io.github.augmentwild.domain;

import java.util.*;

/** Private, bounded match journal. Display text and immutable evidence travel together. */
public final class CombatHistory {
    private static final int CAPACITY = 30;
    private record Row(CombatEvent event, String text) {}
    private final Map<UUID,Deque<Row>> rows = new HashMap<>();
    private final Set<Object> observed = Collections.newSetFromMap(new WeakHashMap<>());

    public boolean beginObservation(Object event) { return observed.add(event); }

    public void record(UUID id, String row) {
        record(id,null,row);
    }

    public void record(UUID id,CombatEvent event,String text) {
        var recent = rows.computeIfAbsent(id, unused -> new ArrayDeque<>());
        if (recent.size() == CAPACITY) recent.removeLast();
        recent.addFirst(new Row(event,text));
    }

    public List<String> recent(UUID id) {
        return rows.containsKey(id) ? rows.get(id).stream().map(Row::text).toList() : List.of();
    }

    public List<CombatEvent> events(UUID id) {
        var recent = rows.get(id);
        return recent == null ? List.of() : recent.stream().map(Row::event).filter(Objects::nonNull).toList();
    }

    public void clear() {
        rows.clear();
        observed.clear();
    }
}
