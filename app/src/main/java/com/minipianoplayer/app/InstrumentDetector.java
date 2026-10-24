package com.minipianoplayer.app;

import java.util.List;
import java.util.Locale;

/** Conservative MusicXML instrument detection for the two bundled timbres. */
final class InstrumentDetector {
    static final class Result {
        final int sound;
        final String label;
        final boolean recognized, lowString;
        Result(int sound, String label, boolean recognized, boolean lowString) {
            this.sound = sound; this.label = label; this.recognized = recognized; this.lowString = lowString;
        }
    }

    private static final String[] VIOLIN = {"violin", "violino", "vln", "fiddle", "小提琴"};
    private static final String[] VIOLA = {"viola", "vla", "中提琴"};
    private static final String[] CELLO = {"violoncello", "cello", "vc", "大提琴"};
    private static final String[] BASS = {"contrabass", "double bass", "upright bass", "低音提琴", "倍低音提琴"};
    private static final String[] PIANO = {"piano", "pianoforte", "pno", "keyboard", "klavier", "鋼琴", "大鋼琴", "平台鋼琴"};

    private InstrumentDetector() {}

    static Result detect(String partName, String abbreviation, List<String> instrumentNames, Integer midiProgram) {
        String text = normalize(partName + " " + abbreviation + " " + String.join(" ", instrumentNames));
        if (contains(text, VIOLIN)) return new Result(PianoSound.VIOLIN, "小提琴", true, false);
        if (contains(text, VIOLA)) return new Result(PianoSound.VIOLIN, "中提琴", true, true);
        if (contains(text, CELLO)) return new Result(PianoSound.VIOLIN, "大提琴", true, true);
        if (contains(text, BASS)) return new Result(PianoSound.VIOLIN, "低音提琴", true, true);
        if (contains(text, PIANO)) return new Result(PianoSound.SALAMANDER, "鋼琴", true, false);
        if (midiProgram != null && midiProgram == 41) return new Result(PianoSound.VIOLIN, "小提琴", true, false);
        if (midiProgram != null && midiProgram == 42) return new Result(PianoSound.VIOLIN, "中提琴", true, true);
        if (midiProgram != null && midiProgram == 43) return new Result(PianoSound.VIOLIN, "大提琴", true, true);
        if (midiProgram != null && midiProgram == 44) return new Result(PianoSound.VIOLIN, "低音提琴", true, true);
        if (midiProgram != null && midiProgram >= 1 && midiProgram <= 8) return new Result(PianoSound.SALAMANDER, "鋼琴", true, false);
        return new Result(PianoSound.SALAMANDER, "鋼琴（未辨識）", false, false);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static boolean contains(String text, String[] keywords) {
        for (String keyword : keywords) if (text.contains(keyword)) return true;
        return false;
    }
}
