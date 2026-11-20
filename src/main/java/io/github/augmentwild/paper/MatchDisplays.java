package io.github.augmentwild.paper;

import io.github.augmentwild.domain.CombatView;
import java.util.*;
import java.util.function.Predicate;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

public final class MatchDisplays {
    private record Target(CombatView.Observation view, BossBar bar) {}
    private record Sidebar(Scoreboard prior, Scoreboard board, Objective objective, Map<Integer,String> lines) {}
    private final Map<UUID,Target> targets = new HashMap<>();
    private final Map<UUID,Sidebar> boards = new HashMap<>();
    private final Set<UUID> conflicts = new HashSet<>();
    private final Predicate<Player> active;
    private final java.util.function.Function<UUID,Player> online;
    private final java.util.function.Function<Player,Player> sight;

    MatchDisplays(Predicate<Player> active) { this(active,Bukkit::getPlayer,MatchDisplays::look); }

    public MatchDisplays(Predicate<Player> active, java.util.function.Function<UUID,Player> online,
                         java.util.function.Function<Player,Player> sight) {
        this.active = active;
        this.online = online;
        this.sight = sight;
    }
    private static Player look(Player viewer) {
        var eye = viewer.getEyeLocation();
        var hit = TargetSight.trace(eye,eye.getDirection(),entity -> entity instanceof Player p && !viewer.equals(p));
        return hit != null && hit.getHitEntity() instanceof Player p ? p : null;
    }

    public void aim(Player viewer, long tick, long now) {
        UUID id = viewer.getUniqueId();
        Target old = targets.get(id);
        if (!eligible(viewer) || !PlayerUi.enabled(viewer, PlayerUi.Option.HEALTH)) {
            hide(id);
            return;
        }
        if (old != null) {
            var prior = online.apply(old.view.target());
            if (prior == null || !eligible(prior) || !viewer.getWorld().equals(prior.getWorld())) {
                hide(id);
                old = null;
            }
        }
        if (CombatView.scan(tick)) {
            Player p = sight.apply(viewer);
            if (p != null && eligible(p) && viewer.canSee(p)
                    && CombatView.inRange(viewer.getLocation().distanceSquared(p.getLocation()))) {
                double hp = p.getHealth(), maximum = p.getAttribute(Attribute.MAX_HEALTH).getValue();
                var view = new CombatView.Observation(p.getUniqueId(),p.getName(),hp,maximum,p.getAbsorptionAmount(),now);
                var title = Component.text(view.title());
                float progress = CombatView.ratio(hp, maximum);
                var bar = old == null ? BossBar.bossBar(title, progress, BossBar.Color.RED, BossBar.Overlay.PROGRESS) : old.bar;
                if (!title.equals(bar.name())) bar.name(title);
                if (progress != bar.progress()) bar.progress(progress);
                if (old == null) viewer.showBossBar(bar);
                targets.put(id, new Target(view,bar));
                return;
            }
        }
        if (old != null && !old.view.visible(now)) hide(id);
    }
    private boolean eligible(Player p) { return active.test(p) && p.getGameMode() != GameMode.SPECTATOR; }
    private void hide(UUID id) {
        var target = targets.remove(id);
        Player viewer = online.apply(id);
        if (viewer != null && target != null) viewer.hideBossBar(target.bar);
    }
    void detach(UUID id) {
        for (var entry : List.copyOf(targets.entrySet())) if (entry.getValue().view.target().equals(id)) hide(entry.getKey());
        hide(id);
        removeBoard(id);
        conflicts.remove(id);
    }
    public void sidebar(Player player, List<String> lines) {
        UUID id = player.getUniqueId();
        if (!PlayerUi.enabled(player, PlayerUi.Option.SIDEBAR)) { removeBoard(id); return; }
        if (conflicts.contains(id)) return;
        var side = boards.get(id);
        var current = player.getScoreboard();
        var main = Bukkit.getScoreboardManager().getMainScoreboard();
        if (side != null && !ownsBoard(side) || side != null && side.board != current || !main.getTeams().isEmpty() || !main.getObjectives().isEmpty()
                || side == null && current != main) {
            removeBoard(id);
            conflicts.add(id);
            Bukkit.getLogger().info("AugmentWild: 기존 Scoreboard를 보존하기 위해 개인 사이드바를 중지합니다.");
            return;
        }
        if (side == null) {
            var board = Bukkit.getScoreboardManager().getNewScoreboard();
            var objective = board.registerNewObjective("aw_match", Criteria.DUMMY, Component.text("AUGMENTWILD"));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            side = new Sidebar(current, board, objective, new HashMap<>());
            boards.put(id, side);
            player.setScoreboard(board);
        }
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i), previous = side.lines.put(i,line);
            if (line.equals(previous)) continue;
            if (previous != null) side.board.resetScores(previous);
            side.objective.getScore(line).setScore(lines.size()-i);
        }
    }
    private boolean ownsBoard(Sidebar side) {
        return side.board.getObjective(DisplaySlot.SIDEBAR) != null && side.board.getObjective(DisplaySlot.SIDEBAR).getName().equals("aw_match") && side.board.getObjectives().size() == 1
                && side.board.getTeams().isEmpty();
    }
    private void removeBoard(UUID id) {
        var board = boards.remove(id);
        var p = online.apply(id);
        if (board == null) return;
        if (p != null && p.getScoreboard() == board.board && ownsBoard(board)) p.setScoreboard(board.prior);

    }
    public void clear() {
        List.copyOf(targets.keySet()).forEach(this::hide);
        List.copyOf(boards.keySet()).forEach(this::removeBoard);
        conflicts.clear();
    }
}
