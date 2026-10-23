package com.minipianoplayer.app;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class MidiTest {
    private Score read(byte[] data) throws Exception { return Midi.read(new ByteArrayInputStream(data)); }
    private byte[] midi(int format, int division, byte[]... tracks) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("MThd".getBytes(StandardCharsets.US_ASCII));
        writeInt(out, 6); writeShort(out, format); writeShort(out, tracks.length); writeShort(out, division);
        for (byte[] track : tracks) {
            out.write("MTrk".getBytes(StandardCharsets.US_ASCII));
            writeInt(out, track.length);
            out.write(track);
        }
        return out.toByteArray();
    }
    private byte[] bytes(ByteArrayOutputStream out) { return out.toByteArray(); }
    private void meta(ByteArrayOutputStream out, long delta, int type, byte[] data) throws Exception {
        vlq(out, delta); out.write(0xFF); out.write(type); vlq(out, data.length); out.write(data);
    }
    private void event(ByteArrayOutputStream out, long delta, int... data) throws Exception {
        vlq(out, delta);
        for (int value : data) out.write(value);
    }
    private void vlq(ByteArrayOutputStream out, long value) {
        int length = 1;
        while ((value >>> (7 * length)) != 0) length++;
        for (int i = length - 1; i >= 0; i--) out.write((int) ((value >>> (7 * i)) & 0x7f) | (i == 0 ? 0 : 0x80));
    }
    private void writeShort(ByteArrayOutputStream out, int value) { out.write((value >> 8) & 0xff); out.write(value & 0xff); }
    private void writeInt(ByteArrayOutputStream out, int value) { out.write((value >> 24) & 0xff); out.write((value >> 16) & 0xff); out.write((value >> 8) & 0xff); out.write(value & 0xff); }
    private void end(ByteArrayOutputStream out) throws Exception { meta(out, 0, 0x2F, new byte[0]); }

    @Test public void formatOneMergesTracksAndConvertsPpq() throws Exception {
        ByteArrayOutputStream conductor = new ByteArrayOutputStream();
        meta(conductor, 0, 0x03, "Piano".getBytes(StandardCharsets.UTF_8));
        meta(conductor, 0, 0x51, new byte[]{0x07, (byte) 0xA1, 0x20});
        meta(conductor, 0, 0x58, new byte[]{4, 2, 24, 8});
        end(conductor);
        ByteArrayOutputStream first = new ByteArrayOutputStream();
        event(first, 0, 0x90, 60, 90); event(first, 96, 0x80, 60, 0); end(first);
        ByteArrayOutputStream second = new ByteArrayOutputStream();
        event(second, 0, 0x91, 64, 80); event(second, 192, 0x81, 64, 0); end(second);
        Score score = read(midi(1, 96, bytes(conductor), bytes(first), bytes(second)));
        assertEquals("Piano", score.title);
        assertEquals(2, score.notes.size());
        assertEquals(0, score.notes.get(0).start);
        assertEquals(480, score.notes.get(0).duration);
        assertEquals(960, score.notes.get(1).duration);
        assertEquals(120, score.bpm);
        assertEquals(4, score.beats); assertEquals(4, score.beatType);
        assertFalse(score.measures.isEmpty());
        assertTrue(score.features.contains("MIDI"));
        assertTrue(score.features.contains("多軌"));
        assertEquals(3, score.partNames.size());
    }

    @Test public void runningStatusVelocityZeroSustainAndOpenNotesAreHandled() throws Exception {
        ByteArrayOutputStream track = new ByteArrayOutputStream();
        event(track, 0, 0x90, 60, 90);
        event(track, 480, 60, 0);
        event(track, 0, 0xB0, 64, 127);
        event(track, 0, 0x90, 62, 70);
        event(track, 0, 0xB0, 64, 0);
        event(track, 480, 0x80, 62, 0);
        event(track, 0, 0x90, 65, 80);
        end(track);
        Score score = read(midi(0, 480, bytes(track)));
        assertEquals(3, score.notes.size());
        assertEquals(480, score.notes.get(0).duration);
        assertEquals(480, score.notes.get(1).duration);
        assertTrue(score.notes.get(2).duration > 0);
        assertEquals(2, score.pedals.size());
        assertTrue(score.pedals.get(0).down);
        assertFalse(score.pedals.get(1).down);
        assertTrue(score.warnings.contains("部分 MIDI 音符缺少 Note Off，已於軌道結尾關閉"));
    }

    @Test public void drumsAreIgnoredAndNonPianoProgramIsReported() throws Exception {
        ByteArrayOutputStream track = new ByteArrayOutputStream();
        event(track, 0, 0xC0, 40);
        event(track, 0, 0x90, 60, 90); event(track, 480, 0x80, 60, 0);
        event(track, 0, 0x99, 36, 100); event(track, 480, 0x89, 36, 0);
        end(track);
        Score score = read(midi(0, 480, bytes(track)));
        assertEquals(1, score.notes.size());
        assertTrue(score.warnings.contains("已忽略 MIDI 打擊樂軌道（Channel 10）"));
        assertTrue(score.warnings.contains("MIDI 含非鋼琴樂器，將以鋼琴音色播放"));
    }

    @Test public void notesOutsidePianoRangeAreSkipped() throws Exception {
        ByteArrayOutputStream track = new ByteArrayOutputStream();
        event(track, 0, 0x90, 20, 90); event(track, 480, 0x80, 20, 0);
        event(track, 0, 0x90, 60, 90); event(track, 480, 0x80, 60, 0);
        event(track, 0, 0x90, 109, 90); event(track, 480, 0x80, 109, 0);
        end(track);
        Score score = read(midi(0, 480, bytes(track)));
        assertEquals(1, score.notes.size());
        assertEquals(60, score.notes.get(0).pitch);
        assertTrue(score.warnings.contains("鋼琴音域之外的音符已略過"));
    }

    @Test public void smpteFormatTwoAndRiffAreRejected() throws Exception {
        ByteArrayOutputStream track = new ByteArrayOutputStream(); end(track);
        assertThrows(Exception.class, () -> read(midi(0, 0xE728, bytes(track))));
        assertThrows(Exception.class, () -> read(midi(2, 480, bytes(track))));
        byte[] riff = new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0};
        assertThrows(Exception.class, () -> ScoreImport.read(new ByteArrayInputStream(riff)));
    }

    @Test public void scoreImportDispatchesMidiAndKeepsMusicXmlWorking() throws Exception {
        ByteArrayOutputStream track = new ByteArrayOutputStream();
        meta(track, 0, 0x03, "MIDI Song".getBytes(StandardCharsets.UTF_8));
        event(track, 0, 0x90, 60, 90); event(track, 480, 0x80, 60, 0); end(track);
        Score midi = ScoreImport.read(new ByteArrayInputStream(midi(0, 480, bytes(track))));
        assertEquals("MIDI Song", midi.title);
        Score xml = ScoreImport.read(new ByteArrayInputStream(TestScores.smallXml().getBytes(StandardCharsets.UTF_8)));
        assertEquals("小星星", xml.title);
    }
}
