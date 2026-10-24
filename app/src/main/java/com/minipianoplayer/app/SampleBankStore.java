package com.minipianoplayer.app;

import android.content.Context;
import android.content.res.AssetManager;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Immutable bank set at a time, with serialized/cancellable loading. No Activity references. */
final class SampleBankStore {
    private static final int PIANO = 1, VIOLIN = 2;
    private static final class State {
        final int signature; final SampleBank piano, violin, spic, low; final String status;
        State(int signature, SampleBank piano, SampleBank violin, SampleBank spic, SampleBank low, String status) {
            this.signature = signature; this.piano = piano; this.violin = violin;
            this.spic = spic; this.low = low; this.status = status;
        }
    }
    private static volatile State state = new State(0, null, null, null, null, "尚未載入");
    private static long generation;
    private static final ExecutorService loader = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "mini-piano-sample-loader"); thread.setDaemon(true); return thread;
    });
    private static final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "mini-piano-sample-watchdog"); thread.setDaemon(true); return thread;
    });
    private static final class Cancelled extends RuntimeException {}
    static SampleBank bank(int sound) {
        State current = state;
        if (sound == PianoSound.SALAMANDER) return current.piano;
        if (sound == PianoSound.VIOLIN) return current.violin;
        return null;
    }
    static SampleBank spicBank(int sound) { State current = state; return sound == PianoSound.VIOLIN ? current.spic : null; }
    static SampleBank fallbackBank(int sound) { State current = state; return sound == PianoSound.VIOLIN ? current.low : null; }
    static String status() { return state.status; }
    static String status(int sound) {
        State current = state;
        if (sound == PianoSound.AUTO) return current.status;
        if (sound == PianoSound.SYNTHETIC) return "可立即使用";
        int bit = sound == PianoSound.VIOLIN ? VIOLIN : PIANO;
        return (current.signature & bit) != 0 ? current.status : "尚未載入";
    }
    static boolean ready(Context context, int[] requiredSounds) {
        State current = state;
        if (current.status.startsWith("載入失敗") || current.status.startsWith("載入逾時")) return true;
        int selected = PianoSound.selected(context);
        if (selected == PianoSound.SYNTHETIC) return true;
        if (selected == PianoSound.SALAMANDER) return current.piano != null;
        if (selected == PianoSound.VIOLIN) return current.violin != null;
        if (requiredSounds != null) {
            for (int sound : requiredSounds) {
                if (sound == PianoSound.SALAMANDER && current.piano == null) return false;
                if (sound == PianoSound.VIOLIN && current.violin == null) return false;
            }
        }
        return true;
    }
    static void start(Context context) { start(context, PianoSound.selected(context)); }
    static synchronized void start(Context context, int sound) {
        if (sound == PianoSound.SYNTHETIC) {
            ++generation;
            state = new State(0, null, null, null, null, "可立即使用");
            return;
        }
        start(context, new int[]{sound});
    }
    static synchronized void start(Context context, int[] sounds) {
        int mask = 0;
        for (int sound : sounds) {
            if (sound == PianoSound.SALAMANDER) mask |= PIANO;
            else if (sound == PianoSound.VIOLIN) mask |= VIOLIN;
        }
        final int signature = mask;
        if (signature == state.signature) {
            if (signature == 0) state = new State(0, null, null, null, null, "等待樂譜");
            return;
        }
        final long request = ++generation;
        state = new State(signature, null, null, null, null, signature == 0 ? "等待樂譜" : "載入中");
        if (signature == 0) return;
        watchdog.schedule(() -> {
            synchronized (SampleBankStore.class) {
                if (generation == request && state.status.startsWith("載入")) {
                    state = new State(signature, null, null, null, null, "載入逾時，暫用合成");
                    generation++;
                }
            }
        }, 120, TimeUnit.SECONDS);
        AssetManager assets = context.getApplicationContext().getAssets();
        loader.execute(() -> {
            try {
                check(request);
                SampleBank piano = null, violin = null, spic = null, low = null;
                int actualSignature = signature;
                if ((signature & PIANO) != 0) {
                    piano = loadBank(assets, request, "piano/", "piano/",
                            (count, total) -> status(request, "載入鋼琴 " + count + "/" + total));
                }
                if ((signature & VIOLIN) != 0) {
                    try {
                        violin = loadBank(assets, request, "violin/", "violin/",
                                (count, total) -> status(request, "載入小提琴 " + count + "/" + total));
                    } catch (Cancelled cancelled) {
                        throw cancelled;
                    } catch (Exception | OutOfMemoryError e) {
                        android.util.Log.e("MiniPianoPlayer", "Violin bank load failed", e);
                        actualSignature &= ~VIOLIN;
                    }
                    if (violin != null) {
                        publish(request, signature, piano, violin, null, null, "載入小提琴完成 · 載入跳弓");
                        try {
                            spic = loadBank(assets, request, "violin-spic/", "violin-spic/",
                                    (count, total) -> status(request, "載入跳弓 " + count + "/" + total));
                        } catch (Cancelled cancelled) {
                            throw cancelled;
                        } catch (Exception | OutOfMemoryError e) {
                            android.util.Log.e("MiniPianoPlayer", "Spiccato bank load failed", e);
                        }
                        if ((signature & PIANO) == 0) {
                            try {
                                low = loadBank(assets, request, "piano-low/", "piano/",
                                        (count, total) -> status(request, "載入低音鋼琴 " + count + "/" + total));
                            } catch (Cancelled cancelled) {
                                throw cancelled;
                            } catch (Exception | OutOfMemoryError e) {
                                android.util.Log.e("MiniPianoPlayer", "Low piano bank load failed", e);
                            }
                        }
                    }
                }
                String status = statusFor(actualSignature, piano, violin, spic, low);
                publish(request, actualSignature, piano, violin, spic, low, status);
                android.util.Log.i("MiniPianoPlayer", "Sample banks loaded: signature=" + signature
                        + ", piano=" + (piano == null ? 0 : piano.decodedBytes / 1024 / 1024)
                        + " MiB, violin=" + (violin == null ? 0 : violin.decodedBytes / 1024 / 1024)
                        + " MiB, spic=" + (spic == null ? 0 : spic.decodedBytes / 1024 / 1024)
                        + " MiB, low=" + (low == null ? 0 : low.decodedBytes / 1024 / 1024) + " MiB");
            } catch (Cancelled ignored) {
                // A more recent selection owns the state; obsolete PCM is released.
            } catch (Exception | OutOfMemoryError e) {
                synchronized (SampleBankStore.class) {
                    if (generation == request) {
                        String detail = e.getMessage();
                        state = new State(signature, null, null, null, null,
                                detail == null || detail.isEmpty() ? "載入失敗，暫用合成" : "載入失敗，暫用合成：" + detail);
                        generation++;
                    }
                }
                android.util.Log.e("MiniPianoPlayer", "Sample bank load failed", e);
            }
        });
    }
    private static String statusFor(int signature, SampleBank piano, SampleBank violin, SampleBank spic, SampleBank low) {
        if (signature == (PIANO | VIOLIN) && piano != null && violin != null) {
            return "已載入 · 鋼琴＋小提琴" + (spic != null ? " · 跳弓" : "");
        }
        if (signature == PIANO && piano != null) return "已載入 · 鋼琴";
        if (signature == VIOLIN && violin != null) {
            return "已載入 · 小提琴" + (spic != null ? " · 跳弓" : "") + (low != null ? " · 低音鋼琴" : "");
        }
        return "載入失敗，暫用合成";
    }
    private static SampleBank loadBank(AssetManager assets, long request, String manifestDir, String sampleDir,
                                       SampleBank.Progress progress) throws Exception {
        return SampleBank.load(name -> {
            check(request);
            if (name.equals("instrument.json")) return assets.open(manifestDir + "instrument.json");
            return assets.open(sampleDir + name);
        }, (name, root) -> {
            check(request);
            String path = sampleDir + name;
            if (name.endsWith(".flac")) return FlacSampleReader.read(assets, path, root);
            try (InputStream input = assets.open(path)) { return PianoSample.read(input, root); }
        }, progress);
    }
    private static synchronized void check(long request) { if (request != generation) throw new Cancelled(); }
    private static synchronized void status(long request, String status) {
        check(request);
        State current = state;
        state = new State(current.signature, current.piano, current.violin, current.spic, current.low, status);
    }
    private static synchronized void publish(long request, int signature, SampleBank piano, SampleBank violin,
                                             SampleBank spic, SampleBank low, String status) {
        check(request); state = new State(signature, piano, violin, spic, low, status);
    }
}
