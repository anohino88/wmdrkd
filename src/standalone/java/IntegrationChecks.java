import io.github.augmentwild.domain.*;
import io.github.augmentwild.match.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class IntegrationChecks {
 static void check(boolean value,String name) { if (!value) throw new AssertionError(name); }
 static MatchRules rules() { return new MatchRules(1800,1820,20,2,1000,5000,80,1000,60,600,1,-1,300,3,80,18,2,2); }
 static GameSession game(UUID p) { var s=new GameSession(rules()); s.join(p); s.transition(MatchState.PREPARING); s.transition(MatchState.GATHERING); return s; }
 public static void main(String[] args) {
  UUID p=new UUID(0,1), q=new UUID(0,2); var s=game(p); s.death(p,0); var old=s.reservation(p).orElseThrow();
  check(!s.respawn(old,19_000_000_000L),"not due"); check(s.respawn(old,20_000_000_000L),"due"); check(!s.respawn(old,20_000_000_000L),"only once");
  s.death(p,21_000_000_000L); check(!s.respawn(old,99_000_000_000L),"stale death");
  var fresh=game(p); fresh.death(p,0); check(!fresh.respawn(old,99_000_000_000L),"stale match");
  var offer=new SelectionRound.Offer("prism",true,true); var notImplemented=new SelectionRound.Offer("unbuilt",true,false);
  var round=new SelectionRound(s.matchId(),6,15,true,Map.of(p,List.of(offer,notImplemented),q,List.of(offer)));
  check(!round.select(s.matchId(),5,p,"prism",0),"stale round"); check(!round.select(s.matchId(),6,p,"unbuilt",0),"unimplemented");
  check(round.select(s.matchId(),6,p,"prism",0),"manual"); check(!round.select(s.matchId(),6,p,"prism",0),"duplicate");
  round.expire(15); check(round.complete(),"timeout"); check(round.choices().get(q).status()==SelectionRound.Status.AUTOMATIC,"automatic");
  var ledger=new TrackingRequests(); var count=new AtomicInteger(); var request=UUID.randomUUID();
  var target=new Tracker.Result(q,"q",1,2,3,4,"N");
  var result=ledger.execute(request,p,true,true,Optional.of(target),1,(id,n)->{count.incrementAndGet(); return true;});
  check(result.status()==TrackingRequests.Status.SUCCESS,"tracking success");
  check(ledger.execute(request,p,true,true,Optional.of(target),1,(id,n)->{count.incrementAndGet(); return true;}).status()==TrackingRequests.Status.ALREADY_PROCESSED,"duplicate request");
  check(count.get()==1,"one debit");
  var g=game(p); var wall=new AtomicLong(); var clock=new MatchSimulationClock(g,rules(),wall::get,15,900,1500);
  wall.set(290_000_000_000L); clock.tick(); check(clock.death(p),"clock death");
  wall.set(300_000_000_000L); clock.tick(); check(clock.respawnDue().isEmpty(),"paused respawn");
  wall.set(315_000_000_000L); clock.tick(); check(clock.respawnDue().isEmpty(),"after selection");
  wall.set(325_000_000_000L); check(clock.respawnDue().equals(List.of(p)),"match time deadline");
  var reconnect = game(p);
  check(reconnect.death(p, 0), "one death accepted");
  var pending = reconnect.reservation(p).orElseThrow();
  check(!reconnect.death(p, 0) && reconnect.reservation(p).orElseThrow().equals(pending), "duplicate keeps reservation");
  reconnect.connected(p, false);
  check(reconnect.respawnDue(20_000_000_000L, id -> true).isEmpty(), "offline remains pending");
  reconnect.connected(p, true);
  check(reconnect.respawnDue(20_000_000_000L, id -> false).isEmpty(), "native respawn remains pending");
  check(reconnect.respawnDue(20_000_000_000L, id -> true).equals(List.of(p)), "both gates clear");
  check(reconnect.respawnDue(20_000_000_000L).isEmpty(), "completion only once");
  reconnect.death(p, 21_000_000_000L);
  reconnect.cancel();
  check(reconnect.reservation(p).isEmpty(), "stop clears pending reservation");
  System.out.println("5 integration scenarios passed");
 }
}
