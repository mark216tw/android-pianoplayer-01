package com.minipianoplayer.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PianoView extends View {
    interface SeekListener {
        void onSeek(double seconds);
        default void onSeekPreview(double seconds) {}
        default void onSeekCancel() {}
    }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<Key> keys = new ArrayList<>();
    private final ArrayList<Integer> whitePitches = new ArrayList<>();
    private final List<Score.Channel> channels = new ArrayList<>();
    private final RectF rollRect = new RectF(), keyboardRect = new RectF(), noteRect = new RectF(), pulseBounds = new RectF(), glowRect = new RectF();
    private final float[] pitchCenter = new float[128];
    private int[] notePitch = new int[0];
    private final PianoAudio audio;
    private static final long ATTACK_FLASH_MS = 140;
    private static final int[] WHITE = {0, 2, 4, 5, 7, 9, 11};
    private static final String[] NAMES = {"C", "D", "E", "F", "G", "A", "B"};
    private static final String[] PITCH_NAMES = {"C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B"};
    private static final String[] NOTE_NAMES = new String[128];
    static { for (int i = 0; i < NOTE_NAMES.length; i++) NOTE_NAMES[i] = createNoteName(i); }
    private int base = 48, topWhite = 88, whites = 24;
    private int[] noteChannel = new int[0], noteStaff = new int[0];
    private double[] noteStart = new double[0], noteEnd = new double[0], noteMaxEnd = new double[0], measureSeconds = new double[0];
    private int[] channelColors = new int[0];
    private String[] measureLabels = new String[0];
    private String rangeLabelText = "";
    private Score score;
    private double futureSeconds = 7, pastSeconds = 2.5;
    private float density = 1f, scaledDensity = 1f, playheadX, pxPerSecond, rulerWidth, rollLaneTop;
    private int primary = 0xFF167C70, textColor = 0xFF1E3038, mutedColor = 0xFF52696D;
    private int rollBackground = 0xFFF2F8F5, keyboardBackground = 0xFF1E3038;
    private int whiteColor = 0xFFFFFDF5, blackColor = 0xFF22343C;
    private boolean dark;
    private int themeHue = 158;
    private SeekListener seekListener;
    private double lastSeconds, dragStartSeconds, dragSeconds = Double.NaN;
    private float downX, downY;
    private boolean tracking, dragging;
    private final int touchSlop;

    private static final class Key {
        final int pitch;
        final boolean black;
        final RectF bounds;
        final String label;
        Key(int pitch, boolean black, RectF bounds, String label) { this.pitch = pitch; this.black = black; this.bounds = bounds; this.label = label; }
    }

    PianoView(Context context, PianoAudio audio) {
        super(context);
        this.audio = audio;
        android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        density = metrics.density;
        scaledDensity = metrics.scaledDensity;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setContentDescription("鋼琴卷軸與完整音域鍵盤，水平拖曳卷軸可移動時間軸");
        setFocusable(true);
    }
    void setOnSeekListener(SeekListener listener) {
        seekListener = listener;
        setClickable(listener != null);
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
        rebuildChannelColors();
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
        noteMaxEnd = new double[count];
        double maxEnd = 0;
        for (int i = 0; i < count; i++) { maxEnd = Math.max(maxEnd, noteEnd[i]); noteMaxEnd[i] = maxEnd; }
        if (count == 0) {
            base = 48;
            topWhite = defaultTopWhite(base);
        } else {
            base = whiteAtOrBelow(low);
            topWhite = whiteAtOrAbove(high);
        }
        updateRangeLabel();
        measureSeconds = new double[value == null ? 0 : value.measures.size()];
        measureLabels = new String[measureSeconds.length];
        for (int i = 0; i < measureSeconds.length; i++) {
            Score.Measure measure = value.measures.get(i);
            measureSeconds[i] = value.seconds(measure.tick);
            measureLabels[i] = measure.number + (measure.visit > 1 ? " (" + measure.visit + ")" : "");
        }
        rebuildChannelColors();
        rebuild();
        invalidate();
    }

    String rangeLabel() { return rangeLabelText; }

    private void updateRangeLabel() {
        int count = 0;
        for (int pitch = base; pitch <= topWhite; pitch++) if (isWhite(pitch)) count++;
        whites = count;
        rangeLabelText = count == 0 ? "" : noteName(base) + " – " + noteName(topWhite) + " · " + count + " 鍵";
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

    private static String noteName(int pitch) { return NOTE_NAMES[Math.max(0, Math.min(127, pitch))]; }

    private static String createNoteName(int pitch) {
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
        if (whites == 0) { rangeLabelText = ""; return; }
        rangeLabelText = noteName(base) + " – " + noteName(topWhite) + " · " + whites + " 鍵";
        float keyWidth = getWidth() / (float) whites;
        boolean compact = keyWidth < 28 * density, hidden = keyWidth < 15 * density;
        float keyboardHeight = Math.min(Math.min(132 * density, getHeight() * .30f), Math.max(64 * density, keyWidth * 5.6f));
        float split = Math.round(getHeight() - keyboardHeight);
        rollRect.set(0, 0, getWidth(), split);
        keyboardRect.set(0, split, getWidth(), getHeight());
        for (int i = 0; i < pitchCenter.length; i++) pitchCenter[i] = -1;
        keys.clear();
        for (int i = 0; i < whites; i++) {
            int pitch = whitePitches.get(i);
            float left = i * keyWidth;
            int pc = pitch % 12, degree = 0;
            for (int j = 0; j < WHITE.length; j++) if (WHITE[j] == pc) degree = j;
            String label = hidden || (compact && pc != 0) ? null : compact ? "C" + (pitch / 12 - 1) : NAMES[degree] + (pitch / 12 - 1);
            keys.add(new Key(pitch, false, new RectF(left + .5f, keyboardRect.top + 1, left + keyWidth - .5f, keyboardRect.bottom - 1), label));
            pitchCenter[pitch] = left + keyWidth / 2;
        }
        for (int i = 0; i < whites - 1; i++) {
            int first = whitePitches.get(i), second = whitePitches.get(i + 1);
            if (second - first != 2) continue;
            int pitch = first + 1;
            float center = (i + 1) * keyWidth;
            float half = keyWidth * .31f;
            keys.add(new Key(pitch, true, new RectF(center - half, keyboardRect.top, center + half, keyboardRect.top + keyboardRect.height() * .61f), null));
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

    private void rebuildChannelColors() {
        channelColors = new int[channels.size() * 2];
        for (int channel = 0; channel < channels.size(); channel++) {
            for (int variant = 0; variant < 2; variant++) {
                int hue = (themeHue + channel * 43 + (variant == 1 ? 24 : 0)) % 360;
                channelColors[channel * 2 + variant] = UiTheme.noteColor(hue, dark);
            }
        }
    }

    private int channelColor(int channel, int staff) {
        int index = channel * 2 + (staff == 2 ? 1 : 0);
        if (index >= 0 && index < channelColors.length) return channelColors[index];
        int hue = (themeHue + channel * 43 + (staff == 2 ? 24 : 0)) % 360;
        return UiTheme.noteColor(hue, dark);
    }

    static int firstAtOrAbove(double[] values, double target) {
        int low = 0, high = values.length;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (values[mid] < target) low = mid + 1; else high = mid;
        }
        return low;
    }

    private boolean muted(int note) {
        if (score == null || channels.isEmpty()) return false;
        int channel = noteChannel[note];
        return channel >= 0 && channel < channels.size() && score.mutedVoices.contains(channels.get(channel).key);
    }

    static double draggedSeconds(double startSeconds, float dx, float pxPerSecond, double duration) {
        if (!Float.isFinite(pxPerSecond) || pxPerSecond <= 0 || !Double.isFinite(duration)) return startSeconds;
        return Math.max(0, Math.min(duration, startSeconds - dx / pxPerSecond));
    }
    private double durationSeconds() { return score == null ? 0 : score.seconds(score.length()); }
    private void resetTouch(boolean notifyCancel) {
        boolean wasDragging = dragging;
        tracking = false; dragging = false; dragSeconds = Double.NaN; downX = downY = 0;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        if (notifyCancel && wasDragging && seekListener != null) seekListener.onSeekCancel();
        invalidate();
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (score == null || seekListener == null || pxPerSecond <= 0) return super.onTouchEvent(event);
        float x = event.getX(), y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!rollRect.contains(x, y)) return false;
                tracking = true; dragging = false; dragSeconds = Double.NaN;
                downX = x; downY = y; dragStartSeconds = lastSeconds;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!tracking) return false;
                float dx = x - downX, dy = y - downY;
                if (!dragging) {
                    if (Math.abs(dx) <= touchSlop || Math.abs(dx) <= Math.abs(dy)) return true;
                    dragging = true;
                }
                dragSeconds = draggedSeconds(dragStartSeconds, dx, pxPerSecond, durationSeconds());
                seekListener.onSeekPreview(dragSeconds);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                if (!tracking) return false;
                if (!dragging) { resetTouch(false); return true; }
                double target = dragSeconds;
                resetTouch(false);
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                seekListener.onSeek(target);
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (!tracking) return false;
                resetTouch(true);
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }
    @Override protected void onDraw(Canvas canvas) {
        if (rollRect.width() <= 0 || keyboardRect.width() <= 0) return;
        double currentSeconds = score == null ? 0 : score.seconds((long) (audio == null ? 0 : audio.position));
        lastSeconds = currentSeconds;
        paint.setStyle(Paint.Style.FILL);
        drawRoll(canvas, Double.isNaN(dragSeconds) ? currentSeconds : dragSeconds);
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
        int measureFrom = firstAtOrAbove(measureSeconds, currentSeconds - past);
        for (int i = measureFrom; i < measureSeconds.length; i++) {
            float x = timeToX(measureSeconds[i], currentSeconds);
            if (x > rollRect.right + 2) break;
            if (x < contentLeft - 2) continue;
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
        for (int i = measureFrom; i < measureSeconds.length; i++) {
            float x = timeToX(measureSeconds[i], currentSeconds);
            if (x > rollRect.right) break;
            if (x < contentLeft) continue;
            float fade = Math.min(1f, (x - contentLeft) / fadeWidth);
            if (fade <= .02f) continue;
            String label = measureLabels[i];
            float anchor = x + 3 * density;
            if (anchor < lastRight + labelGap) continue;
            labelPaint.setAlpha((int) (255 * fade));
            canvas.drawText(label, anchor, labelBaseline, labelPaint);
            lastRight = anchor + labelPaint.measureText(label);
        }
        canvas.restore();
        canvas.save();
        canvas.clipRect(contentLeft, rollLaneTop, rollRect.right, rollRect.bottom);
        double lower = currentSeconds - past, upper = currentSeconds + future;
        for (int i = firstAtOrAbove(noteMaxEnd, lower); i < noteStart.length; i++) {
            if (noteStart[i] > upper) break;
            if (noteEnd[i] < lower) continue;
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
                glowRect.set(noteRect);
                glowRect.inset(-2 * density, -2 * density);
                canvas.drawRoundRect(glowRect, 5 * density, 5 * density, paint);
            }
            int alpha = isMuted ? 42 : dark ? 170 + (noteStaff[i] == 2 ? 30 : 60) : 255;
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
            canvas.drawText("匯入 MusicXML 樂譜開始播放", contentLeft + contentWidth / 2, rollLaneTop + (rollRect.bottom - rollLaneTop) / 2, paint);
        }
        paint.setColor(dark ? 0xFF2B3D46 : 0xFFD6E4DE);
        canvas.drawRect(0, rollRect.bottom, getWidth(), rollRect.bottom + Math.max(1, density), paint);
    }

    private void drawKeyboard(Canvas canvas) {
        paint.setColor(keyboardBackground);
        canvas.drawRect(keyboardRect, paint);
        long now = SystemClock.uptimeMillis();
        boolean compact = whites > 0 && keyboardRect.width() / whites < 28 * density;
        for (Key key : keys) {
            long attackTime = audio == null ? 0 : audio.attackTime(key.pitch), age = now - attackTime;
            float pulse = attackTime > 0 && age >= 0 && age < ATTACK_FLASH_MS ? 1 - age / (float) ATTACK_FLASH_MS : 0;
            boolean lit = (audio != null && audio.lit(key.pitch)) || pulse > 0;
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
            if (key.label != null) {
                paint.setColor(lit ? UiTheme.foreground(primary) : dark ? 0xFFB8C9C6 : 0xFF526963);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(Math.min((compact ? 12 : 16) * scaledDensity, key.bounds.width() * .36f));
                canvas.drawText(key.label, key.bounds.centerX(), key.bounds.bottom - 12 * density, paint);
            }
        }
    }

}
