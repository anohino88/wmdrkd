package io.github.augmentwild.domain;

import java.util.*;

/** Single-threaded request ledger; adapter must atomically check and debit on the server thread. */
public final class TrackingRequests {
    public enum Status { SUCCESS, NO_TARGET, INSUFFICIENT_ITEMS, NOT_PARTICIPATING, PHASE_NOT_ALLOWED, ALREADY_PROCESSED }
    public record Response(Status status, Tracker.Result target) { }
    @FunctionalInterface public interface Cost { boolean tryDebit(UUID player, int amount); }
    private final Set<UUID> processed = new HashSet<>();
    public Response execute(UUID request, UUID player, boolean participating, boolean allowed,
                            Optional<Tracker.Result> target, int amount, Cost cost) {
        Objects.requireNonNull(request); Objects.requireNonNull(player); Objects.requireNonNull(target); Objects.requireNonNull(cost);
        if (amount<0) throw new IllegalArgumentException();
        if (!processed.add(request)) return new Response(Status.ALREADY_PROCESSED,null);
        if (!participating) return new Response(Status.NOT_PARTICIPATING,null);
        if (!allowed) return new Response(Status.PHASE_NOT_ALLOWED,null);
        if (target.isEmpty()) return new Response(Status.NO_TARGET,null);
        if (!cost.tryDebit(player,amount)) return new Response(Status.INSUFFICIENT_ITEMS,null);
        return new Response(Status.SUCCESS,target.orElseThrow());
    }
}
