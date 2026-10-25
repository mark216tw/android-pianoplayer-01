package com.minipianoplayer.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLongArray;

final class PianoAudio implements AutoCloseable {
    private static final int VOICES = 72, MAX_ACTIVE = 64, TABLE_SIZE = 2048;
    private static final double STEAL_FADE_SECONDS = .006;
    private final float[] sine = new float[TABLE_SIZE];
    private final Voice[] voices = new Voice[VOICES];
    private final LinkedBlockingQueue<Runnable> commands = new LinkedBlockingQueue<>();
    private final double[] increments = new double[128];
    private final Thread thread;
    private final int rate, frames;
    private final Limiter limiter;
    private final Reverb reverb;
    private final Context context;
    private final AudioManager manager;
    private double duck = 1;
    private final AudioDeviceCallback deviceCallback = new AudioDeviceCallback() {
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] added) { rebuild = true; }
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) { rebuild = true; }
    };
    private final BroadcastReceiver noisy = new BroadcastReceiver() {
        @Override public void onReceive(Context sender, Intent intent) { pause(); }
    };
    private volatile boolean running = true, rebuild;
    volatile boolean playing;
    volatile double position;
    volatile String error;
    private volatile int bufferFrames, underruns;
    private long lastUnderrunCheck;
    private volatile long keysLow, keysHigh;
    private final AtomicLongArray keyAttackTimes = new AtomicLongArray(128);
    private PlaybackProgram program;
    private PlaybackClock clock;
    private boolean[] muted = new boolean[0], partPedals = new boolean[0];
    private long[] pedalStarts = new long[0];
    private Score score;
    private long scoreLength;
    private int nextEvent;
    private double cursor, speed = 1;
    private int sound = PianoSound.SALAMANDER;
    private volatile int selectedSound = PianoSound.SALAMANDER;
    private volatile int[] requiredSounds;
    private boolean lowStringEnabled = true;
    private double violinLevel = .75;
    private double volume = 1, targetVolume = 1;
    private static final class Voice {
        boolean active, held, sampled, sustainPedal = true; int id, pitch, velocity, age, part, channel;
        long started;
        double phase, level, decay, hammer, release = 1, resumeFade = 1, sampledRelease;
        final SampleVoice sampleVoice = new SampleVoice();
    }
    PianoAudio(Context context) {
        TimbreBalance.prepare();
        this.context = context.getApplicationContext();
        manager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
        rate = outputRate(integer(manager == null ? null : manager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE), 0));
        frames = Math.max(64, Math.min(512, integer(manager == null ? null : manager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER), 192)));
        limiter = new Limiter(rate, .95, .0015, .12);
        reverb = new Reverb(rate);
        for (int i = 0; i < TABLE_SIZE; i++) sine[i] = (float) Math.sin(i * 2 * Math.PI / TABLE_SIZE);
        for (int i = 0; i < VOICES; i++) voices[i] = new Voice();
        for (int i = 0; i < 128; i++) increments[i] = 440 * Math.pow(2, (i - 69) / 12.0) / rate;
        configure(this.context);
        manager.registerAudioDeviceCallback(deviceCallback, new Handler(Looper.getMainLooper()));
        IntentFilter filter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if (Build.VERSION.SDK_INT >= 33) this.context.registerReceiver(noisy, filter, Context.RECEIVER_NOT_EXPORTED);
        else this.context.registerReceiver(noisy, filter);
        thread = new Thread(this::render, "piano-player-audio");
        thread.start();
    }
    void configure(Context context) {
        int selection = PianoSound.selected(context);
        double level = 1;
        boolean reverbEnabled = UiTheme.preferences(context).getBoolean("pianoReverb", false);
        double reverbLevel = Math.max(0, Math.min(60, UiTheme.preferences(context).getInt("pianoReverbMix", 18))) / 100.0;
        boolean lowStrings = UiTheme.preferences(context).getBoolean("stringLowViolin", true);
        double violinVolume = Math.max(0, Math.min(100, UiTheme.preferences(context).getInt("violinVolume", 75))) / 100.0;
        selectedSound = selection;
        if (selection == PianoSound.AUTO) {
            int[] sounds = requiredSounds;
            SampleBankStore.start(context, sounds == null ? new int[0] : sounds);
        } else {
            SampleBankStore.start(context, selection);
        }
        commands.add(() -> {
            if (sound != selection) { clear(); limiter.reset(); reverb.clear(); }
            sound = selection; targetVolume = level; lowStringEnabled = lowStrings; violinLevel = violinVolume; reverb.configure(reverbEnabled, reverbLevel);
        });
    }
    String soundStatus() {
        int selected = selectedSound;
        if (selected == PianoSound.SYNTHETIC) return PianoSound.name(selected);
        if (selected == PianoSound.AUTO) {
            String status = SampleBankStore.status();
            boolean ready = status.startsWith("已載入");
            boolean fallback = status.startsWith("載入中") || status.startsWith("載入逾時");
            return PianoSound.name(selected) + " · " + status + (ready || !fallback ? "" : " · 暫用合成");
        }
        String status = SampleBankStore.status(selected);
        return PianoSound.name(selected) + (SampleBankStore.bank(selected) != null ? "" : " · " + status + (status.startsWith("載入失敗") ? "" : " · 暫用合成"));
    }
    private static int integer(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (Exception e) { return fallback; }
    }
    static int outputRate(int reported) {
        return reported >= 8000 && reported <= 96000 ? reported : 48000;
    }
    int sampleRate() { return rate; }
    int bufferFrames() { return bufferFrames; }
    int underruns() { return underruns; }
    String audioStatus() {
        return "輸出 " + rate + " Hz · 緩衝 " + frames + "/" + bufferFrames + " 幀 · underrun " + underruns;
    }
    void setViolinLevel(double value) {
        double level = Math.max(0, Math.min(1, value));
        commands.add(() -> violinLevel = level);
    }
    void setReverb(boolean enabled, double value) {
        double level = Math.max(0, Math.min(.6, value));
        commands.add(() -> reverb.configure(enabled, level));
    }
    void duck(boolean enabled) { commands.add(() -> duck = enabled ? .25 : 1); }
    void load(Score value) { load(new PlaybackProgram(value)); }
    void load(PlaybackProgram compiled) {
        PlaybackClock newClock = new PlaybackClock(compiled, rate); newClock.speed(speed);
        boolean[] newMuted = compiled.initialMuted.clone(), pedals = new boolean[compiled.parts.size()]; long[] starts = new long[compiled.parts.size()];
        requiredSounds = compiled.requiredSounds.clone();
        if (selectedSound == PianoSound.AUTO) SampleBankStore.start(context, requiredSounds);
        commands.add(() -> {
            playing = false; clear(); limiter.reset(); reverb.clear(); program = compiled; clock = newClock; clock.speed(speed); score = compiled.score; scoreLength = score.length();
            muted = newMuted; partPedals = pedals; pedalStarts = starts; cursor = position = 0; nextEvent = 0;
        });
    }
    void speed(double value) { commands.add(() -> { speed = value; if (clock != null) clock.speed(value); }); }
    void mute(int channel, boolean enabled) {
        commands.add(() -> {
            if (channel < 0 || channel >= muted.length) return; muted[channel] = enabled;
            for (Voice v : voices) if (v.active && v.channel == channel) {
                if (enabled) { v.held = false; release(v, true); } else { v.active = false; v.sampleVoice.clear(); }
            }
            if (!enabled && playing) restoreNotes(channel);
        });
    }
    void play() { commands.add(() -> {
        if (score == null || score.notes.isEmpty()) return;
        if (clock.finished()) clock.seek(0);
        restore(); playing = true;
    }); }
    void pause() { commands.add(() -> { playing = false; clear(); }); }
    void stop() { commands.add(() -> { playing = false; clear(); limiter.reset(); reverb.clear(); if (clock != null) clock.seek(0); cursor = position = 0; nextEvent = 0; }); }
    void seek(long tick) { commands.add(() -> {
        cursor = score == null ? 0 : Math.max(0, Math.min(scoreLength, tick)); if (clock != null) clock.seek((long) cursor);
        clear(); limiter.reset(); reverb.clear(); nextEvent = 0;
        if (playing) restore();
        position = cursor;
    }); }
    private void restore() {
        for (Voice v : voices) { v.active = false; v.sampleVoice.clear(); }
        clearAttacks();
        cursor = clock.tick(); java.util.Arrays.fill(partPedals, false); java.util.Arrays.fill(pedalStarts, Long.MAX_VALUE);
        for (int i = 0; i < score.pedals.size(); i++) {
            Score.Pedal p = score.pedals.get(i); if (p.tick > cursor) break;
            int part = program.pedalParts[i]; if (p.down && !partPedals[part]) pedalStarts[part] = p.tick; partPedals[part] = p.down;
        }
        restoreNotes(-1);
        nextEvent = 0;
        while (nextEvent < program.events.length && program.events[nextEvent].seconds <= clock.seconds()) nextEvent++;
    }
    private void restoreNotes(int channel) {
        cursor = clock.tick();
        double fromSeconds = Math.max(0, clock.seconds() - 120);
        long fromTick = (long) Math.floor(program.timeline.tick(fromSeconds));
        for (int id = firstNoteIndex(fromTick); id < score.notes.size(); id++) {
            Score.Note n = score.notes.get(id);
            if (n.start > cursor) break;
            int noteChannel = program.noteChannels[id], part = program.noteParts[id];
            if (muted[noteChannel] || (channel >= 0 && noteChannel != channel)) continue;
            long soundingEnd = program.noteEnds[id];
            boolean held = soundingEnd > cursor;
            if (!held && !partPedals[part]) continue;
            SampleBank bank = bankForNote(id);
            boolean sustainPedal = bank == null || bank.sustainPedal();
            if (!held && !(sustainPedal && soundingEnd > pedalStarts[part])) continue;
            double seconds = clock.age(id);
            if (seconds > 30) continue;
            noteOn(id, n.pitch, n.velocity, seconds < 1.0 / rate);
            for (Voice v : voices) if (v.active && v.id == id && v.held) {
                if (v.sampled) { v.sampleVoice.seek(seconds); v.active = v.sampleVoice.active; }
                else {
                    v.level *= Math.exp(-seconds / (1.4 + (108 - v.pitch) * .025));
                    v.hammer *= Math.exp(-seconds * rate * .00035); v.age = (int) (seconds * rate);
                    v.phase = (increments[v.pitch] * rate * seconds) % 1;
                }
                v.resumeFade = seconds > 1.0 / rate ? 0 : 1;
                v.held = held;
            }
        }
    }
    private int firstNoteIndex(long tick) {
        int low = 0, high = score.notes.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (score.notes.get(mid).start < tick) low = mid + 1; else high = mid;
        }
        return low;
    }
    private void dispatch(PlaybackProgram.Event e) {
        if (e.type == PlaybackProgram.NOTE_ON) {
            clock.onset(e.id); Score.Note note = score.notes.get(e.id);
            if (!muted[program.noteChannels[e.id]]) noteOn(e.id, note.pitch, note.velocity);
        } else if (e.type == PlaybackProgram.NOTE_OFF) noteOff(e.id);
        else {
            boolean changed = partPedals[e.part] != e.down;
            if (e.down && changed) pedalStarts[e.part] = e.tick;
            partPedals[e.part] = e.down;
            if (!e.down) for (Voice v : voices) if (v.active && v.part == e.part && !v.held) release(v, false);
        }
    }
    boolean lit(int pitch) {
        return pitch < 64 ? (keysLow & (1L << pitch)) != 0 : (keysHigh & (1L << (pitch - 64))) != 0;
    }
    long attackTime(int pitch) { return keyAttackTimes.get(pitch); }
    private SampleBank bankForNote(int id) {
        int noteSound = sound;
        if (sound == PianoSound.AUTO) {
            if (program == null || id < 0 || id >= program.noteSounds.length) return null;
            noteSound = program.noteSounds[id];
        }
        if (noteSound == PianoSound.SYNTHETIC) return null;
        if (noteSound == PianoSound.SALAMANDER) return SampleBankStore.bank(PianoSound.SALAMANDER);
        SampleBank primary = SampleBankStore.bank(PianoSound.VIOLIN);
        if (primary == null || score == null || id < 0 || id >= score.notes.size()) return primary;
        Score.Note note = score.notes.get(id);
        double duration = score.seconds(note.end()) - score.seconds(note.start);
        boolean allowLowString = lowStringEnabled && program != null && id >= 0 && id < program.noteLowStrings.length && program.noteLowStrings[id];
        int route = ViolinRouting.route(note.pitch, duration, allowLowString, note.articulation);
        if (route == ViolinRouting.LOW_PIANO) {
            SampleBank piano = SampleBankStore.bank(PianoSound.SALAMANDER);
            if (piano != null) return piano;
            SampleBank low = SampleBankStore.fallbackBank(PianoSound.VIOLIN);
            if (low != null && note.pitch >= low.coverageLow() && note.pitch <= low.coverageHigh()) return low;
        } else if (route == ViolinRouting.SPIC) {
            SampleBank spic = SampleBankStore.spicBank(PianoSound.VIOLIN);
            if (spic != null && note.pitch >= spic.coverageLow() && note.pitch <= spic.coverageHigh()) return spic;
        }
        return primary;
    }
    private void noteOn(int id, int pitch, int velocity) { noteOn(id, pitch, velocity, true); }
    private void noteOn(int id, int pitch, int velocity, boolean attack) {
        if (pitch < 21 || pitch > 108) return;
        velocity = Math.max(1, Math.min(127, velocity));
        if (attack) keyAttackTimes.set(pitch, SystemClock.uptimeMillis());
        noteOff(id);
        Voice chosen = allocate();
        chosen.active = chosen.held = true; chosen.id = id; chosen.pitch = pitch; chosen.velocity = velocity; chosen.started = renderedFrames;
        chosen.part = program.noteParts[id]; chosen.channel = program.noteChannels[id];
        SampleBank bank = bankForNote(id);
        chosen.sampledRelease = bank == null ? 0 : bank.releaseSeconds();
        chosen.sustainPedal = bank == null || bank.sustainPedal();
        SampleBank.Selection selection = bank == null ? null : bank.select(pitch, velocity);
        PianoSample first = selection == null ? null : bank.sample(pitch, selection.first);
        PianoSample second = selection == null || selection.second < 0 ? null : bank.sample(pitch, selection.second);
        chosen.sampled = first != null || second != null;
        if (chosen.sampled) {
            chosen.sampleVoice.start(first, second, selection.mix, selection.firstGain, selection.secondGain, pitch, rate);
            double tone = bank.toneHz(pitch);
            if (tone > 0) chosen.sampleVoice.tone(tone, rate);
        } else chosen.sampleVoice.clear();
        chosen.phase = 0; chosen.age = 0; chosen.release = 1;
        chosen.resumeFade = 1;
        int playedSound = sound;
        if (sound == PianoSound.AUTO && program != null && id >= 0 && id < program.noteSounds.length) playedSound = program.noteSounds[id];
        double instrumentLevel = playedSound == PianoSound.VIOLIN ? violinLevel : 1;
        chosen.level = (chosen.sampled
            ? TimbreBalance.strength(velocity) * .5 * (bank == null ? 1 : bank.outputGain()) * (bank.calibrated() ? 1 : TimbreBalance.gain(sound, pitch))
            : TimbreBalance.voiceLevel(sound, false, pitch, velocity)) * instrumentLevel;
        chosen.hammer = 1;
        chosen.decay = Math.exp(-1.0 / (rate * (1.4 + (108 - pitch) * .025)));
    }
    private Voice allocate() {
        Voice free = null;
        int active = 0;
        for (Voice v : voices) {
            if (!v.active) { if (free == null) free = v; }
            else active++;
        }
        if (active < MAX_ACTIVE && free != null) return free;
        Voice victim = quietest(false, true);
        if (victim == null) victim = quietest(false, false);
        if (victim == null) victim = quietest(true, false);
        if (victim != null) { victim.held = false; release(victim, STEAL_FADE_SECONDS); }
        if (free != null) return free;
        return victim == null ? voices[0] : victim;
    }
    private Voice quietest(boolean includeHeld, boolean requireUnpedaled) {
        Voice result = null; double lowest = Double.MAX_VALUE;
        for (Voice v : voices) {
            if (!v.active) continue;
            if (!includeHeld && v.held) continue;
            if (requireUnpedaled && pedaled(v)) continue;
            double amplitude = amplitude(v);
            if (result == null || amplitude < lowest || (amplitude == lowest && v.started < result.started)) {
                result = v; lowest = amplitude;
            }
        }
        return result;
    }
    private double amplitude(Voice voice) {
        return voice.level * (voice.sampled ? voice.sampleVoice.envelopeLevel() : 1);
    }
    private boolean pedaled(Voice voice) {
        return voice.sustainPedal && voice.part < partPedals.length && partPedals[voice.part];
    }
    private void noteOff(int id) {
        for (Voice v : voices) if (v.active && v.id == id && v.held) {
            v.held = false;
            if (!pedaled(v)) release(v, false);
        }
    }
    private void release(Voice voice, boolean fast) {
        double seconds = voice.sampled && voice.sampledRelease > 0 ? voice.sampledRelease : Damper.releaseSeconds(voice.pitch);
        release(voice, fast ? Math.min(.025, seconds) : seconds);
    }
    private void release(Voice voice, double seconds) {
        if (voice.sampled) voice.sampleVoice.release(seconds);
        else voice.release = Math.exp(-1.0 / (rate * seconds));
    }
    private void clear() {
        for (Voice v : voices) if (v.active) {
            v.held = false; release(v, true);
        }
        java.util.Arrays.fill(partPedals, false);
        keysLow = keysHigh = 0; clearAttacks();
    }
    private void clearAttacks() { for (int pitch = 21; pitch <= 108; pitch++) keyAttackTimes.set(pitch, 0); }
    private float wave(double phase) { return sine[((int) (phase * TABLE_SIZE)) & (TABLE_SIZE - 1)]; }
    private double resumeGain(Voice voice) {
        double gain = Math.min(1, voice.resumeFade); if (gain < 1) voice.resumeFade += 1.0 / (rate * .003); return gain;
    }
    private boolean active() {
        if (playing || reverb.active()) return true;
        for (Voice v : voices) if (v.active) return true;
        return false;
    }
    private AudioTrack createTrack() {
        int minimum = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT);
        if (minimum <= 0) throw new IllegalStateException("裝置不支援音訊串流");
        AudioTrack track = new AudioTrack.Builder()
            .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(new AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).build())
            .setBufferSizeInBytes(Math.max(minimum, frames * 8 * 2))
            .setTransferMode(AudioTrack.MODE_STREAM).setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY).build();
        track.setBufferSizeInFrames(Math.max(frames * 2, minimum / 8));
        bufferFrames = track.getBufferSizeInFrames();
        underruns = 0;
        lastUnderrunCheck = 0;
        return track;
    }
    private long renderedFrames;
    private void render() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
        AudioTrack track = null;
        boolean trackPlaying = false;
        long idleFrames = 0, warmFrames = rate * 2L;
        int failures = 0;
        try {
            float[] buffer = new float[frames * 2];
            while (running) {
                Runnable command; while ((command = commands.poll()) != null) command.run();
                if (rebuild) {
                    rebuild = false;
                    if (track != null) { try { track.pause(); track.flush(); track.release(); } catch (Exception ignored) {} }
                    track = null; trackPlaying = false; idleFrames = 0; limiter.reset(); reverb.clear();
                }
                if (track == null) {
                    try { track = createTrack(); failures = 0; }
                    catch (Exception e) {
                        if (++failures > 3) throw e;
                        error = e.getMessage();
                        try { Thread.sleep(200); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
                        continue;
                    }
                }
                long uptime = SystemClock.uptimeMillis();
                if (uptime - lastUnderrunCheck >= 500) {
                    lastUnderrunCheck = uptime;
                    underruns = track.getUnderrunCount();
                }
                boolean sounding = active();
                if (!sounding && idleFrames >= warmFrames) {
                    if (trackPlaying) { try { track.pause(); track.flush(); } catch (IllegalStateException ignored) {} trackPlaying = false; }
                    Runnable next;
                    try { next = commands.poll(1, TimeUnit.SECONDS); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                    if (next != null) next.run();
                    continue;
                }
                if (!trackPlaying) { track.play(); trackPlaying = true; }
                for (int sample = 0; sample < frames; sample++) {
                    if (sounding && playing) {
                        while (nextEvent < program.events.length && program.events[nextEvent].seconds <= clock.seconds()) dispatch(program.events[nextEvent++]);
                        clock.advance(1);
                        if (clock.finished()) { cursor = scoreLength; playing = false; clear(); }
                    }
                    if (!sounding) {
                        buffer[sample * 2] = 0; buffer[sample * 2 + 1] = 0;
                        continue;
                    }
                    double left = 0, right = 0;
                    for (Voice v : voices) if (v.active) {
                        if (v.sampled) {
                            v.sampleVoice.next(); double fade = resumeGain(v);
                            left += v.sampleVoice.left * v.level * fade; right += v.sampleVoice.right * v.level * fade;
                            v.active = v.sampleVoice.active;
                            if (!v.active) v.sampleVoice.clear();
                            continue;
                        }
                        double tone = wave(v.phase) + .42 * v.hammer * wave(v.phase * 2) + .20 * v.hammer * wave(v.phase * 3) + .08 * v.hammer * wave(v.phase * 5);
                        double attack = Math.min(1, v.age++ / (rate * .002));
                        double toneLevel = tone * v.level * attack * resumeGain(v); left += toneLevel; right += toneLevel;
                        v.phase += increments[v.pitch]; if (v.phase >= 1) v.phase -= 1;
                        v.level *= v.decay * v.release; v.hammer *= .99965;
                        if (v.level < .00003) v.active = false;
                    }
                    volume += (targetVolume - volume) * .001;
                    renderedFrames++;
                    double master = volume * 1.5 * duck;
                    buffer[sample * 2] = (float) (left * master);
                    buffer[sample * 2 + 1] = (float) (right * master);
                }
                if (sounding) reverb.process(buffer, frames);
                limiter.process(buffer, frames);
                long low = 0, high = 0;
                for (Voice v : voices) if (v.active && v.held) {
                    if (v.pitch < 64) low |= 1L << v.pitch; else high |= 1L << (v.pitch - 64);
                }
                keysLow = low; keysHigh = high; if (clock != null) cursor = clock.tick(); position = cursor;
                try {
                    int written = 0;
                    while (running && written < buffer.length) {
                        int count = track.write(buffer, written, buffer.length - written, AudioTrack.WRITE_BLOCKING);
                        if (count < 0) throw new IllegalStateException("音訊輸出錯誤：" + count);
                        if (count == 0) throw new IllegalStateException("音訊輸出停止");
                        written += count;
                    }
                } catch (Exception e) {
                    if (++failures > 3) throw e;
                    error = e.getMessage();
                    try { track.pause(); track.flush(); track.release(); } catch (Exception ignored) {}
                    track = null; trackPlaying = false; idleFrames = 0;
                    try { Thread.sleep(100); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
                    continue;
                }
                idleFrames = sounding ? 0 : idleFrames + frames;
            }
        } catch (Exception e) { error = e.getMessage(); playing = false; }
        finally { if (track != null) { try { track.stop(); } catch (Exception ignored) {} track.release(); } }
    }
    @Override public void close() {
        running = false;
        try { manager.unregisterAudioDeviceCallback(deviceCallback); } catch (Exception ignored) {}
        try { context.unregisterReceiver(noisy); } catch (Exception ignored) {}
        commands.add(() -> { });
        try { thread.join(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
