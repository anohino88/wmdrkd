package io.github.augmentwild.domain;

/** Visual progress from the authoritative opening timer. No inventory or world state is changed. */
public final class SupplyVisual {
    private SupplyVisual() {}

    public static int step(long remaining,long opening) {
        if (opening <= 0 || remaining >= opening) return 0;
        if (remaining <= 0) return 6;
        return (int) (6.0 * (1 - (double)remaining / opening));
    }

    public static int degrees(int step,boolean premium) {
        return switch (step) {
            case 0 -> 0;
            case 1 -> 7;
            case 2 -> 16;
            case 3 -> premium ? 31 : 28;
            case 4 -> premium ? 56 : 45;
            case 5 -> premium ? 73 : 60;
            case 6 -> premium ? 99 : 85;
            default -> throw new IllegalArgumentException("Unknown opening step " + step);
        };
    }
}
