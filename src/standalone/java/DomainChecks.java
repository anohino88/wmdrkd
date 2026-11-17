import io.github.augmentwild.domain.*;
import io.github.augmentwild.match.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class DomainChecks {
 static int count;
 static void check(boolean value) { count++; if (!value) throw new AssertionError("check " + count); }
 static void reject(Runnable r) { count++; try { r.run(); } catch (IllegalArgumentException | IllegalStateException e) { return; } throw new AssertionError("check " + count); }
 static MatchRules rules() { return new MatchRules(1800,1820,20,2,1000,5000,80,1000,60,600,1,-1,300,3,80,18,2,2); }
 public static void main(String[] args) {
  var sm=new MatchStateMachine(); sm.transitionTo(MatchState.PREPARING); sm.transitionTo(MatchState.GATHERING); check(sm.state()==MatchState.GATHERING); reject(() -> sm.transitionTo(MatchState.WAITING));
  var t=new AtomicLong(); var c=new MatchClock(t::get); t.set(10); c.pause(); t.set(20); check(c.elapsed().toNanos()==10); c.resume(); t.set(30); check(c.elapsed().toNanos()==20); t.set(29); reject(c::elapsed);
  var s=new GameSession(rules()); var a=new UUID(0,1); var b=new UUID(0,2); s.join(a); s.join(b); s.transition(MatchState.PREPARING); s.transition(MatchState.GATHERING);
  for(int i=0;i<100;i++) { check(s.death(a,0)); check(!s.death(a,0)); check(s.respawn(a,20_000_000_000L)); }
  check(s.player(a).life().lives()==2); s.transition(MatchState.PVP); check(s.death(a,0)); check(s.deadline(a).orElseThrow()==20_000_000_000L); check(!s.respawn(a,19)); check(s.respawn(a,20_000_000_000L)); check(s.death(a,0)); check(s.player(a).life().status()==LifeState.Status.ELIMINATED); check(s.winner().orElseThrow().equals(b));
  check(s.death(b,0)); s.transition(MatchState.SUDDEN_DEATH); check(s.respawn(b,20_000_000_000L)); check(s.death(b,0)); check(s.player(b).life().status()==LifeState.Status.ELIMINATED);
  var e=new GameSession(rules()); e.join(a); e.transition(MatchState.PREPARING); e.transition(MatchState.GATHERING); e.death(a,0); e.transition(MatchState.ENDING); check(e.deadline(a).isEmpty()); check(!e.respawn(a,Long.MAX_VALUE));
  var arena=new Arena(0,0,1000,1000); check(arena.contains(-500,-500,0)); check(!arena.contains(500,0,0)); check(!arena.contains(-500,0,1)); check(arena.overlaps(new Arena(999,0,1000,1000),0)); check(!arena.overlaps(new Arena(1000,0,1000,1000),0)); check(arena.centersAtLeast(new Arena(1500,0,1000,1000),1500));
  reject(() -> new MatchRules(1,2,-1,2,1000,5000,80,1000,60,600,1,-1,300,3,80,18,2,2));
  var world="world"; var match=new UUID(1,1); var tr=new Tracker();
  var src=new Tracker.Player(a,"a",match,world,null,Tracker.Status.ACTIVE,0,0,0);
  var near=new Tracker.Player(b,"b",match,world,null,Tracker.Status.ACTIVE,3,4,0);
  var tie=new Tracker.Player(new UUID(0,3),"c",match,world,null,Tracker.Status.ACTIVE,-3,4,0);
  var spectator=new Tracker.Player(new UUID(0,4),"s",match,world,null,Tracker.Status.SPECTATING,0,0,0);
  var other=new Tracker.Player(new UUID(0,5),"o",match,"other",null,Tracker.Status.ACTIVE,0,0,0);
  check(tr.nearest(src,List.of(tie,spectator,other,near)).orElseThrow().id().equals(b)); check(tr.nearest(src,List.of(spectator,other)).isEmpty());
  var timeline=new Timeline(List.of(new Timeline.Event("supply",900,2,false),new Timeline.Event("select",900,1,true)));
  check(timeline.poll(900).getFirst().id().equals("select")); check(timeline.poll(900).getFirst().id().equals("supply")); check(timeline.poll(900).isEmpty());
  System.out.println(count + " checks passed");
 }
}
