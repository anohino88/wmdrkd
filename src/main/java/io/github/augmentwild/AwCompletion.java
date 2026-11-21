package io.github.augmentwild;

import io.github.augmentwild.augment.AugmentCombinations;
import io.github.augmentwild.paper.PaperMatchService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** Permission-aware, source-of-truth tab suggestions for the single /aw command. */
final class AwCompletion implements TabCompleter {
    private static final List<String> PLAYER = List.of(
            "menu", "join", "leave", "status", "augment", "augments", "ui", "combatlog",
            "supply", "result", "scout", "death", "skill", "cast");
    private static final List<String> SKILL = List.of("bind", "select", "next", "unbind", "use", "status");
    private static final List<String> LAB = List.of(
            "status", "start", "solo", "presets", "preset", "grant", "remove",
            "list", "clear", "report", "reset", "progress", "health");
    private final PaperMatchService matches;

    AwCompletion(PaperMatchService matches) { this.matches = matches; }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) return List.of();
        boolean play = sender.hasPermission("augmentwild.play");
        boolean admin = sender.hasPermission("augmentwild.admin");
        boolean lab = admin && sender.hasPermission("augmentwild.admin.test");
        Player player = sender instanceof Player p ? p : null;
        String first = args[0].toLowerCase(Locale.ROOT);
        List<String> choices = List.of();

        if (args.length == 1) {
            var roots = new ArrayList<String>();
            if (play) {
                roots.addAll(PLAYER);
                if (sender.hasPermission("augmentwild.track")) roots.addAll(List.of("locate", "track"));
            }
            if (admin) roots.add("admin");
            choices = roots;
        } else if (first.equals("skill") && play) {
            if (args.length == 2) choices = SKILL;
            else if (args.length == 3 && args[1].equalsIgnoreCase("bind")) {
                var opts = new ArrayList<>(List.of("1", "2", "3"));
                if (player != null) opts.addAll(matches.availableSkills(player));
                choices = opts;
            } else if (args.length == 3 && args[1].equalsIgnoreCase("select")) {
                choices = List.of("1", "2", "3");
            } else if (args.length == 4 && args[1].equalsIgnoreCase("bind")
                    && List.of("1", "2", "3").contains(args[2]) && player != null) {
                choices = matches.availableSkills(player);
            }
        } else if (first.equals("cast") && play && args.length == 2 && player != null) {
            choices = matches.availableSkills(player);
        } else if (first.equals("admin") && admin) {
            if (args.length == 2) {
                var options = new ArrayList<>(List.of("start", "stop", "arena"));
                if (lab) options.add("test");
                choices = options;
            } else if (args[1].equalsIgnoreCase("arena") && args.length == 3) {
                choices = List.of("preview");
            } else if (args[1].equalsIgnoreCase("test") && lab) {
                if (args.length == 3) choices = LAB;
                else if (args.length == 4 && List.of("preset", "grant", "remove", "list",
                        "clear", "report", "reset", "progress", "health").contains(args[2].toLowerCase(Locale.ROOT))) {
                    choices = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                } else if (args.length == 5) {
                    String action = args[2].toLowerCase(Locale.ROOT);
                    if (action.equals("preset")) choices = AugmentCombinations.PRESETS.keySet().stream().toList();
                    else if (action.equals("grant") || action.equals("progress")) choices = matches.registeredAugments();
                    else if (action.equals("remove")) {
                        Player target = Bukkit.getPlayerExact(args[3]);
                        if (target != null) choices = matches.ownedAugments(target);
                    } else if (action.equals("health")) choices = List.of("2", "10", "20");
                } else if (args.length == 6 && args[2].equalsIgnoreCase("progress")) {
                    choices = List.of("0", "1", "5", "10", "20", "50");
                }
            }
        }
        String partial = args[args.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(partial))
                .distinct().sorted(Comparator.naturalOrder()).toList();
    }
}
