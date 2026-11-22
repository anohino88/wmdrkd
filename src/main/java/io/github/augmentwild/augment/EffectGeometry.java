package io.github.augmentwild.augment;

/** Normalized, local-space samples for bounded decorative paths. +Z is forward. */
public enum EffectGeometry {
    CRESCENT, HELIX, FRACTURE, ORBIT, CONVERGE, SHOCKWAVE, REWIND, SHARDS;
    public record Point(double x,double y,double z) {}

    public Point sample(int index,int count,double progress) {
        if (count < 2 || index < 0 || index >= count || !Double.isFinite(progress))
            throw new IllegalArgumentException("Invalid geometry sample");
        double u = (double)index / count;
        double t = Math.clamp(progress,0,1);
        double angle = u * Math.TAU;
        return switch (this) {
            case CRESCENT -> {
                double sweep = (u-.5)*3.6;
                yield new Point(Math.sin(sweep),.22*Math.sin(sweep)+t*.15,Math.cos(sweep));
            }
            case HELIX -> new Point(.65*Math.cos(angle*2+t),u*1.3-.4,.65*Math.sin(angle*2+t));
            case FRACTURE -> new Point((u-.5)*1.7,(index%2)*.18,t*(index%3)*.22);
            case ORBIT -> new Point(Math.cos(angle+t),.42*Math.sin(angle*2),Math.sin(angle+t));
            case CONVERGE -> new Point((1-t)*Math.cos(angle),.7*(1-t), (1-t)*Math.sin(angle));
            case SHOCKWAVE -> new Point(t*Math.cos(angle),.12*Math.sin(Math.PI*t),t*Math.sin(angle));
            case REWIND -> new Point(.75*Math.cos(angle-t*4),u*.32,.75*Math.sin(angle-t*4));
            case SHARDS -> new Point(Math.cos(angle)*t,Math.sin(u*Math.PI)*t-.3*t*t,Math.sin(angle)*t);
        };
    }
}
