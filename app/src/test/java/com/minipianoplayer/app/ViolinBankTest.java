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
import java.security.MessageDigest;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
public class ViolinBankTest {
    private JSONObject manifest() throws Exception {
        try (InputStream input = RuntimeEnvironment.getApplication().getAssets().open("violin/instrument.json")) {
            return new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test public void bundledBankCoversAll88KeysWithTwoLayers() throws Exception {
        JSONObject manifest = manifest();
        assertEquals(2, manifest.getInt("version"));
        assertEquals(2, manifest.getInt("velocityLayers"));
        assertEquals(0.05, manifest.getDouble("releaseSeconds"), 0.0001);
        assertEquals(1.0, manifest.getDouble("outputGain"), 0.0001);
        assertFalse(manifest.getBoolean("sustainPedal"));
        assertEquals(55, manifest.getInt("naturalLowMidi"));
        assertEquals(96, manifest.getInt("naturalHighMidi"));
        assertEquals(21, manifest.getInt("coverageLowMidi"));
        assertEquals(108, manifest.getInt("coverageHighMidi"));
        assertEquals(84, manifest.getInt("toneStartMidi"));
        assertEquals(108, manifest.getInt("toneEndMidi"));
        assertEquals(16000, manifest.getDouble("toneStartHz"), 0.1);
        assertEquals(5500, manifest.getDouble("toneEndHz"), 0.1);
        assertEquals("CC0 1.0", manifest.getString("license"));
        JSONArray samples = manifest.getJSONArray("samples");
        assertEquals(30, samples.length());
        JSONObject layers = new JSONObject();
        for (int i = 0; i < samples.length(); i++) {
            JSONObject entry = samples.getJSONObject(i);
            String layer = entry.getString("layer");
            JSONArray covered = layers.optJSONArray(layer);
            if (covered == null) { covered = new JSONArray(); layers.put(layer, covered); }
            for (int pitch = entry.getInt("lowMidi"); pitch <= entry.getInt("highMidi"); pitch++) covered.put(pitch);
        }
        assertEquals(2, layers.length());
        var keys = layers.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            var covered = layers.getJSONArray(key);
            assertEquals(88, covered.length());
            for (int pitch = 21; pitch <= 108; pitch++) assertTrue(key, contains(covered, pitch));
        }
        assertTrue(manifest.getBoolean("gainCalibrated"));
    }

    @Test public void bundledFilesMatchManifestHashesAndLicense() throws Exception {
        JSONObject manifest = manifest();
        verify(manifest.getJSONArray("samples"));
        try (InputStream input = RuntimeEnvironment.getApplication().getAssets().open("violin/ATTRIBUTION.txt")) {
            String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(text.contains("VSCO 2 Community Edition"));
            assertTrue(text.contains("CC0 1.0"));
            assertTrue(text.contains(manifest.getString("revision")));
        }
        try (InputStream input = RuntimeEnvironment.getApplication().getAssets().open("violin/LICENSE.txt")) {
            String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(text.contains("CC0 1.0"));
            assertTrue(text.contains("Attribution 3.0"));
        }
    }

    @Test public void bundledBankLoadsWithViolinReleaseAndPedalSettings() throws Exception {
        var assets = RuntimeEnvironment.getApplication().getAssets();
        SampleBank bank = SampleBank.load(
            name -> assets.open("violin/" + name),
            (name, root) -> PianoSample.read(assets.open("violin/" + name), root),
            (count, total) -> {});
        assertEquals(2, bank.layers());
        assertEquals(0.05, bank.releaseSeconds(), 0.0001);
        assertEquals(1.0, bank.outputGain(), 0.0001);
        assertFalse(bank.sustainPedal());
        assertEquals(55, bank.naturalLow());
        assertEquals(96, bank.naturalHigh());
        assertEquals(21, bank.coverageLow());
        assertEquals(108, bank.coverageHigh());
        assertEquals(0, bank.toneHz(84), 0);
        assertEquals(5500, bank.toneHz(108), 0.1);
        assertTrue(bank.toneHz(96) > 5500 && bank.toneHz(96) < 16000);
        assertEquals(15, bank.sampleCount);
        for (int layer = 0; layer < bank.layers(); layer++) {
            for (int pitch = 21; pitch <= 108; pitch++) assertNotNull(layer + "/" + pitch, bank.sample(pitch, layer));
        }
    }

    @Test public void calibrationMetadataAndGainLimitsArePresent() throws Exception {
        JSONObject manifest = manifest();
        JSONObject calibration = manifest.getJSONObject("gainCalibration");
        assertEquals("bs1770-attack70-sustain30", calibration.getString("method"));
        JSONArray samples = manifest.getJSONArray("samples");
        for (int i = 0; i < samples.length(); i++) {
            JSONObject entry = samples.getJSONObject(i);
            double gain = entry.getDouble("gain");
            assertTrue(entry.getString("file"), gain > 0 && gain <= calibration.getDouble("maxGain"));
            assertTrue(entry.getString("file"), entry.getInt("rootMidi") >= entry.getInt("lowMidi"));
            assertTrue(entry.getString("file"), entry.getInt("rootMidi") <= entry.getInt("highMidi"));
        }
    }

    private void verify(JSONArray entries) throws Exception {
        var assets = RuntimeEnvironment.getApplication().getAssets();
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.getJSONObject(i); byte[] bytes;
            try (InputStream input = assets.open("violin/" + entry.getString("file"))) { bytes = input.readAllBytes(); }
            StringBuilder digest = new StringBuilder();
            for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) digest.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            assertEquals(entry.getString("sha256"), digest.toString());
        }
    }

    private static boolean contains(JSONArray array, int value) throws Exception {
        for (int i = 0; i < array.length(); i++) if (array.getInt(i) == value) return true;
        return false;
    }
}
