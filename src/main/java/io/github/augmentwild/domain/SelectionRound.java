package io.github.augmentwild.domain;

import java.util.*;

/** One selection round; all methods must be called by the session owner thread. */
public final class SelectionRound {
    public enum Status { PENDING, SELECTED, AUTOMATIC, NO_OPTIONS }
    public record Offer(String id, boolean prismatic, boolean implemented) {
        public Offer { Objects.requireNonNull(id); if (id.isBlank()) throw new IllegalArgumentException(); }
    }
    public record Choice(List<Offer> offers, String selected, Status status) {
        public Choice { offers=List.copyOf(offers); }
    }
    private final UUID matchId;
    private final int round;
    private final long deadline;
    private final Map<UUID,Choice> choices = new LinkedHashMap<>();
    private final boolean prismaticOnly;
    private boolean closed;
    public SelectionRound(UUID matchId, int round, long deadline, boolean prismaticOnly, Map<UUID,List<Offer>> offers) {
        this.matchId=Objects.requireNonNull(matchId);
        if (round<1 || deadline<0) throw new IllegalArgumentException();
        this.round=round; this.deadline=deadline; this.prismaticOnly=prismaticOnly;
        offers.forEach((id,list) -> {
            Objects.requireNonNull(id);
            var valid=list.stream().filter(Offer::implemented).filter(o -> !prismaticOnly || o.prismatic()).toList();
            if (valid.stream().map(Offer::id).distinct().count()!=valid.size()) throw new IllegalArgumentException("duplicate offer");
            choices.put(id,new Choice(valid,null,valid.isEmpty()?Status.NO_OPTIONS:Status.PENDING));
        });
    }
    public UUID matchId() { return matchId; }
    public int round() { return round; }
    public Map<UUID,Choice> choices() { return Map.copyOf(choices); }
    public boolean complete() { return choices.values().stream().noneMatch(c -> c.status()==Status.PENDING); }
    public boolean select(UUID match, int expectedRound, UUID player, String id, long now) {
        if (closed || !matchId.equals(match) || round!=expectedRound || now>=deadline) return false;
        var choice=choices.get(player);
        if (choice==null || choice.status()!=Status.PENDING || choice.offers().stream().noneMatch(o -> o.id().equals(id))) return false;
        choices.put(player,new Choice(choice.offers(),id,Status.SELECTED)); return true;
    }
    public long deadline() { return deadline; }
    public boolean prismaticOnly() { return prismaticOnly; }
    public boolean replace(UUID player, List<Offer> offers, long now) {
        var saved = choices.get(player);
        if (closed || now >= deadline || saved == null || saved.status() != Status.PENDING
                || offers.size() != 3 || offers.stream().anyMatch(o -> !o.implemented() || (prismaticOnly && !o.prismatic()))
                || offers.stream().map(Offer::id).distinct().count() != 3) return false;
        choices.put(player, new Choice(offers, null, Status.PENDING));
        return true;
    }

    public void expire(long now) {
        if (closed || now<deadline) return;
        choices.replaceAll((id,c) -> c.status()==Status.PENDING
                ? new Choice(c.offers(),c.offers().stream().map(Offer::id).sorted().findFirst().orElseThrow(),Status.AUTOMATIC) : c);
    }
    public void leave(UUID id) { if (!closed) choices.remove(id); }
    public void close() { closed=true; }
}
