import io.github.augmentwild.domain.*;
import io.github.augmentwild.match.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class MatchSimulation {
 static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
 static MatchRules rules() { return new MatchRules(1800,1820,20,2,1000,5000,80,1000,60,600,1,-1,300,3,80,18,2,2); }
 static GameSession create(UUID... ids) { var s=new GameSession(rules()); for(var id:ids)s.join(id); s.transition(MatchState.PREPARING); s.transition(MatchState.GATHERING); return s; }
 static void advance(AtomicLong wall,long seconds) { wall.addAndGet(seconds*1_000_000_000L); }
 public static void main(String[] args) {
  UUID a=new UUID(0,1),b=new UUID(0,2),c=new UUID(0,3),d=new UUID(0,4);
  var s=create(a,b,c,d); var wall=new AtomicLong(); var scheduler=new MatchSimulationClock(s,rules(),wall::get,15,900,1500);
  s.death(a,0); require(!s.respawn(a,19_000_000_000L),"early respawn"); require(s.respawn(a,20_000_000_000L),"respawn");
  for(int round=1; round<=6; round++) {
   long target=round*300L; advance(wall,target-scheduler.elapsedSeconds()); scheduler.tick();
   require(s.state()==MatchState.AUGMENT_SELECTION,"selection " + round);
   long paused=scheduler.elapsedSeconds(); advance(wall,15); require(scheduler.elapsedSeconds()==paused,"paused clock"); scheduler.tick(); scheduler.tick();
  }
  require(scheduler.events().stream().filter(x->x.startsWith("selection-")).count()==6,"six rounds");
  var events=scheduler.events(); require(events.indexOf("selection-3")<events.indexOf("normal-supply"),"normal order"); require(events.indexOf("selection-5")<events.indexOf("premium-supply"),"premium order");
  advance(wall,20); scheduler.tick(); require(s.state()==MatchState.PVP,"pvp");
  var border=new Arena(0,0,1000,1000); require(border.shrink(2120,1820,600,60,60).width()==530,"border midpoint"); require(border.shrink(2420,1820,600,60,60).width()==60,"border final");
  for (var id:List.of(a,b,c,d)) { s.death(id,0); s.respawn(id,20_000_000_000L); }
  var first=s.resolveTick(List.of(a,b),21_000_000_000L); require(first.outcome()==GameSession.Outcome.IN_PROGRESS,"two remain");
  var result=s.resolveTick(List.of(c,d),21_000_000_000L); require(result.outcome()==GameSession.Outcome.DRAW,"simultaneous draw");
  require(s.resolveTick(List.of(a),22_000_000_000L).equals(result),"ending immutable");
  var win=create(a,b); win.transition(MatchState.PVP); win.death(b,0); win.respawn(b,20_000_000_000L); require(win.resolveTick(List.of(b),21_000_000_000L).winner().equals(a),"winner");
  var delayed=create(a,b); var w=new AtomicLong(); var sc=new MatchSimulationClock(delayed,rules(),w::get,15,900,1500);
  advance(w,330); sc.tick(); require(sc.elapsedSeconds()==300,"late tick freezes at selection");
  sc.pause(); advance(w,15); sc.tick(); require(sc.isPaused() && sc.elapsedSeconds()==300,"manual pause survives selection");
  advance(w,30); sc.tick(); require(sc.elapsedSeconds()==300,"manual freeze"); sc.resume(); advance(w,10); sc.tick(); require(sc.elapsedSeconds()==310,"manual resume");
  require(sc.events().stream().filter(x->x.equals("selection-1")).count()==1,"late event once");
  var inv=create(a,b); var match=new UUID(7,7);
  var source=new Tracker.Player(a,"a",match,"w",null,Tracker.Status.ACTIVE,0,0,0);
  var target=new Tracker.Player(b,"b",match,"w",null,Tracker.Status.ACTIVE,1,0,0);
  require(inv.locate(a,List.of(source,target)).isPresent(),"session locate"); inv.death(b,0);
  require(inv.locate(a,List.of(source,target)).isEmpty(),"respawning excluded");
  System.out.println("4 simulation scenarios passed");
 }
}
