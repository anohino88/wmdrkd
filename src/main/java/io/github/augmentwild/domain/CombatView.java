package io.github.augmentwild.domain;

public final class CombatView {
    private CombatView() {}
    public static float ratio(double current, double maximum) {
        return maximum > 0 ? (float) Math.clamp(current / maximum, 0, 1) : 0;
    }
    public static double confirmed(double observed, double expected) {
        // A heal, second cause, or plugin override can invalidate the attribution.
        return observed > 0 && Math.abs(observed - expected) <= .0001 ? observed : 0;
    }
    public static long nextPick(long elapsed, long interval, long gathering) {
        if (elapsed >= gathering) return -1;
        return Math.min(gathering - elapsed, interval - elapsed % interval);
    }
    public static String time(long seconds) {
        return "%d:%02d".formatted(seconds / 60, seconds % 60);
    }
}
