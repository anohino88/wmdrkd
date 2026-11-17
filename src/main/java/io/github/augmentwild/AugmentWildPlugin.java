package io.github.augmentwild;

import io.github.augmentwild.match.MatchStateMachine;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class AugmentWildPlugin extends JavaPlugin {
    private final MatchStateMachine match = new MatchStateMachine();

    @Override public void onEnable() {
        saveDefaultConfig();
        getLogger().warning("게임 기능은 아직 구현되지 않았습니다. 경기를 시작할 수 없습니다.");
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
            if (!sender.hasPermission("augmentwild.play")) {
                sender.sendMessage("권한이 없습니다.");
                return true;
            }
            sender.sendMessage("증강 야생 상태: " + match.state() + " (개발 중)");
        } else {
            sender.sendMessage("사용법: /aw status — 게임 기능은 개발 중입니다.");
        }
        return true;
    }
}
