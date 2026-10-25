package com.minipianoplayer.app;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable compiled events: pedal changes, then note-off, then note-on at equal times. */
final class PlaybackProgram {
    static final int PEDAL = 0, NOTE_OFF = 1, NOTE_ON = 2;
    static final class Event {
        final long tick; final double seconds; final int type, id, part; final boolean down;
        Event(long tick, TempoMap timeline, int type, int id, int part, boolean down) {
            this.tick = tick; seconds = timeline.seconds(tick); this.type = type; this.id = id; this.part = part; this.down = down;
        }
    }
    final Score score;
    final TempoMap timeline;
    final Event[] events;
    final List<Score.Channel> channels;
    final Map<String, Integer> parts = new LinkedHashMap<>();
    final int[] noteParts, noteChannels, pedalParts, noteSounds, requiredSounds;
    final long[] noteEnds;
    final boolean[] initialMuted, noteLowStrings;
    final double duration;
    final long length;
    PlaybackProgram(Score source) {
        score = source.copy(); timeline = score.timeline(); channels = score.channels(); length = score.length(); duration = timeline.seconds(length);
        Map<String, Integer> channelIds = new LinkedHashMap<>(); initialMuted = new boolean[channels.size()];
        for (int i = 0; i < channels.size(); i++) { channelIds.put(channels.get(i).key, i); initialMuted[i] = score.mutedVoices.contains(channels.get(i).key); }
        for (Score.Note n : score.notes) parts.computeIfAbsent(n.part, key -> parts.size());
        for (Score.Pedal p : score.pedals) parts.computeIfAbsent(p.part, key -> parts.size());
        noteParts = new int[score.notes.size()]; noteChannels = new int[score.notes.size()]; pedalParts = new int[score.pedals.size()];
        noteSounds = new int[score.notes.size()]; noteLowStrings = new boolean[score.notes.size()]; noteEnds = new long[score.notes.size()];
        boolean piano = false, violin = false;
        ArrayList<Event> compiled = new ArrayList<>();
        for (int id = 0; id < score.notes.size(); id++) {
            Score.Note n = score.notes.get(id); noteParts[id] = parts.get(n.part); noteChannels[id] = channelIds.get(n.channelKey());
            noteSounds[id] = score.partSounds.getOrDefault(n.part, PianoSound.SALAMANDER);
            noteLowStrings[id] = score.lowStringParts.contains(n.part); noteEnds[id] = n.soundingEnd();
            if (noteSounds[id] == PianoSound.SALAMANDER) piano = true;
            if (noteSounds[id] == PianoSound.VIOLIN) violin = true;
            compiled.add(new Event(n.start, timeline, NOTE_ON, id, noteParts[id], false)); compiled.add(new Event(noteEnds[id], timeline, NOTE_OFF, id, noteParts[id], false));
        }
        requiredSounds = new int[(piano ? 1 : 0) + (violin ? 1 : 0)];
        int soundIndex = 0;
        if (piano) requiredSounds[soundIndex++] = PianoSound.SALAMANDER;
        if (violin) requiredSounds[soundIndex] = PianoSound.VIOLIN;
        for (int i = 0; i < score.pedals.size(); i++) { Score.Pedal p = score.pedals.get(i); pedalParts[i] = parts.get(p.part); compiled.add(new Event(p.tick, timeline, PEDAL, 0, pedalParts[i], p.down)); }
        compiled.sort(Comparator.comparingDouble((Event e) -> e.seconds).thenComparingInt(e -> e.type)); events = compiled.toArray(new Event[0]);
    }
}
