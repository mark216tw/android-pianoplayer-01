package com.minipianoplayer.app;

import android.graphics.RectF;
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
    @Test public void portraitGivesTheRollMoreHeightThanTheKeyboard() throws Exception {
        PianoView view = view(score(21, 108));
        Field rollField = PianoView.class.getDeclaredField("rollRect"); rollField.setAccessible(true);
        Field keyboardField = PianoView.class.getDeclaredField("keyboardRect"); keyboardField.setAccessible(true);
        RectF roll = (RectF) rollField.get(view), keyboard = (RectF) keyboardField.get(view);
        assertTrue(roll.height() > keyboard.height());
    }
}
