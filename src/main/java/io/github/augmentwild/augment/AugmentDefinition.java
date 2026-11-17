package io.github.augmentwild.augment;

import java.util.Objects;

public record AugmentDefinition(String id, boolean enabled, boolean implemented) {
    public AugmentDefinition { Objects.requireNonNull(id); }
    public boolean selectable() { return enabled && implemented; }
}
