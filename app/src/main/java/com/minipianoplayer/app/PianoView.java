package com.minipianoplayer.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PianoView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<Key> keys = new ArrayList<>();
    private final ArrayList<Integer> whitePitches = new ArrayList<>();
    private final List<Score.Channel> channels = new ArrayList<>();
    private final RectF rollRect = new RectF(), keyboardRect = new RectF(), noteRect = new RectF(), pulseBounds = new RectF();
    private final float[] pitchCenter = new float[128];
    private int[] notePitch = new int[0];
    private final PianoAudio audio;
    private static final long ATTACK_FLASH_MS = 140;
    private static final int[] WHITE = {0, 2, 4, 5, 7, 9, 11};
    private static final String[] NAMES = {"C", "D", "E", "F", "G", "A", "B"};
    private static final String[] PITCH_NAMES = {"C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B"};
    private int base = 48, topWhite = 88, whites = 24;
    private int[] noteChannel = new int[0], noteStaff = new int[0];
    private double[] noteStart = new double[0], noteEnd = new double[0], measureSeconds = new double[0];
    private String[] measureNumbers = new String[0];
    private int[] measureVisits = new int[0];
    private Score score;
    private double futureSeconds = 7, pastSeconds = 2.5;
    private float density = 1f, scaledDensity = 1f, playheadX, pxPerSecond, rulerWidth, rollLaneTop;
    private int primary = 0xFF167C70, textColor = 0xFF1E3038, mutedColor = 0xFF52696D;
    private int rollBackground = 0xFFF2F8F5, keyboardBackground = 0xFF1E3038;
    private int whiteColor = 0xFFFFFDF5, blackColor = 0xFF22343C;
    private boolean dark;
    private int themeHue = 158;

    private static final class Key {
        final int pitch;
        final boolean black;
        final RectF bounds;
        Key(int pitch, boolean black, RectF bounds) { this.pitch = pitch; this.black = black; this.bounds = bounds; }
    }

    PianoView(Context context, PianoAudio audio) {
        super(context);
        this.audio = audio;
        android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        density = metrics.density;
        scaledDensity = metrics.scaledDensity;
        setContentDescription("鋼琴卷軸與完整音域鍵盤");
        setFocusable(true);
    }

    void theme(UiTheme theme) {
        primary = theme.primary;
        textColor = theme.text;
        mutedColor = theme.muted;
        dark = theme.dark;
        themeHue = theme.hue;
        rollBackground = dark ? 0xFF101B22 : 0xFFF2F8F5;
        keyboardBackground = dark ? 0xFF0B1217 : 0xFF1E3038;
        whiteColor = dark ? 0xFFD3DFDD : 0xFFFFFDF5;
        blackColor = dark ? 0xFF101A22 : 0xFF22343C;
        invalidate();
    }

    void setScore(Score value) {
        score = value;
        channels.clear();
        if (value != null) channels.addAll(value.channels());
        Map<String, Integer> channelIds = new LinkedHashMap<>();
        for (int i = 0; i < channels.size(); i++) channelIds.put(channels.get(i).key, i);
        int count = value == null ? 0 : value.notes.size();
        noteStart = new double[count];
        noteEnd = new double[count];
        notePitch = new int[count];
        noteChannel = new int[count];
        noteStaff = new int[count];
        int low = 108, high = 21;
        for (int i = 0; i < count; i++) {
            Score.Note note = value.notes.get(i);
            noteStart[i] = value.seconds(note.start);
            noteEnd[i] = value.seconds(note.end());
            notePitch[i] = note.pitch;
            noteChannel[i] = channelIds.getOrDefault(note.channelKey(), 0);
            noteStaff[i] = note.staff;
            low = Math.min(low, note.pitch);
            high = Math.max(high, note.pitch);
        }
        if (count == 0) {
            base = 48;
            topWhite = defaultTopWhite(base);
        } else {
            base = whiteAtOrBelow(low);
            topWhite = whiteAtOrAbove(high);
        }
        measureSeconds = new double[value == null ? 0 : value.measures.size()];
        measureNumbers = new String[measureSeconds.length];
        measureVisits = new int[measureSeconds.length];
        for (int i = 0; i < measureSeconds.length; i++) {
            Score.Measure measure = value.measures.get(i);
            measureSeconds[i] = value.seconds(measure.tick);
            measureNumbers[i] = measure.number;
            measureVisits[i] = measure.visit;
        }
        rebuild();
        invalidate();
    }

    String rangeLabel() {
        if (whites <= 0) return "";
        return noteName(base) + " – " + noteName(topWhite) + " · " + whites + " 鍵";
    }

    int whites() { return whites; }

    private int defaultTopWhite(int start) {
        int pitch = start, found = 0;
        while (pitch <= 108) {
            if (isWhite(pitch)) { if (++found == 24) return pitch; }
            pitch++;
        }
        return 88;
    }

    private static boolean isWhite(int pitch) {
        int pc = pitch % 12;
        return pc == 0 || pc == 2 || pc == 4 || pc == 5 || pc == 7 || pc == 9 || pc == 11;
    }

    private static int whiteAtOrBelow(int pitch) {
        int value = Math.max(21, Math.min(108, pitch));
        while (value > 21 && !isWhite(value)) value--;
        return value;
    }

    private static int whiteAtOrAbove(int pitch) {
        int value = Math.max(21, Math.min(108, pitch));
        while (value < 108 && !isWhite(value)) value++;
        return value;
    }

    private static String noteName(int pitch) {
        int pc = pitch % 12;
        int octave = pitch / 12 - 1;
        for (int i = 0; i < WHITE.length; i++) if (WHITE[i] == pc) return NAMES[i] + octave;
        return PITCH_NAMES[pc] + octave;
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        density = metrics.density;
        scaledDensity = metrics.scaledDensity;
        rebuild();
    }

    private void rebuild() {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        whitePitches.clear();
        for (int pitch = base; pitch <= topWhite; pitch++) if (isWhite(pitch)) whitePitches.add(pitch);
        whites = whitePitches.size();
        if (whites == 0) return;
        float keyWidth = getWidth() / (float) whites;
        float keyboardHeight = Math.min(Math.min(132 * density, getHeight() * .30f), Math.max(64 * density, keyWidth * 5.6f));
        float split = Math.round(getHeight() - keyboardHeight);
        rollRect.set(0, 0, getWidth(), split);
        keyboardRect.set(0, split, getWidth(), getHeight());
        for (int i = 0; i < pitchCenter.length; i++) pitchCenter[i] = -1;
        keys.clear();
        for (int i = 0; i < whites; i++) {
            int pitch = whitePitches.get(i);
            float left = i * keyWidth;
            keys.add(new Key(pitch, false, new RectF(left + .5f, keyboardRect.top + 1, left + keyWidth - .5f, keyboardRect.bottom - 1)));
            pitchCenter[pitch] = left + keyWidth / 2;
        }
        for (int i = 0; i < whites - 1; i++) {
            int first = whitePitches.get(i), second = whitePitches.get(i + 1);
            if (second - first != 2) continue;
            int pitch = first + 1;
            float center = (i + 1) * keyWidth;
            float half = keyWidth * .31f;
            keys.add(new Key(pitch, true, new RectF(center - half, keyboardRect.top, center + half, keyboardRect.top + keyboardRect.height() * .61f)));
            pitchCenter[pitch] = center;
        }
        for (int i = 0; i < notePitch.length; i++) if (pitchCenter[notePitch[i]] < 0) pitchCenter[notePitch[i]] = keyboardRect.left;
    }

    private float pitchY(int pitch) {
        int span = Math.max(1, topWhite - base + 1);
        return rollRect.bottom - (pitch - base + .5f) / span * (rollRect.bottom - rollLaneTop);
    }

    private float timeToX(double seconds, double currentSeconds) {
        return playheadX + (float) ((seconds - currentSeconds) * pxPerSecond);
    }

    private int channelColor(int channel, int staff) {
        int hue = (themeHue + channel * 43 + (staff == 2 ? 24 : 0)) % 360;
        return UiTheme.color(hue);
    }

    private boolean muted(int note) {
        if (score == null || channels.isEmpty()) return false;
        int channel = noteChannel[note];
        return channel >= 0 && channel < channels.size() && score.mutedVoices.contains(channels.get(channel).key);
    }

    @Override protected void onDraw(Canvas canvas) {
        if (rollRect.width() <= 0 || keyboardRect.width() <= 0) return;
        double currentSeconds = score == null ? 0 : score.seconds((long) audio.position);
        paint.setStyle(Paint.Style.FILL);
        drawRoll(canvas, currentSeconds);
        drawKeyboard(canvas);
    }

    private void drawRoll(Canvas canvas, double currentSeconds) {
        paint.setColor(rollBackground);
        canvas.drawRect(rollRect, paint);
        boolean portrait = getHeight() > getWidth();
        rulerWidth = (portrait ? 42 : 46) * density;
        float headerHeight = Math.max(18 * density, 16 * scaledDensity);
        rollLaneTop = rollRect.top + headerHeight;
        float contentLeft = rollRect.left + rulerWidth;
        float contentWidth = Math.max(1, rollRect.right - contentLeft);
        double future = portrait ? 6 : futureSeconds;
        double past = portrait ? 2 : pastSeconds;
        pxPerSecond = contentWidth / (float) (future + past);
        playheadX = contentLeft + contentWidth * (float) (past / (future + past));

        paint.setColor(dark ? 0xFF16262E : 0xFFE7F1EC);
        canvas.drawRect(rollRect.left, rollRect.top, rollRect.right, rollLaneTop, paint);
        int span = Math.max(1, topWhite - base + 1);
        float laneHeight = (rollRect.bottom - rollLaneTop) / span;
        for (int pitch = base; pitch <= topWhite; pitch++) {
            float top = pitchY(pitch) - laneHeight / 2;
            int pc = pitch % 12;
            boolean blackLane = pc == 1 || pc == 3 || pc == 6 || pc == 8 || pc == 10;
            paint.setColor(blackLane ? (dark ? 0xFF16262E : 0xFFE8F1ED) : (dark ? 0xFF132129 : 0xFFF7FBF9));
            canvas.drawRect(contentLeft, top, rollRect.right, top + laneHeight, paint);
        }
        paint.setColor(dark ? 0xFF101B22 : 0xFFE3EFE9);
        canvas.drawRect(rollRect.left, rollLaneTop, contentLeft, rollRect.bottom, paint);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(1, density));
        boolean labelEveryWhite = span <= 19;
        for (int pitch = base; pitch <= topWhite; pitch++) {
            boolean c = pitch % 12 == 0;
            boolean label = c || (labelEveryWhite && isWhite(pitch));
            if (!label) continue;
            float y = pitchY(pitch) + laneHeight / 2;
            paint.setColor(c ? (dark ? primary : textColor) : mutedColor);
            paint.setTextSize((c ? 10 : 9) * scaledDensity);
            paint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(noteName(pitch), contentLeft - 7 * density, y + 3.5f * density, paint);
        }
        for (int i = 0; i < measureSeconds.length; i++) {
            float x = timeToX(measureSeconds[i], currentSeconds);
            if (x < contentLeft - 2 || x > rollRect.right + 2) continue;
            stroke.setColor(dark ? 0x774A6570 : 0x664C796E);
            canvas.drawLine(x, rollLaneTop, x, rollRect.bottom, stroke);
        }
        labelPaint.setColor(mutedColor);
        labelPaint.setTextAlign(Paint.Align.LEFT);
        labelPaint.setTextSize(9 * scaledDensity);
        float fadeWidth = 38 * density, labelGap = 6 * density;
        float labelBaseline = rollRect.top + headerHeight - 5 * density;
        float lastRight = -Float.MAX_VALUE;
        canvas.save();
        canvas.clipRect(contentLeft, rollRect.top, rollRect.right, rollLaneTop);
        for (int i = 0; i < measureSeconds.length; i++) {
            float x = timeToX(measureSeconds[i], currentSeconds);
            if (x < contentLeft || x > rollRect.right) continue;
            float fade = Math.min(1f, (x - contentLeft) / fadeWidth);
            if (fade <= .02f) continue;
            String label = measureNumbers[i] + (measureVisits[i] > 1 ? " (" + measureVisits[i] + ")" : "");
            float anchor = x + 3 * density;
            if (anchor < lastRight + labelGap) continue;
            labelPaint.setAlpha((int) (255 * fade));
            canvas.drawText(label, anchor, labelBaseline, labelPaint);
            lastRight = anchor + labelPaint.measureText(label);
        }
        canvas.restore();
        canvas.save();
        canvas.clipRect(contentLeft, rollLaneTop, rollRect.right, rollRect.bottom);
        for (int i = 0; i < noteStart.length; i++) {
            if (noteEnd[i] < currentSeconds - past) continue;
            if (noteStart[i] > currentSeconds + future) break;
            float x1 = timeToX(noteStart[i], currentSeconds), x2 = timeToX(noteEnd[i], currentSeconds);
            if (x2 < contentLeft || x1 > rollRect.right) continue;
            float y = pitchY(notePitch[i]);
            float height = Math.max(2 * density, laneHeight * .78f);
            noteRect.set(x1, y - height / 2, Math.max(x1 + 2 * density, x2), y + height / 2);
            int color = channelColor(noteChannel[i], noteStaff[i]);
            boolean active = currentSeconds >= noteStart[i] && currentSeconds < noteEnd[i];
            boolean isMuted = muted(i);
            if (active && !isMuted) {
                paint.setColor(Color.argb(70, Color.red(color), Color.green(color), Color.blue(color)));
                RectF glow = new RectF(noteRect);
                glow.inset(-2 * density, -2 * density);
                canvas.drawRoundRect(glow, 5 * density, 5 * density, paint);
            }
            int alpha = isMuted ? 42 : 170 + (int) (noteStaff[i] == 2 ? 30 : 60);
            paint.setColor(Color.argb(Math.min(255, alpha), Color.red(color), Color.green(color), Color.blue(color)));
            canvas.drawRoundRect(noteRect, 3 * density, 3 * density, paint);
            if (active && !isMuted) {
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(Math.max(1, density));
                stroke.setColor(Color.argb(210, 255, 255, 255));
                canvas.drawRoundRect(noteRect, 3 * density, 3 * density, stroke);
            }
        }
        canvas.restore();
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1.5f * density);
        stroke.setColor(primary);
        canvas.drawLine(playheadX, rollLaneTop, playheadX, rollRect.bottom, stroke);
        paint.setColor(primary);
        canvas.drawCircle(playheadX, rollLaneTop + 4 * density, 3.5f * density, paint);
        if (noteStart.length == 0) {
            paint.setColor(mutedColor);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(15 * scaledDensity);
            canvas.drawText("匯入 MusicXML／MIDI 樂譜開始播放", contentLeft + contentWidth / 2, rollLaneTop + (rollRect.bottom - rollLaneTop) / 2, paint);
        }
        paint.setColor(dark ? 0xFF2B3D46 : 0xFFD6E4DE);
        canvas.drawRect(0, rollRect.bottom, getWidth(), rollRect.bottom + Math.max(1, density), paint);
    }

    private void drawKeyboard(Canvas canvas) {
        paint.setColor(keyboardBackground);
        canvas.drawRect(keyboardRect, paint);
        long now = SystemClock.uptimeMillis();
        float keyWidth = whites == 0 ? 0 : keyboardRect.width() / whites;
        for (Key key : keys) {
            long attackTime = audio.attackTime(key.pitch), age = now - attackTime;
            float pulse = attackTime > 0 && age >= 0 && age < ATTACK_FLASH_MS ? 1 - age / (float) ATTACK_FLASH_MS : 0;
            boolean lit = audio.lit(key.pitch) || pulse > 0;
            int color = lit ? primary : key.black ? blackColor : whiteColor;
            paint.setColor(color);
            canvas.drawRoundRect(key.bounds, key.black ? 7 : 5, key.black ? 7 : 5, paint);
            if (key.black) {
                paint.setColor(lit ? 0x55334444 : 0xFF48616D);
                canvas.drawRoundRect(key.bounds.left + 4, key.bounds.top + 5, key.bounds.right - 4, key.bounds.bottom - 10, 4, 4, paint);
            }
            if (pulse > 0) {
                paint.setColor(Color.argb((int) (pulse * 170), 255, 248, 211));
                canvas.drawRoundRect(key.bounds, key.black ? 7 : 5, key.black ? 7 : 5, paint);
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(2 * density);
                stroke.setColor(Color.argb((int) (pulse * 255), 242, 147, 35));
                pulseBounds.set(key.bounds);
                pulseBounds.inset(stroke.getStrokeWidth(), stroke.getStrokeWidth());
                canvas.drawRoundRect(pulseBounds, key.black ? 7 : 5, key.black ? 7 : 5, stroke);
                float radius = Math.min(key.bounds.width() * .27f, 16 * density) * (1 - pulse * .7f);
                canvas.drawCircle(key.bounds.centerX(), key.bounds.bottom - (key.black ? 22 : 44) * density, radius, stroke);
            }
            if (!key.black) {
                int pc = key.pitch % 12, degree = 0;
                for (int j = 0; j < 7; j++) if (WHITE[j] == pc) degree = j;
                boolean onlyC = keyWidth < 28 * density;
                if (keyWidth < 15 * density || (onlyC && pc != 0)) continue;
                paint.setColor(lit ? UiTheme.foreground(primary) : dark ? 0xFFB8C9C6 : 0xFF526963);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(Math.min((onlyC ? 12 : 16) * scaledDensity, key.bounds.width() * .36f));
                String label = onlyC ? "C" + (key.pitch / 12 - 1) : NAMES[degree] + (key.pitch / 12 - 1);
                canvas.drawText(label, key.bounds.centerX(), key.bounds.bottom - 12 * density, paint);
            }
        }
    }

}
