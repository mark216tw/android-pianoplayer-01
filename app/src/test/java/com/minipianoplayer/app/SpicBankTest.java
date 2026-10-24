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
public class SpicBankTest {
    private JSONObject manifest() throws Exception {
        try (InputStream input = RuntimeEnvironment.getApplication().getAssets().open("violin-spic/instrument.json")) {
            return new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test public void spicBankCoversViolinRangeWithTwoLayers() throws Exception {
        JSONObject manifest = manifest();
        assertEquals(2, manifest.getInt("velocityLayers"));
        assertEquals(0.03, manifest.getDouble("releaseSeconds"), 0.0001);
        assertEquals(1.0, manifest.getDouble("outputGain"), 0.0001);
        assertFalse(manifest.getBoolean("sustainPedal"));
        assertEquals(21, manifest.getInt("coverageLowMidi"));
        assertEquals(96, manifest.getInt("coverageHighMidi"));
        assertEquals(84, manifest.getInt("toneStartMidi"));
        assertEquals(96, manifest.getInt("toneEndMidi"));
        JSONArray samples = manifest.getJSONArray("samples");
        assertEquals(20, samples.length());
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
            assertEquals(76, covered.length());
            for (int pitch = 21; pitch <= 96; pitch++) assertTrue(key, contains(covered, pitch));
        }
    }

    @Test public void spicFilesMatchManifestHashesAndLicenses() throws Exception {
        JSONObject manifest = manifest();
        var assets = RuntimeEnvironment.getApplication().getAssets();
        JSONArray entries = manifest.getJSONArray("samples");
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.getJSONObject(i); byte[] bytes;
            try (InputStream input = assets.open("violin-spic/" + entry.getString("file"))) { bytes = input.readAllBytes(); }
            StringBuilder digest = new StringBuilder();
            for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) digest.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            assertEquals(entry.getString("sha256"), digest.toString());
        }
        try (InputStream input = assets.open("violin-spic/LICENSE.txt")) {
            String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(text.contains("CC0 1.0"));
            assertTrue(text.contains("Attribution 3.0"));
        }
    }

    @Test public void spicBankLoadsWithLimitedCoverage() throws Exception {
        var assets = RuntimeEnvironment.getApplication().getAssets();
        SampleBank bank = SampleBank.load(
            name -> assets.open("violin-spic/" + name),
            (name, root) -> PianoSample.read(assets.open("violin-spic/" + name), root),
            (count, total) -> {});
        assertEquals(2, bank.layers());
        assertEquals(0.03, bank.releaseSeconds(), 0.0001);
        assertEquals(1.0, bank.outputGain(), 0.0001);
        assertEquals(21, bank.coverageLow());
        assertEquals(96, bank.coverageHigh());
        assertEquals(10, bank.sampleCount);
        assertNotNull(bank.sample(21, 0));
        assertNotNull(bank.sample(96, 0));
        assertNull(bank.sample(20, 0));
        assertNull(bank.sample(97, 0));
    }

    private static boolean contains(JSONArray array, int value) throws Exception {
        for (int i = 0; i < array.length(); i++) if (array.getInt(i) == value) return true;
        return false;
    }
}
