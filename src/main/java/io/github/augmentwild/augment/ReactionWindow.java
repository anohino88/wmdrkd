package io.github.augmentwild.augment;

import java.util.ArrayDeque;

/** A rolling sum on the match clock. Equal-time samples share a bucket. */
public final class ReactionWindow {
    private record Sample(long at, double amount) {}
    private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    private double total;

    public double add(long now, long interval, double amount) {
        while (!samples.isEmpty() && now - samples.getFirst().at() > interval) total -= samples.removeFirst().amount();
        if (samples.isEmpty()) total = 0;
        total += amount;
        if (!samples.isEmpty() && samples.getLast().at() == now) amount += samples.removeLast().amount();
        samples.addLast(new Sample(now, amount));
        return total;
    }

    public boolean expired(long now, long interval) {
        return samples.isEmpty() || now - samples.getLast().at() > interval;
    }
}
