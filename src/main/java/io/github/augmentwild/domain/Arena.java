package io.github.augmentwild.domain;

/** Continuous rectangle: lower edges inclusive, upper edges exclusive. */
public record Arena(double x, double z, double width, double height) {
    public Arena {
        if (!Double.isFinite(x) || !Double.isFinite(z) || !Double.isFinite(width) || !Double.isFinite(height)
                || width <= 0 || height <= 0 || !Double.isFinite(x-width/2) || !Double.isFinite(x+width/2)
                || !Double.isFinite(z-height/2) || !Double.isFinite(z+height/2)) throw new IllegalArgumentException();
    }
    public double minX() { return x-width/2; }
    public double maxX() { return x+width/2; }
    public double minZ() { return z-height/2; }
    public double maxZ() { return z+height/2; }
    public boolean contains(double px, double pz, double margin) {
        if (!Double.isFinite(margin) || margin < 0 || margin*2 >= Math.min(width,height)) throw new IllegalArgumentException();
        return px >= minX()+margin && px < maxX()-margin && pz >= minZ()+margin && pz < maxZ()-margin;
    }
    public boolean overlaps(Arena other, double buffer) {
        if (!Double.isFinite(buffer) || buffer < 0) throw new IllegalArgumentException();
        return minX() < other.maxX()+buffer && maxX()+buffer > other.minX()
                && minZ() < other.maxZ()+buffer && maxZ()+buffer > other.minZ();
    }
    public boolean centersAtLeast(Arena other, double distance) {
        if (!Double.isFinite(distance) || distance < 0) throw new IllegalArgumentException();
        return Math.hypot(x-other.x,z-other.z) >= distance;
    }
    /** Linear interpolation in match seconds; final width/height may differ. */
    public Arena shrink(long elapsed, long start, long duration, double finalWidth, double finalHeight) {
        if (elapsed < 0 || start < 0 || duration <= 0 || !Double.isFinite(finalWidth) || !Double.isFinite(finalHeight)
                || finalWidth <= 0 || finalHeight <= 0 || finalWidth > width || finalHeight > height) throw new IllegalArgumentException();
        double fraction = elapsed <= start ? 0 : Math.min(1.0, (double)(elapsed-start)/duration);
        return new Arena(x,z,width+(finalWidth-width)*fraction,height+(finalHeight-height)*fraction);
    }
}
