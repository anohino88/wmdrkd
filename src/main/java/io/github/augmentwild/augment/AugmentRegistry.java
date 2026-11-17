package io.github.augmentwild.augment;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AugmentRegistry {
    private final Map<String, AugmentDefinition> entries = new LinkedHashMap<>();
    public void register(AugmentDefinition definition) {
        if (entries.putIfAbsent(definition.id(), definition) != null) throw new IllegalArgumentException("Duplicate augment: " + definition.id());
    }
    public Collection<AugmentDefinition> all() { return List.copyOf(entries.values()); }
    public List<AugmentDefinition> selectable() { return entries.values().stream().filter(AugmentDefinition::selectable).toList(); }
}
