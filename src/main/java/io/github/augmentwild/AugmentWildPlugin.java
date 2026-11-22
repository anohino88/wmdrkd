package io.github.augmentwild;

import io.github.augmentwild.paper.ArenaSearchService;
import io.github.augmentwild.paper.PaperMatchService;
import io.github.augmentwild.paper.PaperMatchSettings;
import io.github.augmentwild.paper.PaperQuickMenu;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public final class AugmentWildPlugin extends JavaPlugin {
    private PaperMatchService matches;
    private ArenaSearchService arenaSearch;
    private PaperQuickMenu quickMenu;

    @Override public void onEnable() {
        if (matches != null) return;
        saveDefaultConfig();
        arenaSearch = new ArenaSearchService(this);
        matches = new PaperMatchService(this, PaperMatchSettings.load(getConfig()), arenaSearch);
        quickMenu = new PaperQuickMenu(this, matches);
        var aw = Objects.requireNonNull(getCommand("aw"), "Missing aw command in plugin.yml");
        aw.setExecutor(this);
        aw.setTabCompleter(new AwCompletion(matches));
        getServer().getPluginManager().registerEvents(arenaSearch, this);
        getServer().getPluginManager().registerEvents(matches, this);
        getServer().getPluginManager().registerEvents(matches.restrictions(), this);
        getServer().getPluginManager().registerEvents(matches.augmentListener(), this);
        getServer().getPluginManager().registerEvents(matches.officialAugmentListener(), this);
        getServer().getPluginManager().registerEvents(matches.intelListener(), this);
        getServer().getPluginManager().registerEvents(matches.supplyListener(), this);
        getServer().getPluginManager().registerEvents(matches.feedbackListener(), this);
        getServer().getPluginManager().registerEvents(matches.uiListener(), this);
        getServer().getPluginManager().registerEvents(quickMenu, this);
        getServer().getPluginManager().registerEvents(matches.galleryListener(), this);
        getServer().getPluginManager().registerEvents(matches.artListener(), this);
        matches.recoverSupply();
        matches.recoverOnlinePlayers();
    }

    @Override public void onDisable() {
        if (matches == null) return;
        HandlerList.unregisterAll(matches);
        HandlerList.unregisterAll(matches.restrictions());
        HandlerList.unregisterAll(matches.augmentListener());
        HandlerList.unregisterAll(matches.officialAugmentListener());
        HandlerList.unregisterAll(matches.intelListener());
        HandlerList.unregisterAll(matches.supplyListener());
        HandlerList.unregisterAll(arenaSearch);
        HandlerList.unregisterAll(quickMenu);
        HandlerList.unregisterAll(matches.artListener());
        arenaSearch.cancel();
        matches.shutdown();
        matches = null;
        quickMenu = null;
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (matches == null) {
            sender.sendMessage("경기 서비스를 사용할 수 없습니다.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(sender instanceof Player player ? quickMenu.open(player) : "플레이어 전용 메뉴입니다.");
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("test")) {
            sender.sendMessage(matches.labCommand(sender, java.util.Arrays.copyOfRange(args, 2, args.length)));
            return true;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("arena")
                && args[2].equalsIgnoreCase("preview")) {
            sender.sendMessage(sender.hasPermission("augmentwild.admin")
                    ? arenaSearch.preview(sender, matches.previewPlayerCount())
                    : "관리자 권한이 없습니다.");
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            if (!sender.hasPermission("augmentwild.admin")) {
                sender.sendMessage("관리자 권한이 없습니다.");
                return true;
            }
            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "start" -> sender.sendMessage(matches.start(sender));
                case "stop" -> {
                    arenaSearch.cancel();
                    sender.sendMessage(matches.stop());
                }
                default -> { return false; }
            }
            return true;
        }
        if (!sender.hasPermission("augmentwild.play")) {
            sender.sendMessage("권한이 없습니다.");
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("skill")) {
            sender.sendMessage(sender instanceof Player player ? matches.skill(player, args) : "플레이어 전용 명령입니다.");
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("cast")) {
            sender.sendMessage(sender instanceof Player player
                    ? matches.castAugment(player, args[1].toLowerCase(Locale.ROOT)) : "플레이어 전용 명령입니다.");
            return true;
        }
        if (args.length != 1) return false;
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "menu" -> sender.sendMessage(sender instanceof Player p ? quickMenu.open(p) : "플레이어 전용 메뉴입니다.");
            case "combatlog" -> sender.sendMessage(sender instanceof Player p ? matches.combatLog(p) : "플레이어 전용 명령입니다.");
            case "ui" -> sender.sendMessage(sender instanceof Player p ? matches.ui(p) : "플레이어 전용 명령입니다.");
            case "supply" -> sender.sendMessage(sender instanceof Player p ? matches.guide(p) : "플레이어 전용 명령입니다.");
            case "result" -> sender.sendMessage(sender instanceof Player p ? matches.result(p) : "플레이어 전용 명령입니다.");
            case "augments" -> sender.sendMessage(sender instanceof Player player ? matches.openCodex(player) : "플레이어 전용 명령입니다.");
            case "augment" -> sender.sendMessage(sender instanceof Player player ? matches.openAugments(player) : "플레이어 전용 명령입니다.");
            case "locate", "track" -> sender.sendMessage(sender instanceof Player player && sender.hasPermission("augmentwild.track")
                    ? matches.locate(player) : "추적 권한이 없습니다.");
            case "scout" -> sender.sendMessage(sender instanceof Player player ? matches.scout(player) : "플레이어 전용 명령입니다.");
            case "death" -> sender.sendMessage(sender instanceof Player player ? matches.deathReport(player) : "플레이어 전용 명령입니다.");
            case "status" -> sender.sendMessage(matches.status(sender));
            case "join" -> sender.sendMessage(sender instanceof Player player
                    ? matches.join(player) : "플레이어만 참가할 수 있습니다.");
            case "leave" -> sender.sendMessage(sender instanceof Player player
                    ? matches.leave(player) : "플레이어만 탈퇴할 수 있습니다.");
            default -> { return false; }
        }
        return true;
    }
}
