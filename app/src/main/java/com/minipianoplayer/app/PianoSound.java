package com.minipianoplayer.app;

import android.content.Context;

final class PianoSound {
    static final int SALAMANDER = 0, SYNTHETIC = 2, VIOLIN = 3, AUTO = 4;
    /** User-selectable timbres, in display order. */
    static final int[] SELECTABLE = {AUTO, SALAMANDER, VIOLIN, SYNTHETIC};
    static String name(int sound) {
        if (sound == AUTO) return "自動（依樂譜）";
        if (sound == SYNTHETIC) return "合成鋼琴";
        if (sound == VIOLIN) return "小提琴";
        return "Salamander 大鋼琴";
    }
    static String directory(int sound) {
        if (sound == SYNTHETIC) return "";
        if (sound == VIOLIN || sound == AUTO) return "violin";
        return "piano";
    }
    static int selected(Context context) {
        // 4 selects automatic MusicXML routing; 3 the sampled violin; 2 the
        // synthetic timbre. Anything else (including the retired VSCO id 1) is
        // Salamander. New installs default to automatic routing.
        if (!UiTheme.preferences(context).contains("pianoSound")) return AUTO;
        int stored = UiTheme.preferences(context).getInt("pianoSound", SALAMANDER);
        if (stored == AUTO) return AUTO;
        if (stored == SYNTHETIC) return SYNTHETIC;
        if (stored == VIOLIN) return VIOLIN;
        return SALAMANDER;
    }
    static void select(Context context, int sound) {
        if (sound != AUTO && sound != SALAMANDER && sound != SYNTHETIC && sound != VIOLIN) throw new IllegalArgumentException("Invalid piano sound");
        UiTheme.preferences(context).edit().putInt("pianoSound", sound).apply();
    }
}
