package com.minipianoplayer.app;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
public class SampleBankStoreTest {
    @Test public void syntheticSelectionIsAlwaysReady() {
        var context = RuntimeEnvironment.getApplication();
        PianoSound.select(context, PianoSound.SYNTHETIC);
        try {
            SampleBankStore.start(context, PianoSound.SYNTHETIC);
            assertTrue(SampleBankStore.ready(context, new int[]{PianoSound.SALAMANDER, PianoSound.VIOLIN}));
        } finally {
            PianoSound.select(context, PianoSound.AUTO);
        }
    }
    @Test public void switchingKeepsOnlySelectedBankAndCancelsObsoleteRequests() {
        var context = RuntimeEnvironment.getApplication();
        SampleBankStore.start(context, PianoSound.SYNTHETIC);
        assertEquals("可立即使用", SampleBankStore.status(PianoSound.SYNTHETIC));
        assertNull(SampleBankStore.bank(PianoSound.SYNTHETIC));
        SampleBankStore.start(context, PianoSound.SALAMANDER);
        assertTrue(SampleBankStore.status(PianoSound.SALAMANDER).contains("載入"));
        SampleBankStore.start(context, PianoSound.SYNTHETIC);
        assertNull(SampleBankStore.bank(PianoSound.SALAMANDER));
        assertEquals("可立即使用", SampleBankStore.status(PianoSound.SYNTHETIC));
    }
    @Test public void automaticModeCanRequestBothBanks() {
        var context = RuntimeEnvironment.getApplication();
        SampleBankStore.start(context, new int[]{PianoSound.SALAMANDER, PianoSound.VIOLIN});
        assertTrue(SampleBankStore.status().contains("載入"));
        assertNull(SampleBankStore.bank(PianoSound.SALAMANDER));
        assertNull(SampleBankStore.bank(PianoSound.VIOLIN));
        SampleBankStore.start(context, PianoSound.SYNTHETIC);
        assertNull(SampleBankStore.bank(PianoSound.SALAMANDER));
        assertNull(SampleBankStore.bank(PianoSound.VIOLIN));
        assertEquals("可立即使用", SampleBankStore.status(PianoSound.SYNTHETIC));
    }
    @Test public void violinBankLoadsAndIsCancelledLikePiano() {
        var context = RuntimeEnvironment.getApplication();
        SampleBankStore.start(context, PianoSound.VIOLIN);
        assertTrue(SampleBankStore.status(PianoSound.VIOLIN).contains("載入"));
        assertNull(SampleBankStore.bank(PianoSound.VIOLIN));
        assertNull(SampleBankStore.spicBank(PianoSound.VIOLIN));
        assertNull(SampleBankStore.fallbackBank(PianoSound.VIOLIN));
        SampleBankStore.start(context, PianoSound.SALAMANDER);
        assertNull(SampleBankStore.bank(PianoSound.VIOLIN));
        assertNull(SampleBankStore.spicBank(PianoSound.VIOLIN));
        assertNull(SampleBankStore.fallbackBank(PianoSound.VIOLIN));
        assertTrue(SampleBankStore.status(PianoSound.SALAMANDER).contains("載入"));
        SampleBankStore.start(context, PianoSound.SYNTHETIC);
        assertNull(SampleBankStore.bank(PianoSound.SALAMANDER));
        assertEquals("可立即使用", SampleBankStore.status(PianoSound.SYNTHETIC));
    }
}
