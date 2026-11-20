package io.github.augmentwild.probe;

import io.github.augmentwild.paper.MatchDisplays;
import java.lang.reflect.Proxy;
import java.util.*;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pig;
import org.bukkit.scoreboard.Scoreboard;

/** Synthetic viewers on a real Paper scoreboard manager. No network clients. */
final class HudProbe {
    final UUID id = UUID.randomUUID();
    final Player player;
    Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
    BossBar visible;
    org.bukkit.inventory.Inventory menu;
    double hp = 10;
    int reads;

    HudProbe(Pig pig) {
        player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,method,args) -> switch (method.getName()) {
            case "getUniqueId" -> id;
            case "getGameMode" -> GameMode.SURVIVAL;
            case "getHealth" -> { reads++; yield hp; }
            case "getAbsorptionAmount" -> 4d;
            case "getLocation" -> pig.getLocation();
            case "getWorld" -> pig.getWorld();
            case "getAttribute" -> pig.getAttribute((org.bukkit.attribute.Attribute)args[0]);
            case "getPersistentDataContainer" -> pig.getPersistentDataContainer();
            case "getName" -> "뷰어";
            case "canSee" -> true;
            case "openInventory" -> { menu = (org.bukkit.inventory.Inventory)args[0]; yield null; }
            case "getScoreboard" -> board;
            case "setScoreboard" -> { board = (Scoreboard)args[0]; yield null; }
            case "showBossBar" -> { visible = (BossBar)args[0]; yield null; }
            case "hideBossBar" -> { if (visible == args[0]) visible = null; yield null; }
            case "hashCode" -> id.hashCode();
            case "equals" -> args[0] == proxy;
            case "toString" -> id.toString();
            default -> throw new AssertionError("Unexpected viewer operation " + method);
        });
    }
    private static void verifyControls(HudProbe viewer, Pig pig) {
        var p = viewer.player;
        io.github.augmentwild.paper.PlayerUi.open(p);
        if (!(viewer.menu.getHolder() instanceof io.github.augmentwild.paper.PaperMenu) || viewer.menu.getSize() < io.github.augmentwild.paper.PlayerUi.Option.values().length)
            throw new AssertionError("UI controls must use locked plugin inventory");
        for (var option : io.github.augmentwild.paper.PlayerUi.Option.values()) {
            if (!io.github.augmentwild.paper.PlayerUi.enabled(p,option)) throw new AssertionError(option);
            io.github.augmentwild.paper.PlayerUi.toggle(p,option);
        }
        for (int i=0;i<8;i++) if (!io.github.augmentwild.paper.PlayerUi.toggleFavorite(p,"id"+i)) throw new AssertionError("favorite");
        if (io.github.augmentwild.paper.PlayerUi.toggleFavorite(p,"ninth")) throw new AssertionError("favorite cap");
        try {
            byte[] saved = pig.getPersistentDataContainer().serializeToBytes();
            for (var option : io.github.augmentwild.paper.PlayerUi.Option.values())
                io.github.augmentwild.paper.PlayerUi.toggle(p,option);
            io.github.augmentwild.paper.PlayerUi.toggleFavorite(p,"id0");
            pig.getPersistentDataContainer().readFromBytes(saved);
            if (io.github.augmentwild.paper.PlayerUi.favorites(p).size()!=8) throw new AssertionError("lost favorite");
        } catch (java.io.IOException failure) { throw new AssertionError(failure); }
        for (var option : io.github.augmentwild.paper.PlayerUi.Option.values()) {
            if (io.github.augmentwild.paper.PlayerUi.enabled(p,option)) throw new AssertionError("lost " + option);
            io.github.augmentwild.paper.PlayerUi.toggle(p,option);
        }
        io.github.augmentwild.paper.PlayerUi.result(p,List.of("우승 · A","처치 3 · 사망 1"));
        if (viewer.menu.getItem(0)==null || viewer.menu.getItem(1)==null || viewer.menu.getItem(2)!=null)
            throw new AssertionError("result inventory");
    }

    static void run(Pig pig) {
        var test = new HudProbe(pig);
        verifyControls(test,pig);
        for (int size : List.of(2,4,8,16)) {
            var viewers = new ArrayList<HudProbe>();
            var online = new HashMap<UUID,Player>();
            var aimed = new HashMap<UUID,Player>();
            for (int i=0;i<size;i++) {
                var view = new HudProbe(pig);
                viewers.add(view);
                online.put(view.id,view.player);
            }
            for (int i=0;i<size;i++) aimed.put(viewers.get(i).id,viewers.get((i+1)%size).player);
            var scans = new java.util.concurrent.atomic.AtomicInteger();
            var displays = new MatchDisplays(p -> true,online::get,p -> { scans.incrementAndGet(); return aimed.get(p.getUniqueId()); });
            var main = Bukkit.getScoreboardManager().getMainScoreboard();
            try {
                for (int tick=0;tick<40;tick++) {
                    if (tick==30) { aimed.clear(); viewers.forEach(v -> v.hp=3); }
                    for (var view : viewers) {
                        displays.aim(view.player,tick,tick*50_000_000L);
                        if (tick%20==0) displays.sidebar(view.player,List.of("경기 시간 "+tick));
                        if ((tick < 35) != (view.visible != null)) throw new AssertionError("target retention tick "+tick);
                        if (view.board==main) throw new AssertionError("missing personal board");
                    }
                }
                if (scans.get()!=8*size || viewers.stream().anyMatch(v -> v.reads!=6)) throw new AssertionError("hidden health read or extra ray");
                var foreign = Bukkit.getScoreboardManager().getNewScoreboard();
                viewers.getFirst().board = foreign;
                displays.clear();
                if (viewers.getFirst().board!=foreign || viewers.stream().skip(1).anyMatch(v -> v.board!=main))
                    throw new AssertionError("board ownership");
                Bukkit.getLogger().info("AW_HUD_LOAD_PASS synthetic="+size+" ticks=40 scans="+scans);
            } finally { displays.clear(); }
        }
    }
}
