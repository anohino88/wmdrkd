import io.github.augmentwild.domain.*;
import io.github.augmentwild.match.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Each named scenario executes in a separate JVM. */
public class ScenarioChecks {
 static void check(boolean b,String message) { if (!b) throw new AssertionError(message); }
 static final UUID A=new UUID(0,1), B=new UUID(0,2), C=new UUID(0,3), D=new UUID(0,4);
 static MatchRules rules() { return new MatchRules(1800,1820,20,2,1000,5000,80,1000,60,600,1,-1,300,3,80,18,2,2); }
 static GameSession game(UUID... ids) { var s=new GameSession(rules()); for(var id:ids)s.join(id); s.transition(MatchState.PREPARING); s.transition(MatchState.GATHERING); return s; }
 static MatchSimulationClock clock(GameSession s, AtomicLong now) { return new MatchSimulationClock(s,rules(),now::get,15,900,1500); }
 static void sec(AtomicLong now,long seconds) { now.addAndGet(seconds*1_000_000_000L); }
 static void full(boolean sudden) {
  var s=game(A,B,C,D); var now=new AtomicLong(); var clock=clock(s,now);
  for(int round=1;round<=6;round++) { sec(now,round*300L-clock.elapsedSeconds()); clock.tick(); check(s.state()==MatchState.AUGMENT_SELECTION,"selection"); sec(now,15); clock.tick(); clock.tick(); }
  check(clock.events().size()==8,"six selections and two supplies"); sec(now,20); clock.tick(); check(s.state()==MatchState.PVP,"pvp");
  var arena=new Arena(0,0,1000,1000); sec(now,300); clock.tick(); check(arena.shrink(clock.elapsedSeconds(),1820,600,60,60).width()==530,"border midpoint");
  if(sudden) { sec(now,300); clock.tick(); check(s.state()==MatchState.SUDDEN_DEATH,"sudden death"); }
  else { for(var p:List.of(B,C,D)) { check(s.death(p,clock.elapsedNanos()),"first death"); check(s.respawn(p,clock.elapsedNanos()+20_000_000_000L),"respawn"); } }
  var result=s.resolveTick(List.of(B,C,D),clock.elapsedNanos()); check(result.outcome()==GameSession.Outcome.WINNER && A.equals(result.winner()),"winner");
 }
 public static void main(String[] args) {
  switch(args[0]) {
   case "A" -> full(false);
   case "B" -> { var s=game(A,B); s.transition(MatchState.PVP); for(var p:List.of(A,B)){s.death(p,0);s.respawn(p,20_000_000_000L);} check(s.resolveTick(List.of(B,A),21_000_000_000L).outcome()==GameSession.Outcome.DRAW,"draw"); }
   case "C" -> { var s=game(A,B); var n=new AtomicLong(); var c=clock(s,n); sec(n,330); c.tick(); check(c.elapsedSeconds()==300,"late tick"); sec(n,15); c.tick(); sec(n,1); c.tick(); check(c.elapsedSeconds()==301,"resume"); check(c.events().size()==1,"once"); }
   case "D" -> { var s=game(A,B); var n=new AtomicLong(); var c=clock(s,n); sec(n,290); c.tick(); c.death(A); sec(n,10); c.tick();
    check(c.death(B), "death during selection uses gathering rules");
    check(!c.death(B), "duplicate selection death is ignored");
    check(s.player(B).life().lives() == 2, "selection death preserves combat lives");
    sec(n,15); c.tick();
    check(c.respawnDue().isEmpty(), "paused");
    sec(n,10); check(c.respawnDue().equals(List.of(A)), "first due");
    sec(n,10); check(c.respawnDue().equals(List.of(B)), "selection death due"); }
   case "E" -> { var s=game(A); s.death(A,0); var old=s.reservation(A).orElseThrow(); s.cancel(); var next=game(A); next.death(A,0); check(!next.respawn(old,99_000_000_000L),"stale"); }
   case "F" -> { var s=game(A,B,C); var n=new AtomicLong(); var c=clock(s,n); sec(n,300); c.tick(); c.pause(); sec(n,15); c.tick(); check(c.isPaused(),"manual pause"); c.resume(); s.transition(MatchState.PVP); s.death(B,0); s.respawn(B,20_000_000_000L); check(s.resolveTick(List.of(C),List.of(B),21_000_000_000L).winner().equals(A),"leave"); }
   case "G" -> full(true);
   default -> throw new IllegalArgumentException();
  }
  System.out.println("Scenario "+args[0]+" passed");
 }
}
