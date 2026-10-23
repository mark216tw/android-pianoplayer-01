package com.minipianoplayer.app;

import android.os.Looper;
import android.widget.Button;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import java.io.File;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Queue;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public class ScoreLibraryPlaybackTest {
    @Before public void reset() {
        var context = RuntimeEnvironment.getApplication();
        context.deleteFile("performance.json");
        UiTheme.preferences(context).edit().clear().commit();
        File directory = new File(context.getFilesDir(), "scores");
        File[] files = directory.listFiles();
        if (files != null) for (File file : files) file.delete();
        context.getSharedPreferences("scoreLibrary", android.content.Context.MODE_PRIVATE).edit().clear().commit();
    }
    @SuppressWarnings("unchecked") private void commands(PianoAudio audio) throws Exception {
        Field field = PianoAudio.class.getDeclaredField("commands"); field.setAccessible(true);
        Queue<Runnable> queue = (Queue<Runnable>) field.get(audio);
        Runnable command; while ((command = queue.poll()) != null) command.run();
    }
    @Test public void playingLibraryTrackShowsPauseAndStaysInLibrary() throws Exception {
        var context = RuntimeEnvironment.getApplication();
        PlaybackController playback = PlaybackController.acquire(context);
        try {
            playback.audio.close();
            ScoreLibrary library = new ScoreLibrary(context);
            String id = library.add(TestScores.simple(), "匯入");
            library.select(id);
            playback.load(id, TestScores.simple());
            commands(playback.audio);
            playback.audio.playing = true;
            try (var controller = Robolectric.buildActivity(ScoreLibraryActivity.class).setup()) {
                ScoreLibraryActivity activity = controller.get();
                Button play = null;
                long deadline = System.nanoTime() + 5_000_000_000L;
                while (System.nanoTime() < deadline) {
                    ShadowIdle.idle();
                    play = activity.findViewById(android.R.id.content).findViewWithTag("play_" + id);
                    if (play != null) break;
                    Thread.sleep(10);
                }
                assertNotNull(play);
                assertEquals("❚❚ 暫停", play.getText().toString());
                play.performClick();
                commands(playback.audio);
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600));
                assertFalse(playback.audio.playing);
                assertFalse(activity.isFinishing());
                assertEquals("▶ 播放", play.getText().toString());
            }
        } finally { playback.release(); }
    }
    private static final class ShadowIdle {
        static void idle() { Shadows.shadowOf(Looper.getMainLooper()).idle(); }
    }
}
