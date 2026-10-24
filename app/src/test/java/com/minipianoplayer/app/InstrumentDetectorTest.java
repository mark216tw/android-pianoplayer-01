package com.minipianoplayer.app;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class InstrumentDetectorTest {
    @Test public void namesSelectViolinOrPiano() {
        assertEquals(PianoSound.VIOLIN, InstrumentDetector.detect("Violin I", "Vln.", List.of(), null).sound);
        assertEquals(PianoSound.VIOLIN, InstrumentDetector.detect("Solo 小提琴", "", List.of(), null).sound);
        assertEquals(PianoSound.SALAMANDER, InstrumentDetector.detect("Piano", "Pno.", List.of(), null).sound);
        assertEquals(PianoSound.SALAMANDER, InstrumentDetector.detect("平台鋼琴", "", List.of(), null).sound);
    }
    @Test public void midiProgramIsUsedWhenNamesAreAmbiguous() {
        assertEquals(PianoSound.VIOLIN, InstrumentDetector.detect("", "", List.of(), 41).sound);
        assertEquals(PianoSound.SALAMANDER, InstrumentDetector.detect("", "", List.of(), 1).sound);
    }
    @Test public void namesTakePrecedenceOverProgram() {
        assertEquals(PianoSound.SALAMANDER, InstrumentDetector.detect("Piano", "", List.of(), 41).sound);
    }
    @Test public void stringFamilyUsesViolinSamplesWithLowStringFlag() {
        InstrumentDetector.Result viola = InstrumentDetector.detect("Viola", "Vla.", List.of(), null);
        assertEquals(PianoSound.VIOLIN, viola.sound);
        assertEquals("中提琴", viola.label);
        assertTrue(viola.recognized);
        assertTrue(viola.lowString);
        InstrumentDetector.Result cello = InstrumentDetector.detect("Violoncello", "Vc.", List.of(), null);
        assertEquals(PianoSound.VIOLIN, cello.sound);
        assertEquals("大提琴", cello.label);
        assertTrue(cello.lowString);
        InstrumentDetector.Result bass = InstrumentDetector.detect("Contrabass", "Cb.", List.of(), null);
        assertEquals(PianoSound.VIOLIN, bass.sound);
        assertEquals("低音提琴", bass.label);
        assertTrue(bass.lowString);
        assertFalse(InstrumentDetector.detect("Violin I", "Vln.", List.of(), null).lowString);
    }
    @Test public void midiProgramsSelectStringFamily() {
        assertEquals("中提琴", InstrumentDetector.detect("", "", List.of(), 42).label);
        assertEquals("大提琴", InstrumentDetector.detect("", "", List.of(), 43).label);
        assertEquals("低音提琴", InstrumentDetector.detect("", "", List.of(), 44).label);
        assertTrue(InstrumentDetector.detect("", "", List.of(), 43).lowString);
    }
    @Test public void unsupportedInstrumentsFallBackToPianoWithWarningFlag() {
        InstrumentDetector.Result result = InstrumentDetector.detect("Flute", "", List.of("Flute"), null);
        assertEquals(PianoSound.SALAMANDER, result.sound);
        assertFalse(result.recognized);
        assertEquals("鋼琴（未辨識）", result.label);
    }
}
