package com.minipianoplayer.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public class PianoViewTest {
    private PianoView view(Score score) {
        PianoView view = new PianoView(RuntimeEnvironment.getApplication(), null);
        view.setScore(score);
        view.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 720, 1280);
        return view;
    }
    private Score score(int... pitches) {
        Score score = new Score();
        for (int pitch : pitches) score.notes.add(new Score.Note(pitch, 0, 480, 90));
        score.sort();
        return score;
    }
    @Test public void fullPianoRangeShowsAllEightyEightKeys() {
        PianoView view = view(score(21, 108));
        assertEquals(52, view.whites());
        assertEquals("A0 – C8 · 52 鍵", view.rangeLabel());
    }
    @Test public void narrowScoreUsesOnlyItsOwnRange() {
        PianoView view = view(score(60, 72));
        assertEquals(8, view.whites());
        assertEquals("C4 – C5 · 8 鍵", view.rangeLabel());
    }
    @Test public void blackKeyEdgesExpandToSurroundingWhiteKeys() {
        PianoView view = view(score(61, 70));
        assertEquals("C4 – B4 · 7 鍵", view.rangeLabel());
    }
    @Test public void emptyScoreUsesReadableDefaultRange() {
        PianoView view = view(new Score());
        assertEquals(24, view.whites());
        assertTrue(view.rangeLabel().contains("24 鍵"));
    }
    @Test public void binarySearchFindsFirstValueAtOrAboveTarget() {
        double[] values = {0, 1, 1, 2, 5};
        assertEquals(0, PianoView.firstAtOrAbove(values, -1));
        assertEquals(1, PianoView.firstAtOrAbove(values, 1));
        assertEquals(4, PianoView.firstAtOrAbove(values, 3));
        assertEquals(5, PianoView.firstAtOrAbove(values, 6));
        assertEquals(0, PianoView.firstAtOrAbove(new double[0], 0));
    }
    @Test public void portraitGivesTheRollMoreHeightThanTheKeyboard() throws Exception {
        PianoView view = view(score(21, 108));
        Field rollField = PianoView.class.getDeclaredField("rollRect"); rollField.setAccessible(true);
        Field keyboardField = PianoView.class.getDeclaredField("keyboardRect"); keyboardField.setAccessible(true);
        RectF roll = (RectF) rollField.get(view), keyboard = (RectF) keyboardField.get(view);
        assertTrue(roll.height() > keyboard.height());
    }
    @Test public void dragConversionUsesHorizontalDistanceAndClamps() {
        assertEquals(7, PianoView.draggedSeconds(2, -50, 10, 10), .0001);
        assertEquals(0, PianoView.draggedSeconds(2, 50, 10, 10), .0001);
        assertEquals(10, PianoView.draggedSeconds(9, -500, 10, 10), .0001);
    }
    @Test public void tappingRollDoesNotSeekButHorizontalDragDoes() throws Exception {
        Score source = new Score(); source.notes.add(new Score.Note(60, 0, 9600, 90)); source.sort();
        PianoView view = view(source);
        Bitmap bitmap = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        double[] target = {-1}, preview = {Double.NaN};
        view.setOnSeekListener(new PianoView.SeekListener() {
            @Override public void onSeek(double seconds) { target[0] = seconds; }
            @Override public void onSeekPreview(double seconds) { preview[0] = seconds; }
        });
        Field dragField = PianoView.class.getDeclaredField("dragSeconds"); dragField.setAccessible(true);
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 360, 100, 0);
        MotionEvent up = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, 360, 100, 0);
        assertTrue(view.onTouchEvent(down)); assertTrue(view.onTouchEvent(up));
        down.recycle(); up.recycle();
        assertEquals(-1, target[0], 0);

        down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 360, 100, 0);
        MotionEvent move = MotionEvent.obtain(now, now, MotionEvent.ACTION_MOVE, 200, 100, 0);
        up = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, 200, 100, 0);
        assertTrue(view.onTouchEvent(down)); assertTrue(view.onTouchEvent(move));
        assertTrue(preview[0] > 0);
        assertEquals(preview[0], (double) dragField.get(view), .0001);
        assertEquals(-1, target[0], 0);
        assertTrue(view.onTouchEvent(up));
        down.recycle(); move.recycle(); up.recycle();
        assertTrue(target[0] > 0);
        assertTrue(Double.isNaN((double) dragField.get(view)));

        target[0] = -1;
        down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 360, 1200, 0);
        up = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, 360, 1200, 0);
        view.onTouchEvent(down); view.onTouchEvent(up); down.recycle(); up.recycle();
        assertEquals(-1, target[0], 0);
        bitmap.recycle();
    }
}
