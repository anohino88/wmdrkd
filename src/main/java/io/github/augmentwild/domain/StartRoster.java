package io.github.augmentwild.domain;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/** Eligible players are supplied in stable order. Explicit signups take priority. */
public record StartRoster(List<UUID> players, int automatic, int capacityExcluded) {
    public static StartRoster select(Collection<UUID> signedUp, Collection<UUID> eligible,
                                     boolean autoJoin, int limit) {
        var admitted = new LinkedHashSet<UUID>();
        var online = new LinkedHashSet<>(eligible);
        for (UUID id : signedUp) if (online.contains(id)) admitted.add(id);
        int explicit = Math.min(admitted.size(), limit);
        if (autoJoin) admitted.addAll(online);
        var selected = admitted.stream().limit(limit).toList();
        return new StartRoster(selected, selected.size() - explicit, admitted.size() - selected.size());
    }
}
