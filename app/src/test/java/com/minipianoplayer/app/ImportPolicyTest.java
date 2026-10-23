package com.minipianoplayer.app;

import android.content.Intent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public class ImportPolicyTest {
    @Test public void pickerOffersMusicXmlMxlAndJsonWrappers() throws Exception {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            var method = MainActivity.class.getDeclaredMethod("importScore");
            method.setAccessible(true);
            method.invoke(activity);
            Intent intent = Shadows.shadowOf(activity).getNextStartedActivity();
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.getAction());
            assertEquals(Intent.CATEGORY_OPENABLE, intent.getCategories().iterator().next());
            String[] types = intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES);
            assertNotNull(types);
            assertTrue(java.util.Arrays.asList(types).contains("application/vnd.recordare.musicxml+xml"));
            assertTrue(java.util.Arrays.asList(types).contains("application/xml"));
            assertTrue(java.util.Arrays.asList(types).contains("application/zip"));
            assertTrue(java.util.Arrays.asList(types).contains("application/json"));
            assertTrue(java.util.Arrays.asList(types).contains("audio/midi"));
            assertTrue(java.util.Arrays.asList(types).contains("audio/x-midi"));
        }
    }
}
