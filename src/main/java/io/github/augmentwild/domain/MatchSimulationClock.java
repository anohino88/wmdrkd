package io.github.augmentwild.domain;

import io.github.augmentwild.match.*;
import java.util.*;
import java.util.function.LongSupplier;

/** Single-threaded scheduler. Tick at phase boundaries; selection uses wall time. */
public final class MatchSimulationClock {
    private final GameSession session;
    private final MatchClock clock;
    private final LongSupplier wall;
    private final Timeline timeline;
    private final long selectionNanos;
    private final long rulesGatheringSeconds;
    private final long selectionInterval;
    private final Set<UUID> rerolled = new HashSet<>();
    private final List<String> emitted = new ArrayList<>();
    private long selectionEnd;
    @FunctionalInterface public interface OfferProvider {
        Map<UUID,List<SelectionRound.Offer>> offers(UUID matchId, int round, Set<UUID> players);
    }
    private OfferProvider offerProvider;
    private SelectionRound currentSelection;
    private final List<Map<UUID,SelectionRound.Choice>> completedSelections = new ArrayList<>();
    public void setOfferProvider(OfferProvider provider) { if (selecting) throw new IllegalStateException(); offerProvider=Objects.requireNonNull(provider); }
    public Optional<SelectionRound> selection() { return Optional.ofNullable(currentSelection); }
    public List<Map<UUID,SelectionRound.Choice>> completedSelections() { return List.copyOf(completedSelections); }
    public boolean isFinalRound(int round) { return round == rulesGatheringSeconds / selectionInterval; }
    public long selectionRemainingSeconds() {
        long remaining = Math.max(0, selectionEnd - wall.getAsLong());
        return remaining / 1_000_000_000L + (remaining % 1_000_000_000L == 0 ? 0 : 1);
    }
    public boolean hasRerolled(UUID player) { return rerolled.contains(player); }
    public boolean reroll(UUID player, int round, List<SelectionRound.Offer> replacements) {
        tick();
        if (!session.participantIds().contains(player) || !selecting || currentSelection == null
                || round != currentSelection.round() || rerolled.contains(player)
                || !currentSelection.replace(player, replacements, wall.getAsLong())) return false;
        rerolled.add(player);
        return true;
    }
    public boolean choose(UUID player, int round, String offer) {
        tick();
        if (!session.participantIds().contains(player) || !selecting || currentSelection==null) return false;
        boolean accepted=currentSelection.select(session.matchId(),round,player,offer,wall.getAsLong());
        if (currentSelection.complete()) tick();
        return accepted;
    }
    private boolean selecting;
    private boolean manualPause;
    private long virtualNow;
    private long lastWall;
    public MatchSimulationClock(GameSession session, MatchRules rules, LongSupplier wall, long selectionSeconds,
                                long normalSupplySecond, long premiumSupplySecond) {
        this(session, rules, wall, selectionSeconds, List.of(new Timeline.Event("normal-supply",normalSupplySecond,1,false),
                new Timeline.Event("premium-supply",premiumSupplySecond,1,false)));
    }

    public MatchSimulationClock(GameSession session, MatchRules rules, LongSupplier wall, long selectionSeconds,
                                List<Timeline.Event> supplyEvents) {
        this.session=Objects.requireNonNull(session); this.wall=Objects.requireNonNull(wall);
        if (selectionSeconds<0 || rules.gatheringSeconds()%rules.selectionInterval()!=0) throw new IllegalArgumentException();
        selectionNanos=Math.multiplyExact(selectionSeconds,1_000_000_000L);
        rulesGatheringSeconds=rules.gatheringSeconds();
        selectionInterval=rules.selectionInterval();
        lastWall=wall.getAsLong();
        clock=new MatchClock(() -> virtualNow);
        var events=new ArrayList<Timeline.Event>();
        for (long time=rules.selectionInterval(), round=1; time<=rules.gatheringSeconds(); time=Math.addExact(time,rules.selectionInterval()),round++) {
            events.add(new Timeline.Event("selection-"+round,time,0,true));
            if (time==rules.gatheringSeconds()) break;
        }
        events.addAll(supplyEvents);
        events.add(new Timeline.Event("pvp",rules.pvpStartSeconds(),2,false));
        events.add(new Timeline.Event("sudden-death",Math.addExact(rules.pvpStartSeconds(),rules.shrinkSeconds()),3,false));
        timeline=new Timeline(events);
    }
    public long elapsedSeconds() { return clock.elapsed().toSeconds(); }
    public long elapsedNanos() { return clock.elapsed().toNanos(); }
    /** Advance scheduling before handling player work, so reservations use match time. */
    public boolean death(UUID player) { tick(); return session.death(player, elapsedNanos()); }
    public List<UUID> respawnDue() { tick(); return session.respawnDue(elapsedNanos()); }
    public List<String> events() { return List.copyOf(emitted); }
    public boolean isPaused() { return manualPause || selecting; }
    public void pause() { tick(); manualPause=true; }
    public void resume() { tick(); manualPause=false; }
    /** Overshoot at a selection boundary is discarded: selection starts when observed. */
    public void tick() {
        long now=wall.getAsLong();
        long delta=Math.subtractExact(now,lastWall);
        if (delta<0) throw new IllegalStateException("Clock moved backwards");
        lastWall=now;
        if (session.state()==MatchState.ENDING || session.state()==MatchState.CLEANUP) { if (currentSelection!=null) currentSelection.close(); selecting=false; manualPause=false; return; }
        if (selecting) {
            if (currentSelection!=null) {
                for (var id : new HashSet<>(currentSelection.choices().keySet()))
                    if (!session.participantIds().contains(id)) currentSelection.leave(id);
                currentSelection.expire(now);
            }
            if (now<selectionEnd && (currentSelection==null || !currentSelection.complete())) return;
            if (currentSelection!=null) {
                completedSelections.add(currentSelection.choices()); currentSelection.close(); currentSelection=null;
            }
            selecting=false; session.transition(MatchState.GATHERING);
            // Selection time and late observation do not advance match time.
            delta=0;
        }
        if (manualPause) return;
        long target=Math.addExact(virtualNow,delta);
        while (timeline.peek().isPresent()) {
            var next=timeline.peek().orElseThrow();
            long boundary=Math.multiplyExact(next.second(),1_000_000_000L);
            if (boundary>target) break;
            virtualNow=Math.max(virtualNow,boundary);
            for (var e : timeline.poll(next.second())) {
                emitted.add(e.id());
                if (e.blocks()) {
                    session.transition(MatchState.AUGMENT_SELECTION); selecting=true;
                    selectionEnd=Math.addExact(now,selectionNanos);
                    if (offerProvider!=null) {
                        int round=Integer.parseInt(e.id().substring("selection-".length()));
                        var offers=offerProvider.offers(session.matchId(),round,session.participantIds());
                        if (!offers.keySet().equals(session.participantIds())) throw new IllegalArgumentException("offers must cover participants");
                        currentSelection=new SelectionRound(session.matchId(),round,selectionEnd,
                                e.second()==rulesGatheringSeconds,offers);
                    }
                    return;
                } else if (e.id().equals("pvp")) session.transition(MatchState.PVP);
                else if (e.id().equals("sudden-death")) session.transition(MatchState.SUDDEN_DEATH);
            }
        }
        virtualNow=target;
    }
}
