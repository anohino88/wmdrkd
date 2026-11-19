package io.github.augmentwild.augment;

/** Separate combat ledger; never changes the entity's vanilla absorption pool. */
public final class ResonanceShield {
    private double points;
    private long expiresAt;

    public double remaining(long now) {
        return now < expiresAt ? points : 0;
    }

    public void grant(long now, double capacity, long duration) {
        if (capacity <= 0 || duration <= 0) return;
        points = capacity;
        expiresAt = Math.addExact(now, duration);
    }

    public double preview(long now, double damage) {
        return Math.min(remaining(now), Math.max(0, damage));
    }

    public void consume(long now, double amount) {
        points = remaining(now) - preview(now, amount);
    }

    public void clear() {
        points = 0;
        expiresAt = 0;
    }
}
