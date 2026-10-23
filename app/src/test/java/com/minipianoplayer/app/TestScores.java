package com.minipianoplayer.app;

final class TestScores {
    private TestScores() {}
    static Score simple() {
        Score score = new Score();
        score.title = "測試樂曲";
        score.bpm = 100;
        int[] melody = {60, 60, 67, 67, 69, 69, 67, 65, 65, 64, 64, 62, 62, 60};
        long cursor = 0;
        for (int i = 0; i < melody.length; i++) {
            long duration = (i == 6 || i == 13) ? 960 : 480;
            score.notes.add(new Score.Note(melody[i], cursor, duration, 90));
            cursor += duration;
        }
        for (int i = 0; i < 4; i++) score.notes.add(new Score.Note(i == 2 ? 53 : 48, i * 1920L, 1800, 65, "P1", "2", 2));
        score.partNames.put("P1", "Piano");
        score.sort();
        return score;
    }
    static String smallXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><score-partwise version=\"4.0\"><work><work-title>小星星</work-title></work>"
            + "<part-list><score-part id=\"P1\"><part-name>Piano</part-name></score-part></part-list>"
            + "<part id=\"P1\"><measure number=\"1\"><attributes><divisions>1</divisions><time><beats>4</beats><beat-type>4</beat-type></time></attributes>"
            + "<note><pitch><step>C</step><octave>4</octave></pitch><duration>1</duration><voice>1</voice></note>"
            + "<note><pitch><step>D</step><octave>4</octave></pitch><duration>1</duration><voice>1</voice></note>"
            + "<note><pitch><step>E</step><octave>4</octave></pitch><duration>1</duration><voice>1</voice></note>"
            + "<note><pitch><step>G</step><octave>4</octave></pitch><duration>1</duration><voice>1</voice></note>"
            + "</measure></part></score-partwise>";
    }
}
