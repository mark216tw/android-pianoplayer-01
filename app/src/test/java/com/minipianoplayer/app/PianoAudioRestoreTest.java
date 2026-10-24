package com.minipianoplayer.app;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.lang.reflect.Field;
import java.util.Queue;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
public class PianoAudioRestoreTest {
    @SuppressWarnings("unchecked") private void commands(PianoAudio audio) throws Exception {
        Field field = PianoAudio.class.getDeclaredField("commands"); field.setAccessible(true);
        Queue<Runnable> queue = (Queue<Runnable>) field.get(audio); Runnable command;
        while ((command = queue.poll()) != null) command.run();
    }
    private Score scoreWithRecentLongNote() {
        Score score = new Score();
        score.bpm = 120;
        score.partNames.put("P1", "Piano");
        long tick = 0;
        for (int i = 0; i < 2000; i++) {
            score.notes.add(new Score.Note(60 + (i % 12), i * 480L, 240, 90));
            tick = (i + 1) * 480L;
        }
        long start = tick - 4800;
        score.notes.add(new Score.Note(48, start, 9600, 80));
        score.endTick = tick + 4800;
        score.sort();
        return score;
    }
    private Score.Note longNote(Score score) {
        for (Score.Note note : score.notes) if (note.duration == 9600) return note;
        throw new AssertionError("缺少長音符");
    }
    private int longNoteId(Score score) {
        for (int id = 0; id < score.notes.size(); id++) if (score.notes.get(id).duration == 9600) return id;
        throw new AssertionError("缺少長音符");
    }
    private Object[] voices(PianoAudio audio) throws Exception {
        Field field = PianoAudio.class.getDeclaredField("voices"); field.setAccessible(true);
        return (Object[]) field.get(audio);
    }
    private int onlyActiveVoiceId(Object[] voices) throws Exception {
        Class<?> type = voices.getClass().getComponentType();
        Field active = type.getDeclaredField("active"); active.setAccessible(true);
        Field id = type.getDeclaredField("id"); id.setAccessible(true);
        int count = 0, value = -1;
        for (Object voice : voices) if (active.getBoolean(voice)) { count++; value = id.getInt(voice); }
        assertEquals(1, count);
        return value;
    }
    @Test public void seekRestoresOnlyRecentlyHeldNotes() throws Exception {
        PlaybackController playback = PlaybackController.acquire(RuntimeEnvironment.getApplication());
        try {
            Score score = scoreWithRecentLongNote();
            long target = longNote(score).start + 2640;
            playback.audio.close();
            playback.load("restore", score); commands(playback.audio);
            playback.audio.playing = true;
            playback.audio.seek(target); commands(playback.audio);
            assertEquals(longNoteId(score), onlyActiveVoiceId(voices(playback.audio)));
        } finally { playback.release(); }
    }
    @Test public void resumeRestoresRecentlyHeldNotes() throws Exception {
        PlaybackController playback = PlaybackController.acquire(RuntimeEnvironment.getApplication());
        try {
            Score score = scoreWithRecentLongNote();
            long target = longNote(score).start + 2640;
            playback.audio.close();
            playback.load("resume", score); commands(playback.audio);
            playback.audio.playing = true;
            playback.audio.seek(target); commands(playback.audio);
            playback.audio.pause(); commands(playback.audio);
            assertFalse(playback.audio.playing);
            playback.audio.play(); commands(playback.audio);
            assertTrue(playback.audio.playing);
            assertEquals(longNoteId(score), onlyActiveVoiceId(voices(playback.audio)));
        } finally { playback.release(); }
    }
}
