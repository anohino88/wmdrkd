package io.github.augmentwild;

import io.github.augmentwild.paper.PaperMatchService;
import io.github.augmentwild.paper.PaperMatchSettings;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public final class AugmentWildPlugin extends JavaPlugin {
    private PaperMatchService matches;

    @Override public void onEnable() {
        if (matches != null) return;
        saveDefaultConfig();
        matches = new PaperMatchService(this, PaperMatchSettings.load(getConfig()));
        Objects.requireNonNull(getCommand("aw"), "Missing aw command in plugin.yml").setExecutor(this);
        getServer().getPluginManager().registerEvents(matches, this);
        getServer().getPluginManager().registerEvents(matches.restrictions(), this);
    }

    @Override public void onDisable() {
        if (matches == null) return;
        HandlerList.unregisterAll(matches);
        HandlerList.unregisterAll(matches.restrictions());
        matches.shutdown();
        matches = null;
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (matches == null) {
            sender.sendMessage("경기 서비스를 사용할 수 없습니다.");
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            if (!sender.hasPermission("augmentwild.admin")) {
                sender.sendMessage("관리자 권한이 없습니다.");
                return true;
            }
            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "start" -> sender.sendMessage(matches.start());
                case "stop" -> sender.sendMessage(matches.stop());
                default -> { return false; }
            }
            return true;
        }
        if (!sender.hasPermission("augmentwild.play")) {
            sender.sendMessage("권한이 없습니다.");
            return true;
        }
        if (args.length != 1) return false;
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> sender.sendMessage(matches.status());
            case "join" -> sender.sendMessage(sender instanceof Player player
                    ? matches.join(player) : "플레이어만 참가할 수 있습니다.");
            case "leave" -> sender.sendMessage(sender instanceof Player player
                    ? matches.leave(player) : "플레이어만 탈퇴할 수 있습니다.");
            default -> { return false; }
        }
        return true;
    }
}
