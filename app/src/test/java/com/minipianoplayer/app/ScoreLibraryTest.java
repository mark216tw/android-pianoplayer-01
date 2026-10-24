package com.minipianoplayer.app;

import android.content.Context;
import org.json.JSONObject;
import org.junit.Test;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public class ScoreLibraryTest {
    @Test public void independentScoresRenameUpdateAndDeletePersist() throws Exception {
        Context context = RuntimeEnvironment.getApplication(); ScoreLibrary library = new ScoreLibrary(context);
        Score first = TestScores.simple(); first.title = "第一首";
        Score second = first.copy(); second.title = "第二首";
        String a = library.add(first, "匯入"), b = library.add(second, "匯入");
        assertNotEquals(a, b); assertEquals(2, library.list().size());
        library.rename(a, "新名稱");
        library.update(a, first); // A queued playback-setting update must not undo a rename.
        ScoreLibrary reopened = new ScoreLibrary(context);
        assertEquals("新名稱", reopened.load(a).title);
        assertEquals("第二首", reopened.load(b).title);
        assertEquals(b, reopened.selected());
        reopened.delete(b); assertNull(reopened.selected());
        assertEquals(1, reopened.list().size()); assertEquals(first.notes.size(), reopened.load(a).notes.size());
    }
    @Test public void metadataIncludesComposerInstrumentsAndBackfillsOldEntries() throws Exception {
        Context context = RuntimeEnvironment.getApplication(); ScoreLibrary library = new ScoreLibrary(context);
        Score score = TestScores.simple(); score.title = "測試曲"; score.composer = "Antonio Vivaldi";
        score.partInstruments.put("P1", "鋼琴");
        String id = library.add(score, "匯入");
        ScoreLibrary.Entry entry = library.list().stream().filter(item -> id.equals(item.id)).findFirst().orElseThrow();
        assertEquals("Antonio Vivaldi", entry.composer);
        assertEquals("鋼琴", entry.instruments);
        JSONObject old = new JSONObject().put("title", score.title).put("source", "匯入")
            .put("created", System.currentTimeMillis()).put("updated", System.currentTimeMillis()).put("seconds", score.seconds(score.length()));
        try (FileOutputStream output = new FileOutputStream(new File(new File(context.getFilesDir(), "scores"), id + ".meta"))) {
            output.write(old.toString().getBytes(StandardCharsets.UTF_8));
        }
        ScoreLibrary.Entry backfilled = library.list().stream().filter(item -> id.equals(item.id)).findFirst().orElseThrow();
        assertEquals("Antonio Vivaldi", backfilled.composer);
        assertEquals("鋼琴", backfilled.instruments);
    }
    @Test public void migrationIsRepeatableAndRetainsOriginal() throws Exception {
        Context context = RuntimeEnvironment.getApplication(); Score old = TestScores.simple(); old.title = "舊演奏";
        new ScoreStorage(context).save(old);
        ScoreLibrary library = new ScoreLibrary(context); library.migrate();
        context.getSharedPreferences("scoreLibrary", Context.MODE_PRIVATE).edit().putBoolean("migrated", false).commit();
        new ScoreLibrary(context).migrate();
        assertEquals(1, library.list().size()); assertEquals("legacy", library.selected());
        assertEquals("舊演奏", library.load("legacy").title); assertNotNull(new ScoreStorage(context).load());
    }
    @Test public void invalidNamesDoNotChangeStoredTitle() throws Exception {
        ScoreLibrary library = new ScoreLibrary(RuntimeEnvironment.getApplication()); Score score = TestScores.simple();
        String id = library.add(score, "匯入");
        try { library.rename(id, "   "); fail("Expected invalid name"); } catch (IllegalArgumentException expected) { }
        assertEquals(score.title, library.load(id).title);
    }
}
