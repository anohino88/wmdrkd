package io.github.augmentwild;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AwCompletionTest {
    private static CommandSender sender(String... permissions) {
        Set<String> allowed = Set.of(permissions);
        return (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class}, (proxy, method, args) -> {
                    if (method.getName().equals("hasPermission")) return allowed.contains(args[0].toString());
                    if (method.getReturnType() == boolean.class) return false;
                    return null;
                });
    }

    @Test void normalPlayerGetsOnlyAllowedOptions() {
        var completer = new AwCompletion(null);
        var player = sender("augmentwild.play");
        assertEquals(List.of("menu"), completer.onTabComplete(player, null, "aw", new String[]{"me"}));
        assertFalse(completer.onTabComplete(player, null, "aw", new String[]{""}).contains("admin"));
        assertFalse(completer.onTabComplete(player, null, "aw", new String[]{"loc"}).contains("locate"));
        assertEquals(List.of("bind"), completer.onTabComplete(player, null, "aw", new String[]{"skill", "b"}));
        assertEquals(List.of("1", "2", "3"), completer.onTabComplete(player, null, "aw", new String[]{"skill", "select", ""}));
    }

    @Test void trackerAndAdminOptionsRequireMatchingPermissions() {
        var completer = new AwCompletion(null);
        var tracker = sender("augmentwild.play", "augmentwild.track");
        assertEquals(List.of("locate"), completer.onTabComplete(tracker, null, "aw", new String[]{"loc"}));
        var op = sender("augmentwild.admin", "augmentwild.admin.test");
        assertEquals(List.of("admin"), completer.onTabComplete(op, null, "aw", new String[]{"ad"}));
        assertEquals(List.of("solo"), completer.onTabComplete(op, null, "aw", new String[]{"admin", "test", "sol"}));
        assertEquals(List.of("preview"), completer.onTabComplete(op, null, "aw", new String[]{"admin", "arena", "pre"}));
        assertFalse(completer.onTabComplete(sender("augmentwild.play"), null, "aw", new String[]{"admin", "test", ""}).contains("solo"));
    }
}
