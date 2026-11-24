package io.github.augmentwild.paper;

import java.util.ArrayList;
import java.util.List;

/** Presentation clock. Early cue and contact frames keep their authored ticks in play. */
enum ArtPacing {
    FAST("1.25×",4,5), BALANCED("1×",1,1), CINEMATIC("0.75×",4,3),
    SHOWCASE("0.5×",2,1), LEGACY("8.0",1,1);

    final String label;
    private final int numerator;
    private final int denominator;
    private static final int CONTACT_TICKS = 10;
    static final int MAX_AUTHORED_TICKS = 120;
    static final int MAX_PLAYBACK_TICKS = MAX_AUTHORED_TICKS * 2;

    ArtPacing(String label,int numerator,int denominator) {
        this.label = label;
        this.numerator = numerator;
        this.denominator = denominator;
    }

    List<ArtSignature.Frame> frames(List<ArtSignature.Frame> frames, boolean rehearsal) {
        if (numerator == denominator) return frames;
        int fixed = rehearsal ? 0 : CONTACT_TICKS;
        var paced = new ArrayList<ArtSignature.Frame>(frames.size());
        int previous = -1;
        for (var frame : frames) {
            int tick = frame.tick();
            if (tick > fixed) tick = fixed + Math.round((tick-fixed)*(float)numerator/denominator);
            tick = Math.max(tick,previous+1);
            if (tick > MAX_PLAYBACK_TICKS) throw new IllegalArgumentException("Visual timeline exceeds playback cap");
            paced.add(frame.at(tick));
            previous = tick;
        }
        return List.copyOf(paced);
    }
}
