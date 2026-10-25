package com.minipianoplayer.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.ContextThemeWrapper;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int LIBRARY = 103;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService files = Executors.newSingleThreadExecutor();
    private PianoAudio audio;
    private PlaybackController playback;
    private PianoView piano;
    private ScoreLibrary library;
    private String currentId;
    private Button libraryButton;
    private boolean pendingLibraryPlay;
    private Score score = new Score();
    private UiTheme theme;
    private LinearLayout mainRoot, miniPlayer, stageCard;
    private TextView title, subtitle, detail, time;
    private TextView soundStatus;
    private String lastSoundStatus = "";
    private String lastScoreDetail = "";
    private String lastTime = "";
    private String shownTitle = "";
    private String shownSubtitle = "";
    private boolean lastPlaying, lastIconPlaying;
    private long lastDrawPosition = Long.MIN_VALUE, lastDetailUpdate;
    private Button speedButton;
    private ImageButton play, stop, moreButton;
    private java.util.List<Score.Channel> channels = java.util.Collections.emptyList();
    private SeekBar progress;
    private boolean busy, dragging, resumed, screenOn, playRequested;
    private long playRequestedAt;
    private double dragSeconds = -1;
    private int speedIndex = 2;
    private final double[] speeds = {.5, .75, 1, 1.25, 1.5};
    private long scoreLength;
    private double durationSeconds;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            boolean playing = audio.playing;
            if (playing) playRequested = false;
            keepScreenOn(playing);
            syncPlayback();
            long position = (long) audio.position;
            if (!dragging && !score.notes.isEmpty()) {
                int value = (int) (score.seconds(position) * 1000 / Math.max(.001, durationSeconds));
                if (progress.getProgress() != value) progress.setProgress(value);
            }
            updatePlaybackButtons();
            updateTime(dragging && dragSeconds >= 0 ? dragSeconds : score.seconds(position));
            long now = SystemClock.uptimeMillis();
            if (now - lastDetailUpdate >= 200) {
                lastDetailUpdate = now;
                if (!score.notes.isEmpty()) {
                    Score.Measure measure = score.measureAt(position);
                    String info = score.notes.size() + " 音符 · " + channels.size() + " 聲部 · 樂曲 "
                        + String.format(Locale.ROOT, "%.1f BPM", score.timeline().bpmAt(position) * speeds[speedIndex])
                        + (measure == null ? "" : " · 原譜第 " + measure.number + " 小節（第 " + measure.visit + " 次）")
                        + (score.mutedVoices.isEmpty() ? "" : " · 靜音 " + score.mutedVoices.size() + " 聲部")
                        + " · " + piano.rangeLabel();
                    if (!info.equals(lastScoreDetail)) { detail.setText(info); lastScoreDetail = info; }
                }
                String sound = audio.soundStatus();
                if (!sound.equals(lastSoundStatus)) { soundStatus.setText(sound); soundStatus.setContentDescription(sound); lastSoundStatus = sound; }
            }
            if (playing || playing != lastPlaying || position != lastDrawPosition) piano.invalidate();
            lastPlaying = playing;
            lastDrawPosition = position;
            if (audio.error != null) { String error = audio.error; audio.error = null; message("音訊無法啟動", error); }
            boolean starting = playRequested && SystemClock.uptimeMillis() - playRequestedAt < 2000;
            handler.postDelayed(this, playing || starting ? 33 : 1000);
        }
    };
    private void refreshNow() {
        handler.removeCallbacks(refresh);
        if (resumed) handler.post(refresh);
    }
    @Override public void onCreate(Bundle state) {
        theme = new UiTheme(this); setTheme(theme.dark ? R.style.AppTheme_Dark : R.style.AppTheme);
        super.onCreate(state);
        playback = PlaybackController.acquire(this); audio = playback.audio; library = new ScoreLibrary(this);
        speedIndex = playback.speedIndex;
        createUi(); applyTheme(); setBusy(true);
        if (playback.score != null) { currentId = playback.id; showScore(playback.score); setBusy(false); return; }
        files.execute(() -> {
            try { library.migrate(); }
            catch (Exception e) { runOnUiThread(() -> toast("舊音譜移轉失敗：" + e.getMessage())); }
            try {
                String id = library.selected(); Score saved = id == null ? null : library.load(id);
                PlaybackProgram compiled = saved == null ? null : new PlaybackProgram(saved);
                runOnUiThread(() -> {
                    if (isDestroyed()) return; currentId = id;
                    if (compiled == null) useScore(emptyScore()); else useScore(saved, compiled);
                    setBusy(false);
                });
            } catch (Exception e) {
                runOnUiThread(() -> { setBusy(false); message("無法還原樂譜", e.getMessage()); });
            }
        });
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView label(String text, float size, int color) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color); view.setGravity(Gravity.CENTER_VERTICAL);
        if (color == 0xFF70847B || color == 0xFF687D73) view.setTag("muted");
        return view;
    }
    private LinearLayout row() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.HORIZONTAL); layout.setGravity(Gravity.CENTER_VERTICAL); return layout; }
    private Button button(String text, LinearLayout parent, Runnable action) {
        Button button = new Button(this); button.setText(text); button.setTextSize(14); button.setAllCaps(false);
        button.setMinWidth(0); button.setMinimumWidth(0); button.setMinHeight(0); button.setMinimumHeight(0);
        button.setTextColor(0xFF244B43); button.setPadding(dp(14), 0, dp(14), 0);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(0xFFE1EFE8); bg.setCornerRadius(dp(13)); button.setBackground(bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(44)); params.setMargins(0, 0, dp(7), 0); parent.addView(button, params);
        button.setOnClickListener(v -> action.run()); return button;
    }
    private ImageButton playbackButton(LinearLayout parent, int icon, String description, int endMarginDp, Runnable action) {
        ImageButton button = new ImageButton(this); button.setImageResource(icon); button.setContentDescription(description);
        button.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(48), dp(48)); params.setMargins(0, 0, dp(endMarginDp), 0);
        parent.addView(button, params); button.setOnClickListener(v -> action.run()); return button;
    }
    private void speedMenu() {
        String[] labels = {"0.5×", "0.75×", "1×", "1.25×", "1.5×"};
        dialog().setTitle("播放速度").setSingleChoiceItems(labels, speedIndex, (d, which) -> {
            speedIndex = which; playback.speedIndex = which; audio.speed(speeds[which]); speedButton.setText(labels[which]);
            updateTime(dragging && dragSeconds >= 0 ? dragSeconds : score.seconds((long) audio.position));
            d.dismiss();
        }).setNegativeButton("取消", null).show();
    }
    private void moreMenu() {
        dialog().setTitle("更多選項").setItems(new String[]{"音色", "聲部", "匯入摘要"}, (d, which) -> {
            if (which == 0) soundSettings();
            else if (which == 1) { if (channels.isEmpty()) toast("目前沒有可調整的聲部"); else voiceSettings(); }
            else { if (score.notes.isEmpty()) toast("目前沒有匯入摘要"); else message("匯入摘要", score.importSummary()); }
        }).show();
    }
    private Button dialogButton(LinearLayout parent, String text, boolean weighted, Runnable action) {
        Button button = new Button(this); button.setText(text); button.setTextSize(14); button.setAllCaps(false);
        button.setMinWidth(0); button.setMinimumWidth(0); button.setMinHeight(0); button.setMinimumHeight(0); button.setPadding(dp(8), 0, dp(8), 0);
        button.setTextColor(theme.text); button.setBackground(UiTheme.rounded(theme.surface, dp(12)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(weighted ? 0 : -2, dp(46), weighted ? 1 : 0); params.setMargins(0, dp(3), dp(8), dp(3));
        parent.addView(button, params); button.setOnClickListener(v -> action.run()); return button;
    }
    private void selectionStyle(Button button, String text, boolean selected) {
        button.setSelected(selected);
        button.setText((selected ? "✓ " : "") + text);
        button.setTypeface(Typeface.create("sans-serif", selected ? Typeface.BOLD : Typeface.NORMAL));
        button.setTextColor(selected ? UiTheme.foreground(theme.primary) : theme.text);
        GradientDrawable bg = UiTheme.rounded(selected ? theme.primary : theme.surface, dp(12));
        if (!selected) bg.setStroke(dp(2), theme.primary);
        button.setBackground(bg);
    }
    private void soundSettings() {
        int selected = PianoSound.selected(this);
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(20), dp(8), dp(20), dp(4));
        String[] tags = {"autoSound", "sampledPiano", "sampledViolin", "syntheticPiano"};
        final int count = PianoSound.SELECTABLE.length;
        final Button[] buttons = new Button[count];
        final TextView violinNote = label("", 11, theme.muted);
        LinearLayout firstRow = row(), secondRow = row();
        content.addView(firstRow); content.addView(secondRow);
        for (int i = 0; i < count; i++) {
            final int sound = PianoSound.SELECTABLE[i];
            Button button = new Button(this); button.setTextSize(12); button.setAllCaps(false);
            button.setMinWidth(0); button.setMinimumWidth(0); button.setMinHeight(0); button.setMinimumHeight(0);
            button.setPadding(dp(4), 0, dp(4), 0); button.setTag(tags[i]);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1); params.setMargins(0, dp(3), dp(4), dp(3));
            (i < 2 ? firstRow : secondRow).addView(button, params);
            button.setOnClickListener(v -> {
                PianoSound.select(this, sound); audio.configure(this);
                for (int j = 0; j < count; j++) selectionStyle(buttons[j], PianoSound.name(PianoSound.SELECTABLE[j]), PianoSound.selected(this) == PianoSound.SELECTABLE[j]);
                updateSoundNote(violinNote, PianoSound.selected(this));
            });
            buttons[i] = button;
        }
        violinNote.setPadding(0, dp(5), 0, 0); content.addView(violinNote);
        updateSoundNote(violinNote, selected);
        boolean lowStringOn = UiTheme.preferences(this).getBoolean("stringLowViolin", true);
        CheckBox lowStrings = checkBox(content, "弦樂低音使用小提琴（移調）", lowStringOn, enabled -> {
            UiTheme.preferences(MainActivity.this).edit().putBoolean("stringLowViolin", enabled).apply();
            audio.configure(MainActivity.this);
        });
        lowStrings.setTag("stringLowViolin");
        int violinVolume = Math.max(50, Math.min(100, UiTheme.preferences(this).getInt("violinVolume", 75)));
        TextView violinVolumeLabel = label("小提琴音量 " + violinVolume + "%", 14, theme.text);
        violinVolumeLabel.setPadding(0, dp(8), 0, 0); content.addView(violinVolumeLabel);
        SeekBar violinVolumeBar = new SeekBar(this);
        violinVolumeBar.setTag("violinVolume"); violinVolumeBar.setMax(100); violinVolumeBar.setMin(50); violinVolumeBar.setProgress(violinVolume);
        violinVolumeBar.setThumbTintList(ColorStateList.valueOf(theme.primary));
        violinVolumeBar.setProgressTintList(ColorStateList.valueOf(theme.primary));
        violinVolumeBar.setProgressBackgroundTintList(ColorStateList.valueOf(UiTheme.blend(theme.surface, theme.muted, .45f)));
        violinVolumeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int value, boolean user) {
                if (user) { UiTheme.preferences(MainActivity.this).edit().putInt("violinVolume", value).apply(); violinVolumeLabel.setText("小提琴音量 " + value + "%"); audio.setViolinLevel(value / 100.0); }
            }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        content.addView(violinVolumeBar, new LinearLayout.LayoutParams(-1, dp(44)));
        TextView status = label("", 12, theme.muted); status.setPadding(0, dp(8), 0, dp(4)); content.addView(status);
        boolean reverbOn = UiTheme.preferences(this).getBoolean("pianoReverb", false);
        int reverbMix = Math.max(0, Math.min(60, UiTheme.preferences(this).getInt("pianoReverbMix", 18)));
        final SeekBar[] reverbBar = new SeekBar[1];
        TextView reverbLabel = label("殘響量 " + reverbMix + "%", 14, theme.text);
        CheckBox reverb = checkBox(content, "空間殘響（預設關閉）", reverbOn, enabled -> {
            UiTheme.preferences(MainActivity.this).edit().putBoolean("pianoReverb", enabled).apply();
            SeekBar bar = reverbBar[0];
            if (bar != null) {
                bar.setEnabled(enabled);
                bar.setAlpha(enabled ? 1f : .65f);
                bar.setThumbTintList(ColorStateList.valueOf(enabled ? theme.primary : theme.muted));
            }
            reverbLabel.setAlpha(enabled ? 1f : .65f);
            audio.setReverb(enabled, (bar == null ? reverbMix : bar.getProgress()) / 100.0);
        });
        reverb.setTag("pianoReverb");
        content.addView(reverbLabel);
        SeekBar reverbAmount = new SeekBar(this); reverbBar[0] = reverbAmount; reverbAmount.setTag("pianoReverbMix"); reverbAmount.setMax(60); reverbAmount.setProgress(reverbMix);
        reverbLabel.setAlpha(reverbOn ? 1f : .65f);
        reverbAmount.setEnabled(reverbOn);
        reverbAmount.setAlpha(reverbOn ? 1f : .65f);
        reverbAmount.setThumbTintList(ColorStateList.valueOf(reverbOn ? theme.primary : theme.muted));
        reverbAmount.setProgressTintList(ColorStateList.valueOf(theme.primary));
        reverbAmount.setProgressBackgroundTintList(ColorStateList.valueOf(UiTheme.blend(theme.surface, theme.muted, .45f)));
        reverbAmount.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int value, boolean user) {
                if (user) { UiTheme.preferences(MainActivity.this).edit().putInt("pianoReverbMix", value).apply(); reverbLabel.setText("殘響量 " + value + "%"); audio.setReverb(reverb.isChecked(), value / 100.0); }
            }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        content.addView(reverbAmount, new LinearLayout.LayoutParams(-1, dp(44)));
        Button credits = dialogButton(content, "音色來源與授權", false, () -> showCredits(false)); credits.setTag("soundCredits");
        for (int j = 0; j < count; j++) selectionStyle(buttons[j], PianoSound.name(PianoSound.SELECTABLE[j]), selected == PianoSound.SELECTABLE[j]);
        status.setText(soundDialogStatus(selected));
        ScrollView scroll = new ScrollView(this); scroll.addView(content);
        final boolean[] open = {true};
        final Runnable tick = new Runnable() {
            @Override public void run() {
                if (!open[0]) return;
                int current = PianoSound.selected(MainActivity.this);
                status.setText(soundDialogStatus(current));
                updateSoundNote(violinNote, current);
                handler.postDelayed(this, 400);
            }
        };
        AlertDialog shown = dialog().setTitle("音色").setView(scroll).setPositiveButton("關閉", null).create();
        shown.setOnDismissListener(d -> open[0] = false);
        shown.show(); handler.post(tick);
    }
    private String soundStatusText(int sound) {
        String status = sound == PianoSound.AUTO ? SampleBankStore.status() : SampleBankStore.status(sound);
        return PianoSound.name(sound) + " · " + status;
    }
    private String soundDialogStatus(int sound) { return soundStatusText(sound) + "\n" + audio.audioStatus(); }
    private static void updateSoundNote(TextView note, int sound) {
        if (sound == PianoSound.AUTO) {
            note.setText("自動：依 MusicXML 聲部分配鋼琴／小提琴");
            note.setVisibility(View.VISIBLE);
        } else if (sound == PianoSound.VIOLIN) {
            note.setText("小提琴：低音以鋼琴補位，短音自動跳弓");
            note.setVisibility(View.VISIBLE);
        } else {
            note.setVisibility(View.GONE);
        }
    }
    private CheckBox checkBox(LinearLayout parent, String text, boolean checked, java.util.function.Consumer<Boolean> onChange) {
        CheckBox box = new CheckBox(this); box.setText(text); box.setTextColor(theme.text); box.setButtonTintList(ColorStateList.valueOf(theme.primary));
        box.setMinHeight(dp(48)); box.setPadding(dp(8), 0, dp(8), 0); box.setChecked(checked);
        box.setOnCheckedChangeListener((v, enabled) -> onChange.accept(enabled)); parent.addView(box); return box;
    }
    private void showCredits(boolean fullLicense) {
        int sound = PianoSound.selected(this);
        if (sound == PianoSound.SYNTHETIC) {
            dialog().setTitle("合成鋼琴").setMessage("由 APP 的正弦查表、泛音與衰減包絡產生，沒有使用外部鋼琴採樣。").setPositiveButton("關閉", null).show(); return;
        }
        String directory = PianoSound.directory(sound);
        String content;
        try (InputStream input = getAssets().open(directory + (fullLicense ? "/LICENSE.txt" : "/ATTRIBUTION.txt"))) {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            content = new String(output.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) { content = "無法讀取音色授權：" + e.getMessage(); }
        TextView text = new TextView(this); text.setText(content); text.setTextColor(theme.text); text.setTextSize(14); text.setPadding(dp(20), dp(12), dp(20), dp(12)); text.setTextIsSelectable(true);
        text.setLinkTextColor(theme.dark ? UiTheme.blend(theme.primary, android.graphics.Color.WHITE, .35f) : 0xFF175A98);
        android.text.util.Linkify.addLinks(text, android.text.util.Linkify.WEB_URLS);
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(theme.background); scroll.addView(text);
        AlertDialog.Builder dialog = dialog();
        dialog.setTitle(fullLicense ? "完整授權" : "音色來源與授權").setView(scroll).setPositiveButton("關閉", null);
        if (!fullLicense) dialog.setNeutralButton("完整授權", (d, which) -> showCredits(true)); dialog.show();
    }
    private void createUi() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(0xFFF2F9F5);
        mainRoot = root; UiTheme.insets(root, dp(10), false);
        LinearLayout header = row();
        LinearLayout titleBlock = new LinearLayout(this); titleBlock.setOrientation(LinearLayout.VERTICAL); titleBlock.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = label("鋼琴曲播放", 23, 0xFF183D35); brand.setTypeface(null, Typeface.BOLD); titleBlock.addView(brand);
        soundStatus = label("採樣音色載入中", 11, 0xFF70847B); soundStatus.setSingleLine(true); soundStatus.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleBlock.addView(soundStatus, new LinearLayout.LayoutParams(-1, dp(18)));
        header.addView(titleBlock, new LinearLayout.LayoutParams(0, -1, 1));
        libraryButton = button("音譜庫", header, this::openLibrary);
        libraryButton.setTextSize(13); libraryButton.setPadding(dp(10), 0, dp(10), 0);
        ImageButton gear = new ImageButton(this); gear.setImageResource(R.drawable.ic_settings); gear.setContentDescription("設定"); gear.setTag("settingsGear");
        gear.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER); gear.setPadding(dp(9), dp(9), dp(9), dp(9));
        gear.setOnClickListener(v -> settings()); header.addView(gear, new LinearLayout.LayoutParams(dp(40), dp(40)));
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(58)));
        detail = label("請到音譜庫按右上角 + 匯入 MusicXML 樂譜", 11, 0xFF70847B);
        detail.setMaxLines(2);
        detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
        detail.setPadding(0, dp(2), 0, dp(6));
        root.addView(detail, new LinearLayout.LayoutParams(-1, -2));
        stageCard = new LinearLayout(this); stageCard.setOrientation(LinearLayout.VERTICAL);
        stageCard.setBackground(UiTheme.rounded(theme.surface, dp(16)));
        stageCard.setClipToOutline(true);
        piano = new PianoView(this, audio);
        piano.setOnSeekListener(new PianoView.SeekListener() {
            @Override public void onSeek(double seconds) { seekTo(seconds); }
            @Override public void onSeekPreview(double seconds) { previewSeek(seconds); }
            @Override public void onSeekCancel() { cancelSeekPreview(); }
        });
        stageCard.addView(piano, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout.LayoutParams stageParams = new LinearLayout.LayoutParams(-1, 0, 1);
        stageParams.setMargins(dp(10), dp(2), dp(10), dp(8));
        root.addView(stageCard, stageParams);
        miniPlayer = new LinearLayout(this); miniPlayer.setOrientation(LinearLayout.VERTICAL);
        miniPlayer.setBackground(UiTheme.rounded(theme.surface, dp(16)));
        miniPlayer.setPadding(dp(10), dp(7), dp(10), dp(8));
        LinearLayout info = new LinearLayout(this); info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout titleRow = row();
        title = label("尚未匯入樂譜", 14, 0xFF224B40); title.setSingleLine(true); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        moreButton = new ImageButton(this); moreButton.setImageResource(R.drawable.ic_more); moreButton.setContentDescription("更多選項"); moreButton.setTag("moreMenu");
        moreButton.setPadding(dp(7), dp(7), dp(7), dp(7));
        LinearLayout.LayoutParams moreParams = new LinearLayout.LayoutParams(dp(32), dp(32)); moreParams.setMargins(dp(4), 0, 0, 0);
        titleRow.addView(moreButton, moreParams); moreButton.setOnClickListener(v -> moreMenu());
        info.addView(titleRow, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout subtitleRow = row();
        subtitle = label("", 11, 0xFF70847B); subtitle.setSingleLine(true); subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        subtitle.setTag("muted"); subtitle.setVisibility(View.INVISIBLE);
        subtitleRow.addView(subtitle, new LinearLayout.LayoutParams(0, -2, 1));
        time = label("0:00 / 0:00", 12, 0xFF224B40); time.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(-2, -2); timeParams.setMargins(dp(8), 0, 0, 0);
        subtitleRow.addView(time, timeParams);
        info.addView(subtitleRow, new LinearLayout.LayoutParams(-1, -2));
        info.setMinimumHeight(dp(34));
        miniPlayer.addView(info, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout controls = row();
        play = playbackButton(controls, R.drawable.ic_play, "播放", 2, () -> {
            if (audio.playing) {
                playRequested = false;
                playback.pause();
            } else {
                playRequested = true;
                playRequestedAt = SystemClock.uptimeMillis();
                playback.play();
            }
            refreshNow();
        });
        stop = playbackButton(controls, R.drawable.ic_stop, "停止", 7, this::stopPlayback);
        progress = new SeekBar(this); progress.setMax(1000); progress.setTag("playbackProgress"); progress.setContentDescription("播放進度"); styleProgress(progress);
        controls.addView(progress, new LinearLayout.LayoutParams(0, dp(38), 1));
        speedButton = button(new String[]{"0.5×", "0.75×", "1×", "1.25×", "1.5×"}[speedIndex], controls, this::speedMenu);
        speedButton.setTextSize(13); speedButton.setPadding(dp(10), 0, dp(10), 0); speedButton.setContentDescription("播放速度");
        LinearLayout.LayoutParams speedParams = (LinearLayout.LayoutParams) speedButton.getLayoutParams();
        speedParams.setMargins(dp(8), 0, 0, 0);
        speedButton.setLayoutParams(speedParams);
        miniPlayer.addView(controls, new LinearLayout.LayoutParams(-1, dp(50)));
        LinearLayout.LayoutParams miniParams = new LinearLayout.LayoutParams(-1, -2);
        miniParams.setMargins(dp(10), 0, dp(10), dp(4));
        root.addView(miniPlayer, miniParams);
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int value, boolean user) {
                if (!user || score.notes.isEmpty()) return;
                dragSeconds = durationSeconds * value / 1000.0;
                updateTime(dragSeconds);
            }
            public void onStartTrackingTouch(SeekBar bar) {
                dragging = true;
                dragSeconds = score.notes.isEmpty() ? -1 : score.seconds((long) audio.position);
            }
            public void onStopTrackingTouch(SeekBar bar) {
                double target = dragSeconds >= 0 ? dragSeconds : durationSeconds * bar.getProgress() / 1000.0;
                audio.seek(score.tick(target));
                dragging = false;
                dragSeconds = -1;
                lastTime = "";
                handler.removeCallbacks(refresh);
                handler.postDelayed(refresh, 60);
            }
        });
        setContentView(root);
    }
    private void setBusy(boolean value) { busy = value; updateEnabled(); }
    private void updateEnabled() {
        boolean idle = !busy;
        libraryButton.setEnabled(idle);
        updatePlaybackButtons(); speedButton.setEnabled(idle); moreButton.setEnabled(idle);
        progress.setEnabled(idle && !score.notes.isEmpty());
    }
    private void updatePlaybackButtons() {
        boolean available = !busy && !score.notes.isEmpty(), playing = audio.playing;
        if (play.isEnabled() != available) play.setEnabled(available);
        if (stop.isEnabled() != available) stop.setEnabled(available);
        if (playing != lastIconPlaying) {
            lastIconPlaying = playing;
            play.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
            play.setContentDescription(playing ? "暫停" : "播放");
        }
        float playAlpha = play.isEnabled() ? 1f : .4f, stopAlpha = stop.isEnabled() ? 1f : .4f;
        if (play.getAlpha() != playAlpha) play.setAlpha(playAlpha);
        if (stop.getAlpha() != stopAlpha) stop.setAlpha(stopAlpha);
    }
    private void useScore(Score value) { useScore(value, new PlaybackProgram(value)); }
    private void useScore(Score value, PlaybackProgram compiled) { playback.load(currentId, value, compiled); showScore(value); }
    private void showScore(Score value) {
        score = value; scoreLength = value.length(); durationSeconds = score.seconds(scoreLength); channels = score.channels(); shownTitle = value.title; title.setText(value.title); lastScoreDetail = "";
        shownSubtitle = value.movementSubtitle(); subtitle.setText(shownSubtitle); subtitle.setVisibility(shownSubtitle.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        dragging = false; dragSeconds = -1;
        piano.setScore(value);
        detail.setText(value.notes.isEmpty()
            ? "請到音譜庫按右上角 + 匯入 MusicXML 樂譜"
            : value.notes.size() + " 個音符 · " + channels.size() + " 聲部 · " + value.timeline().size() + " 速度段 · " + value.beats + "/" + value.beatType + " · " + piano.rangeLabel());
        updateEnabled();
    }
    private void syncPlayback() {
        if (playback.score == null) return;
        if (score != playback.score) { currentId = playback.id; showScore(playback.score); }
        else if (!score.title.equals(shownTitle)) { shownTitle = score.title; title.setText(score.title); }
        String movement = score.movementSubtitle();
        if (!movement.equals(shownSubtitle)) {
            shownSubtitle = movement; subtitle.setText(movement);
            subtitle.setVisibility(movement.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        }
    }
    private void voiceSettings() {
        String[] names = new String[channels.size()]; boolean[] audible = new boolean[channels.size()];
        for (int i = 0; i < channels.size(); i++) { names[i] = channels.get(i).label; audible[i] = !score.mutedVoices.contains(channels.get(i).key); }
        AlertDialog shown = dialog().setTitle("聲部（勾選＝播放）").setMultiChoiceItems(names, audible, (d, which, enabled) -> {
            String key = channels.get(which).key; if (enabled) score.mutedVoices.remove(key); else score.mutedVoices.add(key);
            audio.mute(which, !enabled); save(score);
        }).setPositiveButton("完成", null).create();
        shown.show();
        android.widget.ListView list = shown.getListView();
        list.setVerticalScrollBarEnabled(false);
        list.setScrollbarFadingEnabled(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        int maxHeight = Math.min(dp(600), Math.round(getResources().getDisplayMetrics().heightPixels * .72f));
        int contentHeight = channels.size() * dp(48) + dp(16);
        int targetHeight = Math.min(maxHeight, Math.max(dp(120), contentHeight));
        list.setMinimumHeight(targetHeight);
        list.getLayoutParams().height = targetHeight;
        list.setLayoutParams(list.getLayoutParams());
    }
    private void previewSeek(double seconds) {
        if (score.notes.isEmpty()) return;
        double target = Math.max(0, Math.min(durationSeconds, seconds));
        dragging = true; dragSeconds = target;
        progress.setProgress((int) (target * 1000 / Math.max(.001, durationSeconds)));
        updateTime(target);
    }
    private void cancelSeekPreview() {
        if (score.notes.isEmpty()) return;
        double current = score.seconds((long) audio.position);
        dragging = false; dragSeconds = -1; lastTime = "";
        progress.setProgress((int) (current * 1000 / Math.max(.001, durationSeconds)));
        updateTime(current);
    }
    private void seekTo(double seconds) {
        if (score.notes.isEmpty()) return;
        double target = Math.max(0, Math.min(durationSeconds, seconds));
        audio.seek(score.tick(target));
        dragging = false; dragSeconds = -1; lastTime = "";
        progress.setProgress((int) (target * 1000 / Math.max(.001, durationSeconds)));
        updateTime(target);
        handler.removeCallbacks(refresh);
        handler.postDelayed(refresh, 60);
    }
    private void stopPlayback() {
        playRequested = false;
        audio.stop();
        dragging = false; dragSeconds = -1;
        progress.setProgress(0); updateTime(0); updatePlaybackButtons();
        refreshNow();
    }
    private void save(Score value) {
        String id = currentId; if (id == null) return;
        Score snapshot = value.copy(); files.execute(() -> { try { library.update(id, snapshot); } catch (Exception e) { runOnUiThread(() -> toast("樂譜儲存失敗：" + e.getMessage())); } });
    }
    private Score emptyScore() { Score value = new Score(); value.title = "尚未匯入樂譜"; return value; }
    private void openLibrary() {
        if (busy) return;
        setBusy(true);
        files.execute(() -> runOnUiThread(() -> {
            if (isDestroyed()) return;
            setBusy(false); startActivityForResult(new Intent(this, ScoreLibraryActivity.class), LIBRARY);
        }));
    }
    private void reloadLibrary(boolean playNow) {
        setBusy(true);
        files.execute(() -> {
            try {
                String id = library.selected(); Score selected = id == null ? emptyScore() : library.load(id);
                boolean reload = playNow || !java.util.Objects.equals(id, playback.id);
                PlaybackProgram compiled = reload ? new PlaybackProgram(selected) : null;
                runOnUiThread(() -> { if (isDestroyed()) return;
                    if (reload) { currentId = id; useScore(selected, compiled); }
                    else if (playback.score != null) { playback.score.title = selected.title; playback.score.movementTitle = selected.movementTitle; syncPlayback(); }
                    setBusy(false); if (playNow && !selected.notes.isEmpty()) { if (resumed) playback.play(); else pendingLibraryPlay = true; }
                });
            } catch (Exception e) { runOnUiThread(() -> { if (isDestroyed()) return; currentId = null; useScore(emptyScore()); setBusy(false); message("無法載入樂譜", e.getMessage()); }); }
        });
    }
    private void settings() { startActivity(new Intent(this, SettingsActivity.class)); }
    private void styleButton(Button button) {
        button.setSelected(false); button.setTextColor(theme.text); button.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        button.setBackground(UiTheme.rounded(theme.surface, dp(13)));
    }
    private void tint(View view) {
        if (view instanceof ImageButton) {
            ImageButton image = (ImageButton) view; image.setImageTintList(ColorStateList.valueOf(theme.text));
            image.setBackground("settingsGear".equals(view.getTag()) ? null : UiTheme.rounded(theme.surface, dp(14)));
        } else if (view instanceof Button) styleButton((Button) view);
        else if (view instanceof TextView) ((TextView) view).setTextColor("muted".equals(view.getTag()) ? theme.muted : theme.text);
        if (view instanceof SeekBar) {
            SeekBar bar = (SeekBar) view;
            if ("playbackProgress".equals(view.getTag())) styleProgress(bar);
            else { bar.setThumbTintList(ColorStateList.valueOf(theme.primary)); bar.setProgressTintList(ColorStateList.valueOf(theme.primary)); bar.setProgressBackgroundTintList(ColorStateList.valueOf(theme.surface)); }
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view; for (int i = 0; i < group.getChildCount(); i++) tint(group.getChildAt(i));
        }
    }
    private void styleProgress(SeekBar bar) {
        GradientDrawable track = UiTheme.rounded(UiTheme.blend(theme.surface, theme.primary, .22f), dp(5));
        GradientDrawable fill = UiTheme.rounded(theme.primary, dp(5));
        android.graphics.drawable.ClipDrawable clip = new android.graphics.drawable.ClipDrawable(fill, Gravity.START, android.graphics.drawable.ClipDrawable.HORIZONTAL);
        android.graphics.drawable.LayerDrawable layer = new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{track, clip});
        layer.setId(0, android.R.id.background); layer.setId(1, android.R.id.progress);
        bar.setProgressTintList(null); bar.setProgressBackgroundTintList(null); bar.setProgressDrawable(layer);
        GradientDrawable thumb = new GradientDrawable(); thumb.setShape(GradientDrawable.OVAL);
        thumb.setColor(theme.primary); thumb.setSize(dp(10), dp(10));
        bar.setThumb(thumb); bar.setThumbOffset(0);
        bar.setPadding(dp(6), dp(4), dp(6), dp(4));
    }
    private void applyTheme() {
        theme = new UiTheme(this); mainRoot.setBackgroundColor(theme.background);
        if (stageCard != null) stageCard.setBackground(UiTheme.rounded(theme.surface, dp(16)));
        if (miniPlayer != null) miniPlayer.setBackground(UiTheme.rounded(theme.surface, dp(16)));
        tint(mainRoot); piano.theme(theme); theme.window(this, false);
    }
    private AlertDialog.Builder dialog() { return new AlertDialog.Builder(new ContextThemeWrapper(this, theme.dark ? R.style.AppTheme_Dark : R.style.AppTheme)); }
    @Override public void onConfigurationChanged(Configuration configuration) { super.onConfigurationChanged(configuration); if (mainRoot != null) applyTheme(); }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != LIBRARY) return;
        String id = data == null ? null : data.getStringExtra("playId");
        if (id != null) library.select(id);
        reloadLibrary(id != null);
    }
    private void updateTime(double sourceSeconds) {
        String current = formatTime(sourceSeconds / speeds[speedIndex]), total = formatTime(durationSeconds / speeds[speedIndex]);
        String text = current + " / " + total;
        if (!text.equals(lastTime)) {
            time.setText(text); lastTime = text;
            progress.setContentDescription("播放進度 " + text);
        }
    }
    private static String formatTime(double seconds) {
        int value = (int) Math.max(0, seconds), minute = value / 60, second = value % 60;
        return minute + ":" + (second < 10 ? "0" : "") + second;
    }
    private void toast(String text) { if (!isDestroyed()) Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    private void message(String title, String text) { if (!isDestroyed()) dialog().setTitle(title).setMessage(text == null ? "請稍後再試" : text).setPositiveButton("知道了", null).show(); }
    private void keepScreenOn(boolean on) {
        if (screenOn == on) return;
        screenOn = on;
        if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    @Override protected void onStart() {
        super.onStart();
        if (playback != null) playback.uiVisible = true;
    }
    @Override protected void onStop() {
        if (playback != null) playback.uiVisible = false;
        keepScreenOn(false);
        super.onStop();
    }
    @Override protected void onResume() {
        super.onResume();
        audio.configure(this); syncPlayback(); applyTheme(); resumed = true; refreshNow();
        if (pendingLibraryPlay) { pendingLibraryPlay = false; playRequested = true; playRequestedAt = SystemClock.uptimeMillis(); playback.play(); }
    }
    @Override protected void onPause() {
        resumed = false; keepScreenOn(false); handler.removeCallbacks(refresh); super.onPause();
    }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); playback.release(); files.shutdown(); super.onDestroy(); }
}
