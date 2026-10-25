package com.minipianoplayer.app;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class PianoAudioVoiceStealingTest {
    private Object[] voices(PianoAudio audio) throws Exception {
        Field field = PianoAudio.class.getDeclaredField("voices"); field.setAccessible(true);
        return (Object[]) field.get(audio);
    }
    private Object allocate(PianoAudio audio) throws Exception {
        Method method = PianoAudio.class.getDeclaredMethod("allocate"); method.setAccessible(true);
        return method.invoke(audio);
    }
    private void set(Object voice, Class<?> type, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(voice, value);
    }
    @Test public void quietestReleasedVoiceIsStealVictimWhenAllSlotsAreBusy() throws Exception {
        PianoAudio audio = new PianoAudio(RuntimeEnvironment.getApplication());
        audio.close();
        Object[] voices = voices(audio); Class<?> type = voices.getClass().getComponentType();
        for (int i = 0; i < voices.length; i++) {
            set(voices[i], type, "active", true); set(voices[i], type, "held", true);
            set(voices[i], type, "sampled", false); set(voices[i], type, "level", 1.0);
            set(voices[i], type, "started", (long) i);
        }
        set(voices[5], type, "held", false); set(voices[5], type, "level", .01);
        assertSame(voices[5], allocate(audio));
    }
    @Test public void fadingVictimUsesReservedFreeVoiceWhileSlotsRemain() throws Exception {
        PianoAudio audio = new PianoAudio(RuntimeEnvironment.getApplication());
        audio.close();
        Object[] voices = voices(audio); Class<?> type = voices.getClass().getComponentType();
        for (int i = 0; i < 64; i++) {
            set(voices[i], type, "active", true); set(voices[i], type, "held", true);
            set(voices[i], type, "sampled", false); set(voices[i], type, "level", 1.0);
            set(voices[i], type, "started", (long) i);
        }
        set(voices[3], type, "held", false); set(voices[3], type, "level", .01);
        assertSame(voices[64], allocate(audio));
        Field held = type.getDeclaredField("held"); held.setAccessible(true);
        assertFalse(held.getBoolean(voices[3]));
    }
    @Test public void outputRateFallsBackForUnsupportedDeviceValues() {
        assertEquals(44100, PianoAudio.outputRate(44100));
        assertEquals(48000, PianoAudio.outputRate(0));
        assertEquals(48000, PianoAudio.outputRate(192000));
    }
}
