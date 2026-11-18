package io.github.augmentwild.domain;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Match-clock geometry and one non-bursting damage pulse per elapsed interval. */
public final class MatchBorder {
    public enum Notice {
        FARMING_TWO_MINUTES("파밍 종료 2분 전입니다."),
        FARMING_THIRTY_SECONDS("파밍 종료 30초 전입니다."),
        FARMING_ENDED("파밍 시간이 종료됐습니다. 잠시 후 목숨 제한이 시작됩니다."),
        SHRINK_STARTED("목숨 제한 및 자기장 축소가 시작됐습니다."),
        FINAL_SIZE("자기장이 최종 크기에 도달했습니다."),
        SUDDEN_DEATH("서든데스가 시작됐습니다.");

        private final String message;
        Notice(String message) { this.message = message; }
        public String message() { return message; }
    }

    public record Update(Arena area, boolean damagePulse, List<Notice> notices) {
        public boolean damages(boolean eligible, boolean sameWorld, boolean protectedPlayer, double x, double z) {
            return damagePulse && eligible && sameWorld && !protectedPlayer && !area.contains(x, z, 0);
        }
    }

    private final Arena initial;
    private final MatchRules rules;
    private final long startNanos;
    private final long intervalNanos;
    private final EnumSet<Notice> announced = EnumSet.noneOf(Notice.class);
    private long lastPulse;
    private long cachedSecond = -1;
    private Arena cachedArea;

    public MatchBorder(Arena initial, MatchRules rules, int damageIntervalSeconds) {
        this.initial = initial;
        this.rules = rules;
        if (damageIntervalSeconds < 1 || rules.borderFinal() > Math.min(initial.width(), initial.height())) {
            throw new IllegalArgumentException("Invalid border dimensions or damage interval");
        }
        startNanos = TimeUnit.SECONDS.toNanos(rules.pvpStartSeconds());
        intervalNanos = TimeUnit.SECONDS.toNanos(damageIntervalSeconds);
    }

    public Arena area(long seconds) {
        if (seconds != cachedSecond) {
            cachedArea = initial.shrink(seconds, rules.pvpStartSeconds(), rules.shrinkSeconds(),
                    rules.borderFinal(), rules.borderFinal());
            cachedSecond = seconds;
        }
        return cachedArea;
    }

    public int progressPercent(long seconds) {
        double progress = (double) (seconds - rules.pvpStartSeconds()) / rules.shrinkSeconds();
        return (int) Math.round(100 * Math.clamp(progress, 0, 1));
    }

    public Update update(long elapsedNanos, boolean paused) {
        long seconds = TimeUnit.NANOSECONDS.toSeconds(elapsedNanos);
        var next = new ArrayList<Notice>();
        long gathering = rules.gatheringSeconds();
        long shrinkEnd = rules.pvpStartSeconds() + rules.shrinkSeconds();
        if (gathering >= 120) announce(next, Notice.FARMING_TWO_MINUTES, seconds, gathering - 120);
        if (gathering >= 30) announce(next, Notice.FARMING_THIRTY_SECONDS, seconds, gathering - 30);
        announce(next, Notice.FARMING_ENDED, seconds, gathering);
        announce(next, Notice.SHRINK_STARTED, seconds, rules.pvpStartSeconds());
        announce(next, Notice.FINAL_SIZE, seconds, shrinkEnd);
        announce(next, Notice.SUDDEN_DEATH, seconds, shrinkEnd);

        // On a delayed tick apply at most one pulse. Missed intervals never accumulate damage.
        long interval = elapsedNanos < startNanos ? 0 : (elapsedNanos - startNanos) / intervalNanos;
        boolean pulse = !paused && interval > lastPulse;
        if (pulse) lastPulse = interval;
        return new Update(area(seconds), pulse, List.copyOf(next));
    }

    private void announce(List<Notice> result, Notice notice, long now, long at) {
        if (now >= at && announced.add(notice)) result.add(notice);
    }
}
