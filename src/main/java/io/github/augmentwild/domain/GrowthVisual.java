package io.github.augmentwild.domain;

/** Visual-only quartiles. The caller retains the actual growth and evolution rules. */
public final class GrowthVisual {
    private GrowthVisual() {}

    public static int stage(double progress,double goal) {
        if (!Double.isFinite(progress) || !Double.isFinite(goal) || goal <= 0 || progress <= 0) return 0;
        return (int) (Math.min(progress/goal,1) * 4);
    }
}
