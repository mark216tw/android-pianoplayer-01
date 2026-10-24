package com.minipianoplayer.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowContentResolver;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public class ScoreLibraryImportTest {
    @Before public void reset() {
        var context = RuntimeEnvironment.getApplication();
        context.deleteFile("performance.json");
        UiTheme.preferences(context).edit().clear().commit();
        File directory = new File(context.getFilesDir(), "scores");
        File[] files = directory.listFiles();
        if (files != null) for (File file : files) file.delete();
        context.getSharedPreferences("scoreLibrary", android.content.Context.MODE_PRIVATE).edit().clear().commit();
    }
    @Test public void importingFromLibraryStaysOpenAndKeepsCurrentSelection() throws Exception {
        var context = RuntimeEnvironment.getApplication();
        ScoreLibrary library = new ScoreLibrary(context);
        Score existing = TestScores.simple(); existing.title = "原有";
        String existingId = library.add(existing, "匯入");
        library.select(existingId);
        Uri uri = Uri.parse("content://test/score.musicxml");
        ShadowContentResolver resolver = Shadows.shadowOf(context.getContentResolver());
        resolver.registerInputStream(uri, new ByteArrayInputStream(TestScores.smallXml().getBytes(StandardCharsets.UTF_8)));
        Field request = ScoreLibraryActivity.class.getDeclaredField("IMPORT"); request.setAccessible(true);
        int importRequest = request.getInt(null);
        try (var controller = Robolectric.buildActivity(ScoreLibraryActivity.class).setup()) {
            ScoreLibraryActivity activity = controller.get();
            activity.onActivityResult(importRequest, Activity.RESULT_OK, new Intent().setData(uri));
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (ShadowAlertDialog.getLatestAlertDialog() == null && System.nanoTime() < deadline) {
                Shadows.shadowOf(Looper.getMainLooper()).idle();
                Thread.sleep(10);
            }
            var summary = ShadowAlertDialog.getLatestAlertDialog();
            assertNotNull(summary);
            assertEquals(2, library.list().size());
            assertFalse(activity.isFinishing());
            assertEquals(existingId, library.selected());
            summary.dismiss();
        }
    }
}
