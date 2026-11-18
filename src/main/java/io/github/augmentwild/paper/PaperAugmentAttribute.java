package io.github.augmentwild.paper;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;

public final class PaperAugmentAttribute {
    private PaperAugmentAttribute() { }

    /** Reapplication is idempotent. Transient modifiers are never written to player data. */
    public static void update(AttributeInstance attribute, NamespacedKey key, double amount,
                              AttributeModifier.Operation operation, boolean enabled) {
        var current = attribute.getModifier(key);
        if (current != null && (!enabled || current.getAmount() != amount || current.getOperation() != operation)) {
            attribute.removeModifier(current);
            current = null;
        }
        if (enabled && current == null) attribute.addTransientModifier(new AttributeModifier(key, amount, operation));
    }
}
