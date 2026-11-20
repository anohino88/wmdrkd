package io.github.augmentwild.domain;

public final class CombatView {
    private CombatView() {}
    public static final int RANGE = 24;
    public static final long GRACE = 500_000_000L;
    public static final long DAMAGE_WINDOW = 200_000_000L;
    public static final long DAMAGE_VISIBLE = 1_200_000_000L;
    public record Observation(java.util.UUID target, String name, double hp, double max, double absorption, long seen) {
        public boolean visible(long now) { return now - seen < GRACE; }
        public String title() {
            return String.format(java.util.Locale.ROOT,"%s · %.1f / %.1f HP · 흡수 +%.1f",name,hp,max,absorption);
        }
    }
    public record Vitals(double hp, double absorption) {}
    public record Receipt(Vitals before, Vitals expected) {
        public Vitals confirm(Vitals after, boolean cancelled) {
            return cancelled ? new Vitals(0,0) : new Vitals(
                    confirmed(before.hp - after.hp,expected.hp), confirmed(before.absorption - after.absorption,expected.absorption));
        }
    }
    public record OriginTarget(java.util.UUID attacker, java.util.UUID target, java.util.UUID origin) {}
    public static String supplyStatus(boolean published, boolean claimed, boolean attempted, boolean present) {
        if (!published) return "공개 대기";
        if (claimed) return "획득 완료";
        if (attempted) return present ? "개봉 가능" : "취소";
        return "등장 예정";
    }
    public static String feed(String cause, boolean eliminated) {
        return cause + (eliminated ? " · 최종 탈락" : " · 사망");
    }
    public static boolean scan(long tick) { return tick % 5 == 0; }
    public static boolean inRange(double distanceSquared) { return distanceSquared <= RANGE * RANGE; }

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
