package com.minipianoplayer.app;

/** Chooses the sample bank for a violin note by pitch and notated duration. */
final class ViolinRouting {
    static final int ARCO = 0, SPIC = 1, LOW_PIANO = 2;
    static final double SPIC_SECONDS = .35;
    static final int LOW_PIANO_HIGH = 54;
    static final int SPIC_HIGH = 96;
    private ViolinRouting() {}
    static int route(int pitch, double durationSeconds) { return route(pitch, durationSeconds, false, Score.Note.NORMAL); }
    static int route(int pitch, double durationSeconds, boolean allowLowString) { return route(pitch, durationSeconds, allowLowString, Score.Note.NORMAL); }
    static int route(int pitch, double durationSeconds, boolean allowLowString, int articulation) {
        if (pitch <= LOW_PIANO_HIGH && !allowLowString) return LOW_PIANO;
        if (pitch > SPIC_HIGH) return ARCO;
        if (articulation == Score.Note.STACCATO) return SPIC;
        if (articulation == Score.Note.TENUTO) return ARCO;
        if (durationSeconds < SPIC_SECONDS) return SPIC;
        return ARCO;
    }
}
