package io.github.augmentwild.domain;

import java.util.*;

public final class Tracker {
    public enum Status { ACTIVE, RESPAWNING, ELIMINATED, SPECTATING, DISCONNECTED }
    public record Player(UUID id, String name, UUID match, String world, String team, Status status, double x, double y, double z) {
        public Player {
            Objects.requireNonNull(id); Objects.requireNonNull(name); Objects.requireNonNull(match);
            Objects.requireNonNull(world); Objects.requireNonNull(status);
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) throw new IllegalArgumentException();
        }
    }
    public record Result(UUID id, String name, double x, double y, double z, double distance, String direction) { }
    public Optional<Result> nearest(Player source, Collection<Player> players) {
        Objects.requireNonNull(source); Objects.requireNonNull(players);
        if (source.status()!=Status.ACTIVE) return Optional.empty();
        return players.stream().filter(p -> !p.id().equals(source.id()) && p.match().equals(source.match())
                && p.world().equals(source.world()) && p.status()==Status.ACTIVE
                && (source.team()==null || !source.team().equals(p.team())))
                .map(p -> new Result(p.id(),p.name(),p.x(),p.y(),p.z(),
                        Math.hypot(Math.hypot(p.x()-source.x(),p.y()-source.y()),p.z()-source.z()),
                        direction(p.x()-source.x(),p.z()-source.z())))
                .min(Comparator.comparingDouble(Result::distance).thenComparing(Result::id));
    }
    private static String direction(double dx, double dz) {
        if (dx==0 && dz==0) return "HERE";
        String[] directions={"S","SW","W","NW","N","NE","E","SE"};
        return directions[Math.floorMod((int)Math.round(Math.atan2(-dx,dz)/(Math.PI/4)),8)];
    }
}
