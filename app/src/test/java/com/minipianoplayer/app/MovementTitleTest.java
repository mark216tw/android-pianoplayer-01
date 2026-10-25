package com.minipianoplayer.app;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
public class MovementTitleTest {
    private static final String MOVEMENT = "第一樂章：小星星";
    private static final String WITH_BOTH = TestScores.smallXml().replace("</work>", "</work><movement-title>" + MOVEMENT + "</movement-title>");
    private static final String MOVEMENT_ONLY = TestScores.smallXml()
        .replace("<work><work-title>小星星</work-title></work>", "<movement-title>" + MOVEMENT + "</movement-title>");

    @Before public void reset() {
        Context context = RuntimeEnvironment.getApplication();
        context.deleteFile("performance.json");
        File directory = new File(context.getFilesDir(), "scores");
        File[] files = directory.listFiles();
        if (files != null) for (File file : files) file.delete();
        context.getSharedPreferences("scoreLibrary", Context.MODE_PRIVATE).edit().clear().commit();
    }

    private Score read(String xml) throws Exception {
        return MusicXml.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test public void workTitleAndMovementTitleAreReadSeparately() throws Exception {
        Score score = read(WITH_BOTH);
        assertEquals("小星星", score.title);
        assertEquals(MOVEMENT, score.movementTitle);
        assertEquals(MOVEMENT, score.movementSubtitle());
        assertTrue(score.importSummary().startsWith("小星星\n" + MOVEMENT + "\n"));
    }

    @Test public void movementTitleOnlyFallsBackWithoutDuplicatingSubtitle() throws Exception {
        Score score = read(MOVEMENT_ONLY);
        assertEquals(MOVEMENT, score.title);
        assertEquals(MOVEMENT, score.movementTitle);
        assertTrue(score.movementSubtitle().isEmpty());
        assertFalse(score.importSummary().startsWith(MOVEMENT + "\n" + MOVEMENT));
    }

    @Test public void movementTitleSurvivesStorageAndLibraryMetadata() throws Exception {
        Score score = read(WITH_BOTH);
        new ScoreStorage(RuntimeEnvironment.getApplication()).save(score);
        assertEquals(MOVEMENT, new ScoreStorage(RuntimeEnvironment.getApplication()).load().movementTitle);

        ScoreLibrary library = new ScoreLibrary(RuntimeEnvironment.getApplication());
        String id = library.add(score, "匯入");
        ScoreLibrary.Entry entry = library.list().stream().filter(item -> id.equals(item.id)).findFirst().orElseThrow();
        assertEquals(MOVEMENT, entry.movementTitle);
        assertEquals(MOVEMENT, library.load(id).movementTitle);
    }

    @Test public void movementTitleCanBeBackfilledFromOriginalXml() throws Exception {
        assertEquals(MOVEMENT, MusicXml.movementTitle(WITH_BOTH));
        Score score = read(WITH_BOTH);
        ScoreLibrary library = new ScoreLibrary(RuntimeEnvironment.getApplication());
        String id = library.add(score, "匯入");
        File meta = new File(new File(RuntimeEnvironment.getApplication().getFilesDir(), "scores"), id + ".meta");
        String oldMeta = "{\"title\":\"小星星\",\"source\":\"匯入\",\"created\":0,\"updated\":0,\"seconds\":1}";
        try (var output = new java.io.FileOutputStream(meta)) {
            output.write(oldMeta.getBytes(StandardCharsets.UTF_8));
        }
        ScoreLibrary.Entry entry = library.list().stream().filter(item -> id.equals(item.id)).findFirst().orElseThrow();
        assertEquals(MOVEMENT, entry.movementTitle);
    }

    @Test public void playerAndLibraryCardsShowMovementTitleSubtitleWithAlignedTimeAndDuration() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        ScoreLibrary library = new ScoreLibrary(context);
        String id = library.add(read(WITH_BOTH), "匯入");
        library.select(id);
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            java.lang.reflect.Field subtitleField = MainActivity.class.getDeclaredField("subtitle");
            subtitleField.setAccessible(true);
            TextView subtitle = (TextView) subtitleField.get(activity);
            await(() -> subtitle.getVisibility() == View.VISIBLE);
            assertEquals(MOVEMENT, subtitle.getText().toString());
            java.lang.reflect.Field timeField = MainActivity.class.getDeclaredField("time");
            timeField.setAccessible(true);
            assertSame(subtitle.getParent(), ((View) timeField.get(activity)).getParent());
            java.lang.reflect.Field titleField = MainActivity.class.getDeclaredField("title");
            java.lang.reflect.Field moreField = MainActivity.class.getDeclaredField("moreButton");
            titleField.setAccessible(true); moreField.setAccessible(true);
            assertSame(((View) titleField.get(activity)).getParent(), ((View) moreField.get(activity)).getParent());
        }
        try (var controller = Robolectric.buildActivity(ScoreLibraryActivity.class).setup()) {
            ScoreLibraryActivity activity = controller.get();
            View content = activity.findViewById(android.R.id.content);
            TextView subtitle = null;
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (subtitle == null && System.nanoTime() < deadline) {
                Shadows.shadowOf(Looper.getMainLooper()).idle();
                subtitle = content.findViewWithTag("scoreMovementTitle_" + id);
                if (subtitle == null) Thread.sleep(10);
            }
            assertNotNull(subtitle);
            assertEquals(MOVEMENT, subtitle.getText().toString());
            View instruments = content.findViewWithTag("scoreInstruments_" + id);
            View duration = content.findViewWithTag("scoreDuration_" + id);
            assertNotNull(instruments); assertNotNull(duration);
            assertSame(instruments.getParent(), duration.getParent());
        }
    }

    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            Thread.sleep(10);
        }
        assertTrue(condition.getAsBoolean());
    }

    private interface BooleanSupplier { boolean getAsBoolean(); }
}
