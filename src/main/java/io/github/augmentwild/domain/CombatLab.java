package io.github.augmentwild.domain;

public record CombatLab(boolean enabled, String world, String productionWorld) {
    public boolean ready() {
        return enabled && !world.isBlank() && !world.equalsIgnoreCase(productionWorld);
    }

    public boolean allows(boolean authorised, boolean testRunning, boolean paused) {
        return authorised && ready() && testRunning && !paused;
    }
}
