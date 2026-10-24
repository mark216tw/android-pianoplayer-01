package com.minipianoplayer.app;

import android.content.Intent;
import java.io.ByteArrayInputStream;
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
        try (var controller = Robolectric.buildActivity(ScoreLibraryActivity.class).setup()) {
            ScoreLibraryActivity activity = controller.get();
            var method = ScoreLibraryActivity.class.getDeclaredMethod("pickImport");
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
            assertFalse(java.util.Arrays.asList(types).contains("audio/midi"));
            assertFalse(java.util.Arrays.asList(types).contains("audio/x-midi"));
        }
    }
    @Test public void midiFilesAreRejected() {
        byte[] midi = {'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 0, 0, 1, 0, 96};
        try {
            ScoreImport.read(new ByteArrayInputStream(midi));
            fail("Expected MIDI rejection");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().contains("MIDI"));
        }
    }
}
