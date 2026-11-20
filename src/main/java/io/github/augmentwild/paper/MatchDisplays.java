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

final class MatchDisplays {
    private record Target(UUID id, BossBar bar, long seen) {}
    private record Sidebar(Scoreboard prior, Scoreboard board, Objective objective, Map<Integer,String> lines) {}
    private final Map<UUID,Target> targets = new HashMap<>();
    private final Map<UUID,Sidebar> boards = new HashMap<>();
    private final Set<UUID> conflicts = new HashSet<>();
    private final Predicate<Player> active;
    MatchDisplays(Predicate<Player> active) { this.active = active; }

    void aim(Player viewer, long tick, long now) {
        UUID id = viewer.getUniqueId();
        Target old = targets.get(id);
        if (!eligible(viewer) || !PlayerUi.enabled(viewer, PlayerUi.Option.HEALTH)) {
            hide(id);
            return;
        }
        if (old != null) {
            var prior = Bukkit.getPlayer(old.id);
            if (prior == null || !eligible(prior) || !viewer.getWorld().equals(prior.getWorld())) {
                hide(id);
                old = null;
            }
        }
        if (tick % 5 == 0) {
            var eye = viewer.getEyeLocation();
            var hit = viewer.getWorld().rayTrace(eye, eye.getDirection(), 24, FluidCollisionMode.NEVER, false, 0,
                    entity -> entity instanceof Player p && !viewer.equals(p));
            if (hit != null && hit.getHitEntity() instanceof Player p && eligible(p) && viewer.canSee(p)
                    && viewer.getLocation().distanceSquared(p.getLocation()) <= 576) {
                double hp = p.getHealth(), maximum = p.getAttribute(Attribute.MAX_HEALTH).getValue();
                var title = Component.text(String.format(Locale.ROOT,"%s · %.1f / %.1f HP · 흡수 +%.1f",p.getName(),hp,maximum,p.getAbsorptionAmount()));
                float progress = CombatView.ratio(hp, maximum);
                var bar = old == null ? BossBar.bossBar(title, progress, BossBar.Color.RED, BossBar.Overlay.PROGRESS) : old.bar;
                if (!title.equals(bar.name())) bar.name(title);
                if (progress != bar.progress()) bar.progress(progress);
                if (old == null) viewer.showBossBar(bar);
                targets.put(id, new Target(p.getUniqueId(),bar,now));
                return;
            }
        }
        if (old != null && now - old.seen >= 500_000_000L) hide(id);
    }
    private boolean eligible(Player p) { return active.test(p) && p.getGameMode() != GameMode.SPECTATOR; }
    private void hide(UUID id) {
        var target = targets.remove(id);
        Player viewer = Bukkit.getPlayer(id);
        if (viewer != null && target != null) viewer.hideBossBar(target.bar);
    }
    void detach(UUID id) {
        for (var entry : List.copyOf(targets.entrySet())) if (entry.getValue().id.equals(id)) hide(entry.getKey());
        hide(id);
        removeBoard(id);
        conflicts.remove(id);
    }
    void sidebar(Player player, List<String> lines) {
        UUID id = player.getUniqueId();
        if (!PlayerUi.enabled(player, PlayerUi.Option.SIDEBAR)) { removeBoard(id); return; }
        if (conflicts.contains(id)) return;
        var side = boards.get(id);
        var current = player.getScoreboard();
        var main = Bukkit.getScoreboardManager().getMainScoreboard();
        if (side != null && side.board != current || !main.getTeams().isEmpty() || !main.getObjectives().isEmpty()
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
    private void removeBoard(UUID id) {
        var board = boards.remove(id);
        var p = Bukkit.getPlayer(id);
        if (board != null && p != null && p.getScoreboard() == board.board) p.setScoreboard(board.prior);
    }
    void clear() {
        List.copyOf(targets.keySet()).forEach(this::hide);
        List.copyOf(boards.keySet()).forEach(this::removeBoard);
        conflicts.clear();
    }
}
