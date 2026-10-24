package com.minipianoplayer.app;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MusicXmlTest {
    private Score read(String xml) throws Exception { return MusicXml.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))); }
    private String wrap(String measures) { return "<score-partwise version=\"4.0\"><part id=\"P1\">" + measures + "</part></score-partwise>"; }
    private String note(String extra, String step, int duration) {
        return "<note>" + extra + "<pitch><step>" + step + "</step><octave>4</octave></pitch><duration>" + duration + "</duration><voice>1</voice></note>";
    }
    @Test public void chordBackupAndForwardKeepIndependentTimelines() throws Exception {
        Score s = read(wrap("<measure number=\"1\"><attributes><divisions>2</divisions></attributes>" +
            note("", "C", 2) + note("<chord/>", "E", 2) +
            "<backup><duration>2</duration></backup><forward><duration>1</duration></forward>" + note("", "G", 1) + "</measure>"));
        assertEquals(3, s.notes.size()); assertEquals(0, s.notes.get(0).start); assertEquals(0, s.notes.get(1).start);
        assertEquals(240, s.notes.get(2).start); assertEquals(240, s.notes.get(2).duration);
    }
    @Test public void tiesMergeAcrossMeasuresAndPartsRemainConcurrent() throws Exception {
        String c1 = "<note><pitch><step>C</step><octave>4</octave></pitch><duration>4</duration><tie type=\"start\"/></note>";
        String c2 = c1.replace("start", "stop");
        Score s = read("<score-partwise><part id=\"P1\"><measure>" + c1 + "</measure><measure>" + c2 + "</measure></part>" +
            "<part id=\"P2\"><measure>" + note("", "G", 1) + "</measure></part></score-partwise>");
        assertEquals(2, s.notes.size()); assertEquals(3840, s.notes.get(0).duration); assertEquals(0, s.notes.get(1).start);
    }
    @Test public void composerIsReadFromIdentification() throws Exception {
        Score s = read("<score-partwise><identification><creator type=\"composer\">Antonio Vivaldi</creator>"
            + "<creator type=\"composer\">Second Composer</creator><creator type=\"lyricist\">Lyricist</creator></identification>"
            + "<part id=\"P1\"><measure>" + note("", "C", 1) + "</measure></part></score-partwise>");
        assertEquals("Antonio Vivaldi、Second Composer", s.composer);
    }
    @Test public void pickupGraceRestAndDottedMetronomeAreHandled() throws Exception {
        Score s = read(wrap("<measure implicit=\"yes\"><attributes><divisions>1</divisions></attributes>" +
            "<direction><direction-type><metronome><beat-unit>quarter</beat-unit><beat-unit-dot/><per-minute>80</per-minute></metronome></direction-type></direction>" +
            "<note><grace/><pitch><step>D</step><octave>4</octave></pitch></note>" + note("", "C", 1) + "</measure>" +
            "<measure><note><rest/><duration>1</duration></note>" + note("", "E", 1) + "</measure>"));
        assertEquals(120, s.bpm); assertEquals(2, s.notes.size()); assertEquals(960, s.notes.get(1).start);
        assertTrue(s.warnings.contains("裝飾音暫略過"));
    }
    @Test public void compressedMxlUsesContainerRootRatherThanFirstXml() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            put(zip, "unrelated.xml", "<wrong/>");
            put(zip, "META-INF/container.xml", "<container><rootfiles><rootfile full-path=\"scores/main.musicxml\"/></rootfiles></container>");
            put(zip, "scores/main.musicxml", wrap("<measure>" + note("", "C", 1) + "</measure>"));
        }
        Score s = MusicXml.read(new ByteArrayInputStream(output.toByteArray())); assertEquals(60, s.notes.get(0).pitch);
    }
    private void put(ZipOutputStream zip, String name, String text) throws Exception {
        zip.putNextEntry(new ZipEntry(name)); zip.write(text.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
    }
    @Test public void standardDoctypeDoesNotRequireNetwork() throws Exception {
        Score s = read("<!DOCTYPE score-partwise PUBLIC \"-//Recordare//DTD MusicXML 4.0 Partwise//EN\" \"https://invalid.example/partwise.dtd\">" + wrap("<measure>" + note("", "C", 1) + "</measure>"));
        assertEquals(1, s.notes.size());
    }
    @Test public void entitiesAndInvalidVoicePositionAreRejected() throws Exception {
        assertThrows(Exception.class, () -> read("<!DOCTYPE score-partwise [<!ENTITY x SYSTEM 'file:///secret'>]>" + wrap("<measure>" + note("", "C", 1) + "</measure>")));
        assertThrows(Exception.class, () -> read(wrap("<measure><backup><duration>1</duration></backup>" + note("", "C", 1) + "</measure>")));
    }
    @Test public void nonPartwiseDocumentIsRejected() {
        assertThrows(Exception.class, () -> read("<score-timewise><part id=\"P1\"/></score-timewise>"));
    }
}
