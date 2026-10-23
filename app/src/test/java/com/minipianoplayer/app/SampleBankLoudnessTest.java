package com.minipianoplayer.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
public class SampleBankLoudnessTest {
    private static final int[] ROOTS = {48, 60, 72, 84, 87, 96, 105, 108};

    private JSONObject manifest() throws Exception {
        try (InputStream input = RuntimeEnvironment.getApplication().getAssets().open("piano/instrument.json")) {
            return new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private JSONObject entry(JSONObject manifest, int root, String layer) throws Exception {
        JSONArray samples = manifest.getJSONArray("samples");
        for (int i = 0; i < samples.length(); i++) {
            JSONObject entry = samples.getJSONObject(i);
            if (entry.getInt("rootMidi") == root && entry.getString("layer").equals(layer)) return entry;
        }
        throw new AssertionError("missing sample: " + root + " " + layer);
    }

    private double level(JSONObject entry) throws Exception {
        try (InputStream input = RuntimeEnvironment.getApplication().getAssets().open("piano/" + entry.getString("file"))) {
            PianoSample sample = PianoSample.read(input, entry.getInt("rootMidi"));
            int frames = Math.min(sample.frames, Math.max(1, (int) (sample.rate * .1)));
            double sum = 0;
            int count = frames * sample.channels;
            for (int i = 0; i < count; i++) {
                double value = sample.pcm[i] * (double) PianoSample.SCALE;
                sum += value * value;
            }
            return Math.sqrt(sum / count) * entry.getDouble("gain");
        }
    }

    private static double db(double ratio) { return 20 * Math.log10(ratio); }

    @Test public void velocityLayersAreLevelMatchedAtRepresentativeRoots() throws Exception {
        JSONObject manifest = manifest();
        for (int root : ROOTS) {
            double soft = level(entry(manifest, root, "soft"));
            double medium = level(entry(manifest, root, "medium"));
            double loud = level(entry(manifest, root, "loud"));
            assertTrue("soft/medium " + root, Math.abs(db(medium / soft)) < 3);
            assertTrue("medium/loud " + root, Math.abs(db(loud / medium)) < 3);
        }
    }

    @Test public void accentedHighNoteDoesNotJumpAcrossVelocityLayers() throws Exception {
        JSONObject manifest = manifest();
        for (int root : new int[]{84, 87, 105, 108}) {
            double medium = level(entry(manifest, root, "medium"));
            double loud = level(entry(manifest, root, "loud"));
            double beforeAccent = medium * Math.pow(90 / 127.0, .85);
            double afterAccent = loud * Math.pow(102 / 127.0, .85);
            assertTrue("accent " + root, Math.abs(db(afterAccent / beforeAccent)) < 3);
        }
    }

    @Test public void calibrationMetadataAndGainLimitsArePresent() throws Exception {
        JSONObject manifest = manifest();
        JSONObject calibration = manifest.getJSONObject("gainCalibration");
        assertEquals("bs1770-attack70-sustain30", calibration.getString("method"));
        assertEquals(0.05, calibration.getDouble("attackSeconds"), 0.0001);
        assertEquals(0.25, calibration.getDouble("sustainSeconds"), 0.0001);
        JSONArray samples = manifest.getJSONArray("samples");
        for (int i = 0; i < samples.length(); i++) {
            double gain = samples.getJSONObject(i).getDouble("gain");
            assertTrue(gain > 0 && gain <= calibration.getDouble("maxGain"));
        }
    }
}
