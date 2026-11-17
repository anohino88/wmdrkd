package io.github.augmentwild.match;

/** Pure per-player death state. Access must be serialized by the match owner. */
public final class LifeState {
    public enum Status { ACTIVE, RESPAWNING, ELIMINATED }
    public enum Phase { GATHERING, PVP, SUDDEN_DEATH }
    private int lives;
    private Status status = Status.ACTIVE;

    public LifeState(int lives) {
        if (lives < 1) throw new IllegalArgumentException("lives must be positive");
        this.lives = lives;
    }
    public Status status() { return status; }
    public int lives() { return lives; }
    /** Returns false for duplicate lethal events while not active. */
    public boolean resolveDeath(Phase phase) {
        java.util.Objects.requireNonNull(phase);
        if (status != Status.ACTIVE) return false;
        if (phase == Phase.SUDDEN_DEATH) status = Status.ELIMINATED;
        else if (phase == Phase.GATHERING) status = Status.RESPAWNING;
        else { lives--; status = lives == 0 ? Status.ELIMINATED : Status.RESPAWNING; }
        return true;
    }
    public boolean respawn() {
        if (status != Status.RESPAWNING) return false;
        status = Status.ACTIVE;
        return true;
    }
}
