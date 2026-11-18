package io.github.augmentwild.paper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.LivingEntity;

final class PaperAugmentBurns {
    record Burn(LivingEntity target, int stacks, long expiresAt, long nextPulse) { }

    private final Map<UUID, Burn> targets = new HashMap<>();

    void ignite(LivingEntity target, int gain, int limit, long now, int duration) {
        var id = target.getUniqueId();
        var current = targets.get(id);
        int previous = current == null || current.expiresAt() < now ? 0 : current.stacks();
        int next = Math.min(previous + gain, limit);
        targets.put(id, new Burn(target, next, now + duration, previous == 0 ? now + 1 : current.nextPulse()));
    }

    Map<UUID, Burn> targets() { return targets; }

    void clear() { targets.clear(); }

    Burn remove(UUID id) { return targets.remove(id); }
}
