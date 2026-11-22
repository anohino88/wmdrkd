package io.github.augmentwild.domain;

/** One deterministic midpoint per stratum. Its weight is the number of columns the stratum represents. */
public final class OceanSampling {
    private OceanSampling() { }

    public static int[] axisWeights(int size, int step) {
        int[] weights = new int[size];
        for (int start = 0; start < size; start += step) {
            int length = Math.min(step, size - start);
            weights[start + (length - 1) / 2] = length;
        }
        return weights;
    }

    public static boolean refine(double estimate, double maximum, double tolerance) {
        return Math.abs(estimate - maximum) <= tolerance + 1e-12;
    }

    public static boolean exceeds(long oceanWeight, long totalWeight, double maximum) {
        return oceanWeight > totalWeight * maximum + 1e-9;
    }
}
