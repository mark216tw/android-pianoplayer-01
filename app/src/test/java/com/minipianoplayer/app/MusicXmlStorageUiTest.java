package com.minipianoplayer.app;

import android.os.Looper;
import android.view.View;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowAlertDialog;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.io.File;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
public class MusicXmlStorageUiTest {
    private static final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><score-partwise><work><work-title>測試 &amp; 保存</work-title></work><part-list><score-part id=\"P1\"><part-name>Piano</part-name></score-part></part-list><part id=\"P1\"><measure><attributes><divisions>1</divisions><time><beats>1</beats><beat-type>4</beat-type></time></attributes><direction><sound tempo=\"60\" dynamics=\"50\"/></direction><note><pitch><step>C</step><octave>4</octave></pitch><duration>1</duration><voice>1</voice></note><backup><duration>1</duration></backup><note><pitch><step>C</step><octave>3</octave></pitch><duration>1</duration><voice>2</voice><staff>2</staff></note></measure></part></score-partwise>";
    @Before public void reset() {
        var context = RuntimeEnvironment.getApplication();
        context.deleteFile("performance.json");
        UiTheme.preferences(context).edit().clear().commit();
        File directory = new File(context.getFilesDir(), "scores");
        File[] files = directory.listFiles();
        if (files != null) for (File file : files) file.delete();
        context.getSharedPreferences("scoreLibrary", android.content.Context.MODE_PRIVATE).edit().clear().commit();
    }
    private Score imported() throws Exception { return MusicXml.read(new ByteArrayInputStream(XML.getBytes(StandardCharsets.UTF_8))); }
    @Test public void sourceTempoVoiceAndMuteSurviveSavingAndOriginalRemainsExact() throws Exception {
        var context = RuntimeEnvironment.getApplication(); Score score = imported(); score.mutedVoices.add(score.notes.get(0).channelKey());
        new ScoreStorage(context).save(score); Score restored = new ScoreStorage(context).load();
        assertEquals(XML, restored.originalXml); assertEquals(60, restored.timeline().bpmAt(0), 0);
        assertEquals(score.notes.get(0).voice, restored.notes.get(0).voice); assertEquals(2, restored.channels().size());
        assertEquals(score.mutedVoices, restored.mutedVoices); assertEquals(score.endTick, restored.endTick); assertEquals(score.features, restored.features);
        assertEquals(score.partSounds, restored.partSounds); assertEquals(score.partInstruments, restored.partInstruments);
    }
    @Test public void legacyFourFieldDataLoadsWithDefaultVoiceAndTempo() throws Exception {
        var context = RuntimeEnvironment.getApplication();
        String legacy = "{\"title\":\"舊資料\",\"bpm\":120,\"beats\":4,\"beatType\":4,\"notes\":[[60,0,480,90]],\"pedals\":[[0,true],[480,false]]}";
        try (var output = context.openFileOutput("performance.json", 0)) { output.write(legacy.getBytes(StandardCharsets.UTF_8)); }
        Score score = new ScoreStorage(context).load(); assertEquals("舊資料", score.title); assertNull(score.originalXml);
        assertEquals(.5, score.seconds(score.length()), .00001); assertEquals("P1", score.notes.get(0).part); assertEquals(1, score.channels().size());
    }
    @Test public void legacyPartNamesInferToneAssignments() throws Exception {
        var context = RuntimeEnvironment.getApplication();
        String legacy = "{\"title\":\"舊二重奏\",\"bpm\":120,\"beats\":4,\"beatType\":4,"
            + "\"notes\":[[72,0,480,90,\"P1\",\"1\",1,1],[60,0,480,90,\"P2\",\"1\",2,2]],"
            + "\"pedals\":[],\"partNames\":{\"P1\":\"Violin\",\"P2\":\"Piano\"}}";
        try (var output = context.openFileOutput("performance.json", 0)) { output.write(legacy.getBytes(StandardCharsets.UTF_8)); }
        Score score = new ScoreStorage(context).load();
        assertEquals(PianoSound.VIOLIN, (int) score.partSounds.get("P1"));
        assertEquals(PianoSound.SALAMANDER, (int) score.partSounds.get("P2"));
    }
    @Test public void composerSurvivesSaving() throws Exception {
        var context = RuntimeEnvironment.getApplication();
        Score score = imported(); score.composer = "Antonio Vivaldi";
        new ScoreStorage(context).save(score);
        assertEquals("Antonio Vivaldi", new ScoreStorage(context).load().composer);
    }
    @Test public void lowStringPartsSurviveSaving() throws Exception {
        var context = RuntimeEnvironment.getApplication();
        Score score = imported(); score.lowStringParts.add("P1");
        new ScoreStorage(context).save(score);
        assertTrue(new ScoreStorage(context).load().lowStringParts.contains("P1"));
    }
    @Test public void legacyUnrecognizedViolaIsReDetectedAsString() throws Exception {
        var context = RuntimeEnvironment.getApplication();
        String legacy = "{\"title\":\"舊弦樂\",\"bpm\":120,\"beats\":4,\"beatType\":4,"
            + "\"notes\":[[60,0,480,90,\"P4\",\"1\",1,1]],\"pedals\":[],"
            + "\"partNames\":{\"P4\":\"Viola\"},\"partSounds\":{\"P4\":0},"
            + "\"partInstruments\":{\"P4\":\"鋼琴（未辨識）\"}}";
        try (var output = context.openFileOutput("performance.json", 0)) { output.write(legacy.getBytes(StandardCharsets.UTF_8)); }
        Score score = new ScoreStorage(context).load();
        assertEquals(PianoSound.VIOLIN, (int) score.partSounds.get("P4"));
        assertEquals("中提琴", score.partInstruments.get("P4"));
        assertTrue(score.lowStringParts.contains("P4"));
    }
    @Test public void voiceDialogMutesAndPersistsSelection() throws Exception {
        var context = RuntimeEnvironment.getApplication();
        ScoreLibrary library = new ScoreLibrary(context); String id = library.add(imported(), "匯入"); library.select(id);
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get(); View content = activity.findViewById(android.R.id.content);
            View more = content.findViewWithTag("moreMenu");
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (!more.isEnabled() && System.nanoTime() < deadline) { Shadows.shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10); }
            assertTrue(more.isEnabled()); more.performClick();
            var menu = ShadowAlertDialog.getLatestAlertDialog();
            View voicesItem = menu.getListView().getAdapter().getView(1, null, menu.getListView());
            menu.getListView().performItemClick(voicesItem, 1, 1);
            var dialog = ShadowAlertDialog.getLatestAlertDialog(); assertEquals(2, dialog.getListView().getCount());
            assertTrue(dialog.getListView().getMinimumHeight() >= Math.round(96 * dialog.getListView().getResources().getDisplayMetrics().density));
            assertFalse(dialog.getListView().isVerticalScrollBarEnabled());
            assertTrue(dialog.getListView().getLayoutParams().height >= Math.round(96 * dialog.getListView().getResources().getDisplayMetrics().density));
            View choice = dialog.getListView().getAdapter().getView(0, null, dialog.getListView());
            assertTrue(dialog.getListView().isItemChecked(0)); dialog.getListView().performItemClick(choice, 0, 0); dialog.dismiss();
            java.lang.reflect.Field scoreField = MainActivity.class.getDeclaredField("score"); scoreField.setAccessible(true);
            assertEquals(1, ((Score) scoreField.get(activity)).mutedVoices.size());
        }
    }
    @Test public void startupMigratesLegacyPerformanceFileIntoLibrary() throws Exception {
        var context = RuntimeEnvironment.getApplication();
        Score legacy = TestScores.simple(); legacy.title = "舊演奏";
        new ScoreStorage(context).save(legacy);
        context.getSharedPreferences("scoreLibrary", android.content.Context.MODE_PRIVATE).edit().clear().commit();
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            ScoreLibrary library = new ScoreLibrary(context);
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (library.selected() == null && System.nanoTime() < deadline) {
                Shadows.shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10); library = new ScoreLibrary(context);
            }
            assertEquals("legacy", library.selected());
            assertTrue(library.list().stream().anyMatch(entry -> "legacy".equals(entry.id)));
            assertEquals("舊演奏", library.load("legacy").title);
        }
    }
    @Test public void tappingTitleShowsSummaryWithoutStartingPlayback() throws Exception {
        var context = RuntimeEnvironment.getApplication();
        ScoreLibrary library = new ScoreLibrary(context); Score score = imported();
        score.title = "這是一個非常長而且需要在摘要中完整顯示的樂曲名稱";
        String id = library.add(score, "匯入"); library.select(id);
        try (var controller = Robolectric.buildActivity(ScoreLibraryActivity.class).setup()) {
            ScoreLibraryActivity activity = controller.get();
            View content = activity.findViewById(android.R.id.content);
            long deadline = System.nanoTime() + 5_000_000_000L;
            View title = null;
            while ((title = content.findViewWithTag("scoreTitle_" + id)) == null && System.nanoTime() < deadline) {
                Shadows.shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10);
            }
            assertNotNull(title); title.performClick();
            deadline = System.nanoTime() + 5_000_000_000L;
            while (ShadowAlertDialog.getLatestAlertDialog() == null && System.nanoTime() < deadline) {
                Shadows.shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10);
            }
            var summary = ShadowAlertDialog.getLatestAlertDialog();
            assertNotNull(summary); CharSequence message = Shadow.<ShadowAlertDialog>extract(summary).getMessage();
            assertTrue(message.toString().contains(score.title)); assertTrue(message.toString().contains("個音符"));
            java.lang.reflect.Field playbackField = ScoreLibraryActivity.class.getDeclaredField("playback"); playbackField.setAccessible(true);
            PlaybackController playback = (PlaybackController) playbackField.get(activity);
            assertFalse(playback.audio.playing);
            summary.dismiss();
            View card = content.findViewWithTag("scoreCard_" + id);
            assertNotNull(card); card.performClick();
            assertFalse(playback.audio.playing);
        }
    }
    @Test public void libraryUpdatePersistsMutedVoices() throws Exception {
        var context = RuntimeEnvironment.getApplication();
        ScoreLibrary library = new ScoreLibrary(context);
        Score score = imported();
        score.mutedVoices.add(score.notes.get(0).channelKey());
        String id = library.add(score, "匯入");
        library.update(id, score);
        assertEquals(1, library.load(id).mutedVoices.size());
    }
}
