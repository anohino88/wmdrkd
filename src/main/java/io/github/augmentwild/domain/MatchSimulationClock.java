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
    private final List<String> emitted = new ArrayList<>();
    private long selectionEnd;
    private boolean selecting;
    private boolean manualPause;
    private long virtualNow;
    private long lastWall;
    public MatchSimulationClock(GameSession session, MatchRules rules, LongSupplier wall, long selectionSeconds,
                                long normalSupplySecond, long premiumSupplySecond) {
        this.session=Objects.requireNonNull(session); this.wall=Objects.requireNonNull(wall);
        if (selectionSeconds<0 || rules.gatheringSeconds()%rules.selectionInterval()!=0) throw new IllegalArgumentException();
        selectionNanos=Math.multiplyExact(selectionSeconds,1_000_000_000L);
        lastWall=wall.getAsLong();
        clock=new MatchClock(() -> virtualNow);
        var events=new ArrayList<Timeline.Event>();
        for (long time=rules.selectionInterval(), round=1; time<=rules.gatheringSeconds(); time=Math.addExact(time,rules.selectionInterval()),round++) {
            events.add(new Timeline.Event("selection-"+round,time,0,true));
            if (time==rules.gatheringSeconds()) break;
        }
        events.add(new Timeline.Event("normal-supply",normalSupplySecond,1,false));
        events.add(new Timeline.Event("premium-supply",premiumSupplySecond,1,false));
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
        if (session.state()==MatchState.ENDING || session.state()==MatchState.CLEANUP) { selecting=false; manualPause=false; return; }
        if (selecting) {
            if (now<selectionEnd) return;
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
                    return;
                } else if (e.id().equals("pvp")) session.transition(MatchState.PVP);
                else if (e.id().equals("sudden-death")) session.transition(MatchState.SUDDEN_DEATH);
            }
        }
        virtualNow=target;
    }
}
