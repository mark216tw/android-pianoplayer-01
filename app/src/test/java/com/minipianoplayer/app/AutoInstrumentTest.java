package com.minipianoplayer.app;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class AutoInstrumentTest {
    private static final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        + "<score-partwise><work><work-title>Duo</work-title></work>"
        + "<part-list>"
        + "<score-part id=\"P1\"><part-name>Violin</part-name><score-instrument id=\"I1\"><instrument-name>Violin</instrument-name></score-instrument></score-part>"
        + "<score-part id=\"P2\"><part-name>Piano</part-name><score-instrument id=\"I2\"><instrument-name>Piano</instrument-name></score-instrument></score-part>"
        + "</part-list>"
        + "<part id=\"P1\"><measure number=\"1\"><attributes><divisions>1</divisions><time><beats>4</beats><beat-type>4</beat-type></time></attributes>"
        + "<note><pitch><step>C</step><octave>5</octave></pitch><duration>4</duration><voice>1</voice></note></measure></part>"
        + "<part id=\"P2\"><measure number=\"1\"><attributes><divisions>1</divisions><time><beats>4</beats><beat-type>4</beat-type></time></attributes>"
        + "<note><pitch><step>C</step><octave>4</octave></pitch><duration>4</duration><voice>1</voice></note></measure></part>"
        + "</score-partwise>";

    @Test public void musicXmlAssignsEachPartToItsDetectedTone() throws Exception {
        Score score = MusicXml.read(new ByteArrayInputStream(XML.getBytes(StandardCharsets.UTF_8)));
        assertEquals(PianoSound.VIOLIN, (int) score.partSounds.get("P1"));
        assertEquals(PianoSound.SALAMANDER, (int) score.partSounds.get("P2"));
        assertEquals("小提琴", score.partInstruments.get("P1"));
        assertEquals("鋼琴", score.partInstruments.get("P2"));
        assertTrue(score.features.contains("自動音色"));
        assertTrue(score.importSummary().contains("Violin → 小提琴"));
        assertTrue(score.importSummary().contains("Piano → 鋼琴"));

        PlaybackProgram program = new PlaybackProgram(score);
        assertArrayEquals(new int[]{PianoSound.SALAMANDER, PianoSound.VIOLIN}, program.requiredSounds);
        boolean violin = false, piano = false;
        for (int sound : program.noteSounds) {
            if (sound == PianoSound.VIOLIN) violin = true;
            if (sound == PianoSound.SALAMANDER) piano = true;
        }
        assertTrue(violin && piano);
    }

    @Test public void musicXmlStringFamilyKeepsViolaAndCelloOnStringBank() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><score-partwise><work><work-title>Strings</work-title></work>"
            + "<part-list>"
            + "<score-part id=\"P1\"><part-name>Violin I</part-name></score-part>"
            + "<score-part id=\"P2\"><part-name>Viola</part-name></score-part>"
            + "<score-part id=\"P3\"><part-name>Violoncello</part-name></score-part>"
            + "</part-list>"
            + "<part id=\"P1\"><measure number=\"1\"><attributes><divisions>1</divisions><time><beats>4</beats><beat-type>4</beat-type></time></attributes>"
            + "<note><pitch><step>C</step><octave>5</octave></pitch><duration>4</duration><voice>1</voice></note></measure></part>"
            + "<part id=\"P2\"><measure number=\"1\"><attributes><divisions>1</divisions><time><beats>4</beats><beat-type>4</beat-type></time></attributes>"
            + "<note><pitch><step>C</step><octave>4</octave></pitch><duration>4</duration><voice>1</voice></note></measure></part>"
            + "<part id=\"P3\"><measure number=\"1\"><attributes><divisions>1</divisions><time><beats>4</beats><beat-type>4</beat-type></time></attributes>"
            + "<note><pitch><step>C</step><octave>3</octave></pitch><duration>4</duration><voice>1</voice></note></measure></part>"
            + "</score-partwise>";
        Score score = MusicXml.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        assertEquals(PianoSound.VIOLIN, (int) score.partSounds.get("P1"));
        assertEquals(PianoSound.VIOLIN, (int) score.partSounds.get("P2"));
        assertEquals(PianoSound.VIOLIN, (int) score.partSounds.get("P3"));
        assertEquals("中提琴", score.partInstruments.get("P2"));
        assertEquals("大提琴", score.partInstruments.get("P3"));
        assertFalse(score.lowStringParts.contains("P1"));
        assertTrue(score.lowStringParts.contains("P2"));
        assertTrue(score.lowStringParts.contains("P3"));
        assertEquals("小提琴、中提琴、大提琴", score.instrumentSummary());
        PlaybackProgram program = new PlaybackProgram(score);
        for (int id = 0; id < score.notes.size(); id++) {
            assertEquals(!score.notes.get(id).part.equals("P1"), program.noteLowStrings[id]);
        }
        assertArrayEquals(new int[]{PianoSound.VIOLIN}, program.requiredSounds);
    }
    @Test public void lowStringPartsAreCompiledIntoPlaybackProgram() {
        Score score = new Score();
        score.partNames.put("P1", "Viola");
        score.partSounds.put("P1", PianoSound.VIOLIN);
        score.lowStringParts.add("P1");
        score.notes.add(new Score.Note(47, 0, 480, 90, "P1", "1", 2));
        score.sort();
        PlaybackProgram program = new PlaybackProgram(score);
        assertTrue(program.noteLowStrings[0]);
        assertArrayEquals(new int[]{PianoSound.VIOLIN}, program.requiredSounds);
    }
    @Test public void unknownInstrumentFallsBackToPianoWithWarning() throws Exception {
        String xml = XML.replace("<part-name>Violin</part-name>", "<part-name>Flute</part-name>")
            .replace("<instrument-name>Violin</instrument-name>", "<instrument-name>Flute</instrument-name>");
        Score score = MusicXml.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        assertEquals(PianoSound.SALAMANDER, (int) score.partSounds.get("P1"));
        assertTrue(score.warnings.stream().anyMatch(message -> message.contains("無法辨識樂器")));
    }
}
