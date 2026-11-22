package io.github.augmentwild.paper;

import io.github.augmentwild.domain.Arena;
import io.github.augmentwild.domain.ArenaCoordinates;
import io.github.augmentwild.domain.GameSession;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

final class ArenaPlacement {
    private record Attempt(GameSession.RespawnReservation reservation, ArenaCoordinates.Column column,
                           CompletableFuture<Chunk> chunk) { }

    private final Random random = new Random();
    private final Map<UUID, Attempt> attempts = new HashMap<>();
    private final Map<UUID, Integer> failures = new HashMap<>();
    private final Map<UUID, Long> retryAt = new HashMap<>();
    private final Map<UUID, ArenaCoordinates.Column> previous = new HashMap<>();
    private final Map<UUID, Long> protectedUntil = new HashMap<>();
    private final int margin;
    private final int attemptsPerCycle;
    private final int retryTicks;
    private final int preferredSeparation;
    private final long protectionNanos;

    ArenaPlacement(int margin, int attemptsPerCycle, int retryTicks, int protectionSeconds, int preferredSeparation) {
        if (margin < 1 || attemptsPerCycle < 1 || retryTicks < 1 || protectionSeconds < 0 || preferredSeparation < 0) {
            throw new IllegalArgumentException("Invalid respawn search settings");
        }
        this.margin = margin;
        this.attemptsPerCycle = attemptsPerCycle;
        this.retryTicks = retryTicks;
        this.preferredSeparation = preferredSeparation;
        protectionNanos = java.util.concurrent.TimeUnit.SECONDS.toNanos(protectionSeconds);
    }

    boolean protectedFromDamage(UUID id, long tickTime) {
        Long deadline = protectedUntil.get(id);
        return deadline != null && deadline - tickTime > 0;
    }

    void protect(UUID id, int seconds) {
        long now = System.nanoTime();
        long duration = java.util.concurrent.TimeUnit.SECONDS.toNanos(seconds);
        if (duration > 0 && protectedUntil.getOrDefault(id, now) - now < duration) {
            protectedUntil.put(id, now + duration);
        }
    }

    void forgetAttempt(UUID id) { attempts.remove(id); }

    void forget(UUID id) {
        forgetAttempt(id);
        failures.remove(id);
        retryAt.remove(id);
        previous.remove(id);
        protectedUntil.remove(id);
    }

    void clear() {
        attempts.clear();
        failures.clear();
        retryAt.clear();
        previous.clear();
        protectedUntil.clear();
    }

    void respawn(Player player, World world, Arena safe, GameSession session, long gameTime, long tick,
                 BiPredicate<Player, Location> move) {
        UUID id = player.getUniqueId();
        var reservation = session.reservation(id).orElse(null);
        if (reservation == null || reservation.deadline() > gameTime || player.isDead()
                || tick < retryAt.getOrDefault(id, 0L)) return;
        var attempt = attempts.get(id);
        if (attempt == null) {
            if (attempts.size() >= 4) return;
            var column = ArenaCoordinates.sample(safe, margin, random).orElse(null);
            if (column == null) {
                defer(id, tick);
                return;
            }
            if (column.equals(previous.get(id)) && failures.getOrDefault(id, 0) < attemptsPerCycle - 1) {
                defer(id, tick);
                return;
            }
            attempts.put(id, new Attempt(reservation, column, world.getChunkAtAsync(column.x() >> 4, column.z() >> 4, false)));
            return;
        }
        if (!attempt.chunk().isDone()) return;
        attempts.remove(id);
        if (!reservation.equals(attempt.reservation())) return;
        Location target = null;
        var column = attempt.column();
        if (!attempt.chunk().isCompletedExceptionally() && attempt.chunk().getNow(null) != null
                && safe.contains(column.x() + 0.5, column.z() + 0.5, margin)) {
            target = SafeArenaSurface.spawnAt(world, column.x(), column.z());
        }
        if (target == null || (failures.getOrDefault(id, 0) < attemptsPerCycle / 2
                && nearSurvivor(target, player, session))) {
            defer(id, tick);
            return;
        }
        Location before = player.getLocation();
        if (!move.test(player, target)) {
            if (!player.getLocation().equals(before)) move.test(player, before);
            defer(id, tick);
            return;
        }
        if (!session.respawn(reservation, gameTime)) {
            move.test(player, before);
            defer(id, tick);
            return;
        }
        previous.put(id, column);
        failures.remove(id);
        retryAt.remove(id);
        protectedUntil.put(id, System.nanoTime() + protectionNanos);
        player.sendMessage("안전한 경기 위치에서 리스폰했습니다.");
    }

    private boolean nearSurvivor(Location point, Player player, GameSession session) {
        return point.getWorld().getPlayers().stream().filter(other -> !other.equals(player))
                .filter(other -> session.participantIds().contains(other.getUniqueId()))
                .filter(other -> session.player(other.getUniqueId()).life().status()
                        == io.github.augmentwild.match.LifeState.Status.ACTIVE)
                .anyMatch(other -> other.getLocation().distanceSquared(point)
                        < (double) preferredSeparation * preferredSeparation);
    }

    private void defer(UUID id, long tick) {
        int count = failures.merge(id, 1, Integer::sum);
        if (count >= attemptsPerCycle) {
            failures.remove(id);
            retryAt.put(id, tick + retryTicks);
        }
    }
}
