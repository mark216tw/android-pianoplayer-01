package com.minipianoplayer.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Midi {
    private static final int LIMIT = 8 * 1024 * 1024;
    private static final int MAX_TRACKS = 64;
    private static final int MAX_MEASURES = 10000;
    private Midi() {}

    public static Score read(InputStream input) throws Exception {
        byte[] data = bounded(input);
        ByteReader reader = new ByteReader(data);
        if (!"MThd".equals(reader.ascii(4))) throw new IOException("不是有效的 MIDI 檔案");
        long headerLength = reader.u32();
        if (headerLength < 6 || headerLength > reader.remaining()) throw new IOException("MIDI header 長度無效");
        int format = reader.u16();
        int trackCount = reader.u16();
        int division = reader.u16();
        if (format != 0 && format != 1) throw new IOException("目前支援 MIDI Format 0／1");
        if ((division & 0x8000) != 0) throw new IOException("不支援 SMPTE 時間格式的 MIDI");
        if (division == 0) throw new IOException("MIDI 時間分割無效");
        reader.skip(headerLength - 6);
        Score score = new Score();
        List<TimeSignature> signatures = new ArrayList<>();
        int tracks = 0;
        long maxTick = 0;
        boolean drums = false, nonPiano = false, openNotes = false;
        while (reader.remaining() >= 8) {
            String id = reader.ascii(4);
            long length = reader.u32();
            if (length > reader.remaining()) throw new IOException("MIDI 區塊長度超過檔案");
            if ("MTrk".equals(id)) {
                if (++tracks > MAX_TRACKS) throw new IOException("MIDI 軌道數量過多");
                TrackInfo info = parseTrack(new ByteReader(reader.bytes((int) length)), tracks, division, score, signatures);
                if (score.title.equals("未命名樂曲") && !info.name.isEmpty()) score.title = info.name;
                score.partNames.put("T" + tracks, info.name.isEmpty() ? "Track " + tracks : info.name);
                drums |= info.drums; nonPiano |= info.nonPiano; openNotes |= info.openNotes;
                maxTick = Math.max(maxTick, info.maxTick);
            } else reader.skip(length);
        }
        if (tracks == 0) throw new IOException("MIDI 檔案沒有樂曲軌道");
        if (trackCount > 0 && tracks < trackCount) score.warnings.add("MIDI 宣告的軌道數與實際內容不同");
        if ("未命名樂曲".equals(score.title)) score.title = "MIDI 樂曲";
        score.features.add("MIDI");
        if (!score.notes.isEmpty()) score.features.add("力度");
        if (!score.pedals.isEmpty()) score.features.add("踏板");
        if (!score.tempos.isEmpty()) score.features.add("速度");
        if (!signatures.isEmpty()) score.features.add("拍號");
        if (tracks > 1) score.features.add("多軌");
        if (drums) score.warnings.add("已忽略 MIDI 打擊樂軌道（Channel 10）");
        if (nonPiano) score.warnings.add("MIDI 含非鋼琴樂器，將以鋼琴音色播放");
        if (openNotes) score.warnings.add("部分 MIDI 音符缺少 Note Off，已於軌道結尾關閉");
        score.bpm = 120;
        for (Score.Tempo tempo : score.tempos) if (tempo.tick == 0) { score.bpm = (int) Math.round(tempo.bpm); break; }
        signatures.sort(Comparator.comparingLong(value -> value.tick));
        if (signatures.isEmpty()) { score.beats = 4; score.beatType = 4; }
        else { score.beats = signatures.get(0).beats; score.beatType = signatures.get(0).beatType; }
        score.endTick = Math.max(maxTick, score.length());
        score.sort();
        buildMeasures(score, signatures, Math.max(score.endTick, score.length()));
        score.originalMeasureCount = score.measures.size();
        return score;
    }

    private static TrackInfo parseTrack(ByteReader reader, int trackNumber, int division, Score score, List<TimeSignature> signatures) throws IOException {
        TrackInfo info = new TrackInfo();
        String part = "T" + trackNumber;
        Map<Integer, ArrayDeque<long[]>> open = new HashMap<>();
        long tick = 0;
        int running = -1;
        boolean pedalDown = false;
        while (reader.remaining() > 0) {
            tick += reader.vlq();
            if (reader.remaining() == 0) break;
            int status = reader.u8();
            if (status < 0x80) {
                if (running < 0) throw new IOException("MIDI running status 無效");
                reader.back();
                status = running;
            }
            if (status == 0xFF) {
                int type = reader.u8();
                int length = reader.vlq();
                byte[] payload = reader.bytes(length);
                if (type == 0x03) info.name = decode(payload);
                else if (type == 0x51 && payload.length >= 3) {
                    int micros = ((payload[0] & 0xff) << 16) | ((payload[1] & 0xff) << 8) | (payload[2] & 0xff);
                    if (micros > 0) score.tempos.add(new Score.Tempo(convert(tick, division), 60000000.0 / micros));
                } else if (type == 0x58 && payload.length >= 2) {
                    int beats = Math.max(1, payload[0] & 0xff);
                    int beatType = 1 << Math.min(6, payload[1] & 0xff);
                    signatures.add(new TimeSignature(convert(tick, division), beats, beatType));
                } else if (type == 0x2F) break;
                continue;
            }
            if (status == 0xF0 || status == 0xF7) {
                reader.skip(reader.vlq());
                continue;
            }
            if (status < 0x80 || status >= 0xF0) throw new IOException("MIDI 事件狀態無效");
            running = status;
            int type = status & 0xF0, channel = status & 0x0F;
            int first = reader.u8();
            int second = type == 0xC0 || type == 0xD0 ? 0 : reader.u8();
            if (channel == 9) { info.drums = true; continue; }
            if (type == 0x90) {
                if (second == 0) close(open, channel, first, tick, division, part, score, info);
                else open.computeIfAbsent((channel << 8) | first, key -> new ArrayDeque<>()).addLast(new long[]{tick, Math.max(1, Math.min(127, second))});
            } else if (type == 0x80) close(open, channel, first, tick, division, part, score, info);
            else if (type == 0xB0 && first == 64) {
                boolean down = second >= 64;
                if (down != pedalDown) { pedalDown = down; score.pedals.add(new Score.Pedal(convert(tick, division), down, part)); }
            } else if (type == 0xC0 && first > 7) info.nonPiano = true;
        }
        for (Map.Entry<Integer, ArrayDeque<long[]>> entry : open.entrySet()) {
            int channel = entry.getKey() >> 8, pitch = entry.getKey() & 0xff;
            for (long[] note : entry.getValue()) {
                addNote(score, note[0], tick, (int) note[1], pitch, channel, part, division);
                info.openNotes = true;
            }
        }
        info.maxTick = Math.max(info.maxTick, convert(tick, division));
        return info;
    }

    private static void close(Map<Integer, ArrayDeque<long[]>> open, int channel, int pitch, long tick, int division, String part, Score score, TrackInfo info) {
        ArrayDeque<long[]> queue = open.get((channel << 8) | pitch);
        if (queue == null) return;
        long[] note = queue.pollFirst();
        if (note == null) return;
        addNote(score, note[0], tick, (int) note[1], pitch, channel, part, division);
        if (queue.isEmpty()) open.remove((channel << 8) | pitch);
    }

    private static void addNote(Score score, long start, long end, int velocity, int pitch, int channel, String part, int division) {
        if (pitch < 21 || pitch > 108) {
            if (!score.warnings.contains("鋼琴音域之外的音符已略過")) score.warnings.add("鋼琴音域之外的音符已略過");
            return;
        }
        long begin = convert(start, division), finish = convert(end, division);
        if (finish <= begin) finish = begin + 1;
        score.notes.add(new Score.Note(pitch, begin, finish - begin, velocity, part, Integer.toString(channel + 1), pitch < 60 ? 2 : 1));
    }

    private static long convert(long ticks, int division) {
        return Math.round(ticks * (double) Score.PPQ / division);
    }

    private static String decode(byte[] data) {
        if (data.length == 0) return "";
        String value = new String(data, StandardCharsets.UTF_8);
        if (value.indexOf('\uFFFD') >= 0) value = new String(data, StandardCharsets.ISO_8859_1);
        return value.trim();
    }

    private static void buildMeasures(Score score, List<TimeSignature> source, long endTick) {
        if (endTick <= 0) return;
        List<TimeSignature> signatures = new ArrayList<>();
        for (TimeSignature value : source) if (value.tick < endTick) signatures.add(value);
        if (signatures.isEmpty() || signatures.get(0).tick > 0) signatures.add(0, new TimeSignature(0, 4, 4));
        signatures.sort(Comparator.comparingLong(value -> value.tick));
        int number = 1;
        for (int i = 0; i < signatures.size(); i++) {
            TimeSignature signature = signatures.get(i);
            long sectionEnd = i + 1 < signatures.size() ? signatures.get(i + 1).tick : endTick;
            long measureLength = (long) signature.beats * Score.PPQ * 4 / signature.beatType;
            if (measureLength <= 0 || signature.tick >= sectionEnd) continue;
            for (long tick = signature.tick; tick < sectionEnd; tick += measureLength) {
                score.measures.add(new Score.Measure(tick, measureLength, Integer.toString(number++), 1));
                if (score.measures.size() >= MAX_MEASURES) {
                    score.warnings.add("MIDI 小節數超過 10,000，僅顯示前 10,000 小節");
                    return;
                }
            }
        }
    }

    private static byte[] bounded(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > LIMIT) throw new IOException("MIDI 檔案過大（上限 8 MB）");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static final class TrackInfo {
        String name = "";
        long maxTick;
        boolean drums, nonPiano, openNotes;
    }

    private static final class TimeSignature {
        final long tick;
        final int beats, beatType;
        TimeSignature(long tick, int beats, int beatType) { this.tick = tick; this.beats = beats; this.beatType = beatType; }
    }

    private static final class ByteReader {
        private final byte[] data;
        private int position, end;
        ByteReader(byte[] data) { this(data, 0, data.length); }
        ByteReader(byte[] data, int start, int end) { this.data = data; this.position = start; this.end = end; }
        int remaining() { return end - position; }
        int u8() throws IOException {
            if (position >= end) throw new IOException("MIDI 檔案提前結束");
            return data[position++] & 0xff;
        }
        int u16() throws IOException { return (u8() << 8) | u8(); }
        long u32() throws IOException { return ((long) u16() << 16) | u16(); }
        int vlq() throws IOException {
            int value = 0;
            for (int i = 0; i < 4; i++) {
                int byteValue = u8();
                value = (value << 7) | (byteValue & 0x7f);
                if ((byteValue & 0x80) == 0) return value;
            }
            throw new IOException("MIDI 變動長度數值過長");
        }
        void back() { if (position > 0) position--; }
        byte[] bytes(int count) throws IOException {
            if (count < 0 || count > remaining()) throw new IOException("MIDI 區塊長度無效");
            byte[] result = new byte[count];
            System.arraycopy(data, position, result, 0, count);
            position += count;
            return result;
        }
        void skip(long count) throws IOException {
            if (count < 0 || count > remaining()) throw new IOException("MIDI 區塊長度無效");
            position += (int) count;
        }
        String ascii(int count) throws IOException { return new String(bytes(count), StandardCharsets.US_ASCII); }
    }
}
