package com.minipianoplayer.app;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScoreLibraryActivityTest {
    private ScoreLibrary.Entry entry(String id, String title, long created, double seconds) {
        ScoreLibrary.Entry entry = new ScoreLibrary.Entry();
        entry.id = id; entry.title = title; entry.created = created; entry.seconds = seconds;
        return entry;
    }
    @Test public void searchFiltersByTitleIgnoringCase() {
        List<ScoreLibrary.Entry> source = Arrays.asList(entry("a", "小星星", 1, 30), entry("b", "Moonlight", 2, 60));
        assertEquals(1, ScoreLibraryActivity.filterAndSort(source, "moon", 0).size());
        assertEquals("小星星", ScoreLibraryActivity.filterAndSort(source, "星", 0).get(0).title);
    }
    @Test public void searchMatchesComposerAndInstrumentsWithoutFailingOnEmptyFields() {
        ScoreLibrary.Entry first = entry("a", "小星星", 1, 30);
        first.composer = "Claude Debussy"; first.instruments = "鋼琴、小提琴";
        ScoreLibrary.Entry second = entry("b", "Moonlight", 2, 60);
        second.composer = "Ludwig van Beethoven"; second.instruments = "鋼琴";
        List<ScoreLibrary.Entry> source = Arrays.asList(first, second, entry("c", "無資料", 3, 10));
        assertEquals("a", ScoreLibraryActivity.filterAndSort(source, "debussy", 0).get(0).id);
        assertEquals("a", ScoreLibraryActivity.filterAndSort(source, "小提琴", 0).get(0).id);
        assertEquals("b", ScoreLibraryActivity.filterAndSort(source, "beethoven", 0).get(0).id);
        assertEquals(3, ScoreLibraryActivity.filterAndSort(source, "", 0).size());
    }
    @Test public void sortModesOrderByRecentNameAndDuration() {
        List<ScoreLibrary.Entry> source = Arrays.asList(entry("a", "Zeta", 3, 60), entry("b", "Alpha", 2, 90), entry("c", "Mid", 1, 30));
        assertEquals("a", ScoreLibraryActivity.filterAndSort(source, "", 0).get(0).id);
        assertEquals("b", ScoreLibraryActivity.filterAndSort(source, "", 1).get(0).id);
        assertEquals("c", ScoreLibraryActivity.filterAndSort(source, "", 2).get(0).id);
    }
}
