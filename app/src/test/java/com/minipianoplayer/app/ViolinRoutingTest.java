package com.minipianoplayer.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class ViolinRoutingTest {
    @Test public void lowNotesUsePianoFallback() {
        assertEquals(ViolinRouting.LOW_PIANO, ViolinRouting.route(21, 2));
        assertEquals(ViolinRouting.LOW_PIANO, ViolinRouting.route(54, .1));
    }
    @Test public void shortMiddleNotesUseSpiccato() {
        assertEquals(ViolinRouting.SPIC, ViolinRouting.route(55, .1));
        assertEquals(ViolinRouting.SPIC, ViolinRouting.route(84, ViolinRouting.SPIC_SECONDS - .001));
    }
    @Test public void longOrVeryHighNotesUseArco() {
        assertEquals(ViolinRouting.ARCO, ViolinRouting.route(55, ViolinRouting.SPIC_SECONDS));
        assertEquals(ViolinRouting.ARCO, ViolinRouting.route(84, .8));
        assertEquals(ViolinRouting.SPIC, ViolinRouting.route(85, .1));
        assertEquals(ViolinRouting.SPIC, ViolinRouting.route(96, .1));
        assertEquals(ViolinRouting.ARCO, ViolinRouting.route(97, .1));
        assertEquals(ViolinRouting.ARCO, ViolinRouting.route(108, .1));
    }
    @Test public void lowStringPartsCanUseTransposedStringSamples() {
        assertEquals(ViolinRouting.LOW_PIANO, ViolinRouting.route(47, .1, false));
        assertEquals(ViolinRouting.SPIC, ViolinRouting.route(47, .1, true));
        assertEquals(ViolinRouting.ARCO, ViolinRouting.route(47, .8, true));
    }
}
