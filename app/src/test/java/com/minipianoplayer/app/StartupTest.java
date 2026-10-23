package com.minipianoplayer.app;

import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 29, 35})
public class StartupTest {
    @Test public void activityLaunchesInPortraitAndDrawsKeyboard() throws Exception {
        try (var controller = Robolectric.buildActivity(MainActivity.class)) {
            MainActivity activity = controller.setup().get();
            ActivityInfo info = activity.getPackageManager().getActivityInfo(activity.getComponentName(), 0);
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, info.screenOrientation);
            Field pianoField = MainActivity.class.getDeclaredField("piano"); pianoField.setAccessible(true);
            Field whitesField = PianoView.class.getDeclaredField("whites"); whitesField.setAccessible(true);
            assertEquals(24, whitesField.getInt(pianoField.get(activity)));
            View content = activity.findViewById(android.R.id.content);
            content.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY));
            content.layout(0, 0, 720, 1280);
            Bitmap bitmap = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888);
            content.draw(new Canvas(bitmap));
            assertNotNull(content);
            bitmap.recycle();
            controller.pause().resume();
        }
    }
}
