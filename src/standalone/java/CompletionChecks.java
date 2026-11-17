import io.github.augmentwild.domain.*;
import io.github.augmentwild.match.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class CompletionChecks {
 static void check(boolean b,String message) { if (!b) throw new AssertionError(message); }
 static MatchRules rules() { return new MatchRules(1800,1820,20,2,1000,5000,80,1000,60,600,1,-1,300,3,80,18,2,2); }
 static GameSession game(UUID... ids) { var s=new GameSession(rules()); for(var id:ids)s.join(id); s.transition(MatchState.PREPARING); s.transition(MatchState.GATHERING); return s; }
 public static void main(String[] args) {
  var a=new UUID(0,1); var b=new UUID(0,2); var c=new UUID(0,3); var d=new UUID(0,4);
  var session=game(a,b,c,d); var now=new AtomicLong(); var clock=new MatchSimulationClock(session,rules(),now::get,15,900,1500);
  clock.setOfferProvider((m,r,players)-> { var offers=new HashMap<UUID,List<SelectionRound.Offer>>(); for(var p:players) offers.put(p,List.of(new SelectionRound.Offer("prism",true,true))); return offers; });
  now.set(290_000_000_000L); clock.tick(); check(clock.death(a),"gathering death");
  now.set(300_000_000_000L); clock.tick(); check(clock.choose(b,1,"prism"),"manual b"); check(clock.choose(c,1,"prism"),"manual c");
  now.set(315_000_000_000L); clock.tick(); check(clock.completedSelections().getFirst().get(d).status()==SelectionRound.Status.AUTOMATIC,"auto selection");
  check(clock.respawnDue().isEmpty(),"paused respawn"); now.set(325_000_000_000L); check(clock.respawnDue().equals(List.of(a)),"resume respawn");
  var stale=game(a); stale.death(a,0); var reservation=stale.reservation(a).orElseThrow(); stale.cancel(); var newer=game(a); newer.death(a,0); check(!newer.respawn(reservation,99_000_000_000L),"stale reservation");
  var combat=game(a,b,c); combat.transition(MatchState.PVP); combat.death(b,0); combat.respawn(b,20_000_000_000L);
  var result=combat.resolveTick(List.of(c),List.of(b),21_000_000_000L); check(result.outcome()==GameSession.Outcome.WINNER && a.equals(result.winner()),"leave and death");
  var sameTick=game(a,b,c); sameTick.transition(MatchState.PVP); sameTick.death(b,0); sameTick.resolveCombatTick(List.of(),List.of(b),20_000_000_000L);
  check(sameTick.player(b).life().status()==LifeState.Status.ELIMINATED,"respawn before death");
  var disconnected=game(a,b); disconnected.transition(MatchState.PVP); disconnected.connected(b,false);
  check(disconnected.resolveTick(List.of(),0).outcome()==GameSession.Outcome.IN_PROGRESS,"disconnected remains eligible");
  System.out.println("5 completion scenarios passed");
 }
}
