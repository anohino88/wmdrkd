package io.github.augmentwild.domain;

import java.util.*;

/** Ordered, once-only events. Call poll again after completing a blocking selection. */
public final class Timeline {
    public record Event(String id, long second, int order, boolean blocks) {
        public Event { Objects.requireNonNull(id); if (second<0) throw new IllegalArgumentException(); }
    }
    private final List<Event> events;
    private int next;
    public Timeline(Collection<Event> events) {
        this.events=events.stream().sorted(Comparator.comparingLong(Event::second).thenComparingInt(Event::order).thenComparing(Event::id)).toList();
        if (this.events.stream().map(Event::id).distinct().count()!=this.events.size()) throw new IllegalArgumentException("duplicate event ID");
    }
    public List<Event> poll(long elapsedSeconds) {
        if (elapsedSeconds<0) throw new IllegalArgumentException();
        var due=new ArrayList<Event>();
        while (next<events.size() && events.get(next).second()<=elapsedSeconds) {
            var event=events.get(next++); due.add(event);
            if (event.blocks()) break;
        }
        return List.copyOf(due);
    }
}
