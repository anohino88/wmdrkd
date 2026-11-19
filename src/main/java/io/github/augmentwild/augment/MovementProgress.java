package io.github.augmentwild.augment;

public final class MovementProgress {
    private double distance;

    public void reset() { distance = 0; }

    public static double horizontal(double dx, double dz, double maximumStep) {
        double step = Math.hypot(dx, dz);
        return Double.isFinite(step) && maximumStep > 0 && step <= maximumStep ? step : 0;
    }

    public int add(double step, double threshold) {
        if (!Double.isFinite(step) || step < 0 || !Double.isFinite(threshold) || threshold <= 0) {
            throw new IllegalArgumentException("Invalid movement progress");
        }
        distance += step;
        int earned = (int) (distance / threshold);
        distance %= threshold;
        return earned;
    }
}
