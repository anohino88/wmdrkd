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
    public MatchSimulationClock(GameSession session, MatchRules rules, LongSupplier wall, long selectionSeconds,
                                long normalSupplySecond, long premiumSupplySecond) {
        this.session=Objects.requireNonNull(session); this.wall=Objects.requireNonNull(wall);
        if (selectionSeconds<0 || rules.gatheringSeconds()%rules.selectionInterval()!=0) throw new IllegalArgumentException();
        selectionNanos=Math.multiplyExact(selectionSeconds,1_000_000_000L);
        clock=new MatchClock(wall);
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
    public List<String> events() { return List.copyOf(emitted); }
    public void tick() {
        if (session.state()==MatchState.ENDING || session.state()==MatchState.CLEANUP) return;
        if (selecting) {
            if (wall.getAsLong()<selectionEnd) return;
            selecting=false; session.transition(MatchState.GATHERING); clock.resume();
        }
        for (var e : timeline.poll(elapsedSeconds())) {
            emitted.add(e.id());
            if (e.blocks()) {
                session.transition(MatchState.AUGMENT_SELECTION); clock.pause(); selecting=true;
                selectionEnd=Math.addExact(wall.getAsLong(),selectionNanos);
            } else if (e.id().equals("pvp")) session.transition(MatchState.PVP);
            else if (e.id().equals("sudden-death")) session.transition(MatchState.SUDDEN_DEATH);
        }
    }
}
