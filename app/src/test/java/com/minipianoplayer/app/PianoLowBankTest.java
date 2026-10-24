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
public class PianoLowBankTest {
    private JSONObject manifest() throws Exception {
        try (InputStream input = RuntimeEnvironment.getApplication().getAssets().open("piano-low/instrument.json")) {
            return new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test public void lowBankCoversOnlyThePianoBassRange() throws Exception {
        JSONObject manifest = manifest();
        assertEquals(3, manifest.getInt("velocityLayers"));
        assertEquals(21, manifest.getInt("coverageLowMidi"));
        assertEquals(54, manifest.getInt("coverageHighMidi"));
        assertEquals(0, manifest.getDouble("releaseSeconds"), 0.0001);
        assertTrue(manifest.getBoolean("sustainPedal"));
        JSONArray samples = manifest.getJSONArray("samples");
        assertEquals(36, samples.length());
        JSONObject layers = new JSONObject();
        for (int i = 0; i < samples.length(); i++) {
            JSONObject entry = samples.getJSONObject(i);
            String layer = entry.getString("layer");
            JSONArray covered = layers.optJSONArray(layer);
            if (covered == null) { covered = new JSONArray(); layers.put(layer, covered); }
            for (int pitch = entry.getInt("lowMidi"); pitch <= entry.getInt("highMidi"); pitch++) covered.put(pitch);
        }
        assertEquals(3, layers.length());
        var keys = layers.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            var covered = layers.getJSONArray(key);
            assertEquals(34, covered.length());
            for (int pitch = 21; pitch <= 54; pitch++) assertTrue(key, contains(covered, pitch));
        }
    }

    @Test public void lowBankReusesPianoFilesAndLicenses() throws Exception {
        JSONObject manifest = manifest();
        var assets = RuntimeEnvironment.getApplication().getAssets();
        JSONArray entries = manifest.getJSONArray("samples");
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.getJSONObject(i); byte[] bytes;
            try (InputStream input = assets.open("piano/" + entry.getString("file"))) { bytes = input.readAllBytes(); }
            StringBuilder digest = new StringBuilder();
            for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) digest.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            assertEquals(entry.getString("sha256"), digest.toString());
        }
        try (InputStream input = assets.open("piano-low/ATTRIBUTION.txt")) {
            assertTrue(new String(input.readAllBytes(), StandardCharsets.UTF_8).contains("Alexander Holm"));
        }
        try (InputStream input = assets.open("piano-low/LICENSE.txt")) {
            assertTrue(new String(input.readAllBytes(), StandardCharsets.UTF_8).contains("Attribution 3.0"));
        }
    }

    @Test public void lowBankLoadsUsingSharedPianoSamples() throws Exception {
        var assets = RuntimeEnvironment.getApplication().getAssets();
        SampleBank bank = SampleBank.load(
            name -> assets.open(name.equals("instrument.json") ? "piano-low/instrument.json" : "piano/" + name),
            (name, root) -> PianoSample.read(assets.open("piano/" + name), root),
            (count, total) -> {});
        assertEquals(3, bank.layers());
        assertEquals(0, bank.releaseSeconds(), 0);
        assertTrue(bank.sustainPedal());
        assertEquals(21, bank.coverageLow());
        assertEquals(54, bank.coverageHigh());
        assertNotNull(bank.sample(21, 0));
        assertNotNull(bank.sample(54, 2));
        assertNull(bank.sample(55, 0));
    }

    private static boolean contains(JSONArray array, int value) throws Exception {
        for (int i = 0; i < array.length(); i++) if (array.getInt(i) == value) return true;
        return false;
    }
}
