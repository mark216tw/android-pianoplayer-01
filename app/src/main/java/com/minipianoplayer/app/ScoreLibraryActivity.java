package com.minipianoplayer.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ScoreLibraryActivity extends Activity {
    private static final int IMPORT = 1;
    private static final String[] SORT_NAMES = {"最近加入", "名稱", "演奏時間"};
    private final ExecutorService files = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Button> playButtons = new LinkedHashMap<>();
    private final Map<String, String> playStates = new LinkedHashMap<>();
    private final Runnable playbackRefresh = new Runnable() {
        @Override public void run() {
            updatePlayButtons();
            handler.postDelayed(this, 500);
        }
    };
    private PlaybackController playback;
    private ScoreLibrary library;
    private UiTheme theme;
    private LinearLayout body;
    private EditText search;
    private Button sortButton;
    private TextView count;
    private boolean busy;
    private String query = "";
    private int sortMode;
    private List<ScoreLibrary.Entry> entries = Collections.emptyList();

    @Override public void onCreate(Bundle state) {
        theme = new UiTheme(this); setTheme(theme.dark ? R.style.AppTheme_Dark : R.style.AppTheme);
        super.onCreate(state);
        playback = PlaybackController.acquire(this);
        library = new ScoreLibrary(this);
        sortMode = Math.max(0, Math.min(2, UiTheme.preferences(this).getInt("librarySort", 0)));
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(theme.background);
        UiTheme.insets(root, dp(16), false);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(iconButton(R.drawable.ic_arrow_back, "返回", this::finish), new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView title = text("音譜庫", 23, theme.text); title.setTypeface(null, Typeface.BOLD); title.setPadding(dp(8), 0, 0, 0);
        header.addView(title);
        View spacer = new View(this); header.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
        header.addView(iconButton(R.drawable.ic_add, "匯入音譜", this::pickImport), new LinearLayout.LayoutParams(dp(44), dp(44)));
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(56)));
        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("搜尋樂曲名稱");
        search.setTextSize(15);
        search.setTextColor(neutralText());
        search.setHintTextColor(neutralMuted());
        search.setBackground(UiTheme.rounded(neutralField(), dp(12)));
        search.setPadding(dp(12), 0, dp(12), 0);
        search.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0);
        search.setCompoundDrawablePadding(dp(8));
        search.setCompoundDrawableTintList(ColorStateList.valueOf(neutralMuted()));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { query = s.toString(); render(); }
            public void afterTextChanged(Editable s) {}
        });
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, dp(48));
        searchParams.setMargins(0, dp(6), 0, dp(4));
        root.addView(search, searchParams);
        LinearLayout sortRow = new LinearLayout(this); sortRow.setGravity(Gravity.CENTER_VERTICAL);
        count = text("", 12, theme.muted); sortRow.addView(count, new LinearLayout.LayoutParams(0, -1, 1));
        sortButton = new Button(this);
        sortButton.setAllCaps(false);
        sortButton.setTextSize(12);
        sortButton.setTextColor(neutralText());
        sortButton.setMinWidth(0); sortButton.setMinimumWidth(0); sortButton.setMinHeight(0); sortButton.setMinimumHeight(0);
        sortButton.setPadding(dp(10), 0, dp(10), 0);
        rippleBackground(sortButton);
        sortButton.setOnClickListener(v -> sortMenu());
        sortRow.addView(sortButton, new LinearLayout.LayoutParams(-2, dp(36)));
        root.addView(sortRow, new LinearLayout.LayoutParams(-1, dp(40)));
        ScrollView scroll = new ScrollView(this); body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root); theme.window(this, false); updateSortLabel(); refresh();
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL); return view;
    }
    private int neutralText() { return theme.dark ? 0xFFE8E8E8 : 0xFF202020; }
    private int neutralMuted() { return theme.dark ? 0xFFA8A8A8 : 0xFF666666; }
    private int neutralField() { return theme.dark ? 0xFF2A2A2A : 0xFFF1F1F1; }
    private void rippleBackground(View view) {
        android.util.TypedValue value = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, value, true);
        view.setBackgroundResource(value.resourceId);
    }
    private ImageButton iconButton(int icon, String description, Runnable action) {
        ImageButton button = new ImageButton(this); button.setImageResource(icon); button.setContentDescription(description);
        button.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        button.setImageTintList(ColorStateList.valueOf(neutralText()));
        rippleBackground(button);
        button.setOnClickListener(v -> { if (!busy) action.run(); });
        return button;
    }
    private Button cardButton(String value, int color, Runnable action) {
        Button button = new Button(this); button.setText(value); button.setAllCaps(false); button.setTextSize(13);
        button.setTextColor(color); button.setMinWidth(0); button.setMinimumWidth(0); button.setMinHeight(0); button.setMinimumHeight(0);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setBackground(UiTheme.rounded(UiTheme.blend(theme.surface, theme.primary, .14f), dp(10)));
        button.setOnClickListener(v -> action.run()); return button;
    }
    private void pickImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
            "application/vnd.recordare.musicxml+xml", "application/vnd.recordare.musicxml", "application/xml", "text/xml",
            "application/zip", "application/json", "text/plain", "application/octet-stream"
        });
        startActivityForResult(intent, IMPORT);
    }
    private void importScore(Uri uri) {
        final Score[] imported = new Score[1];
        perform(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new java.io.IOException("無法開啟檔案");
                imported[0] = ScoreImport.read(input);
                if (imported[0].notes.isEmpty()) throw new java.io.IOException("此樂譜沒有可播放的音符");
                String previous = library.selected();
                library.add(imported[0], "匯入");
                if (previous != null) library.select(previous);
            }
        }, () -> {
            refresh();
            if (imported[0] != null) dialog().setTitle("匯入摘要").setMessage(imported[0].importSummary()).setPositiveButton("知道了", null).show();
        });
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == IMPORT && result == RESULT_OK && data != null && data.getData() != null) importScore(data.getData());
    }
    private AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(new ContextThemeWrapper(this, theme.dark ? R.style.AppTheme_Dark : R.style.AppTheme));
    }
    private void updateSortLabel() { sortButton.setText("排序：" + SORT_NAMES[sortMode]); }
    private void sortMenu() {
        dialog().setTitle("排序方式").setSingleChoiceItems(SORT_NAMES, sortMode, (d, which) -> {
            sortMode = which; UiTheme.preferences(this).edit().putInt("librarySort", which).apply();
            updateSortLabel(); d.dismiss(); render();
        }).setNegativeButton("取消", null).show();
    }
    private interface Work { void run() throws Exception; }
    private void perform(Work work, Runnable success) {
        if (busy) return; busy = true;
        files.execute(() -> {
            try { work.run(); runOnUiThread(() -> { if (isDestroyed()) return; busy = false; success.run(); }); }
            catch (Exception e) { runOnUiThread(() -> { if (isDestroyed()) return; busy = false; Toast.makeText(this, "操作失敗：" + e.getMessage(), Toast.LENGTH_LONG).show(); }); }
        });
    }
    private void refresh() {
        busy = true;
        files.execute(() -> {
            try {
                List<ScoreLibrary.Entry> loaded = library.list();
                runOnUiThread(() -> { if (isDestroyed()) return; busy = false; entries = loaded; render(); });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return; busy = false; body.removeAllViews();
                    body.addView(text("無法讀取音譜庫：" + e.getMessage(), 15, theme.muted));
                });
            }
        });
    }
    private void render() {
        if (body == null) return;
        body.removeAllViews();
        playButtons.clear();
        playStates.clear();
        List<ScoreLibrary.Entry> shown = filterAndSort(entries, query, sortMode);
        count.setText(shown.size() + " 首樂曲");
        if (shown.isEmpty()) {
            TextView empty = text(entries.isEmpty() ? "尚無樂譜。按右上角 + 匯入 MusicXML。" : "找不到符合的樂曲", 15, theme.muted);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(28), 0, dp(28)); body.addView(empty);
            return;
        }
        for (ScoreLibrary.Entry entry : shown) {
            LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(12), dp(14), dp(10));
            card.setBackground(UiTheme.rounded(theme.surface, dp(16)));
            TextView title = text(entry.title, 17, theme.text); title.setTypeface(null, Typeface.BOLD);
            title.setSingleLine(true); title.setEllipsize(android.text.TextUtils.TruncateAt.END); card.addView(title);
            LinearLayout summaryRow = new LinearLayout(this); summaryRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView composer = text(entry.composer == null ? "" : entry.composer.trim(), 12, theme.muted);
            composer.setSingleLine(true); composer.setEllipsize(android.text.TextUtils.TruncateAt.END);
            summaryRow.addView(composer, new LinearLayout.LayoutParams(0, -1, 1));
            TextView duration = text(formatDuration(entry.seconds), 12, theme.muted);
            summaryRow.addView(duration);
            summaryRow.setPadding(0, dp(3), 0, 0);
            card.addView(summaryRow);
            String instruments = entry.instruments == null ? "" : entry.instruments.trim();
            if (!instruments.isEmpty()) {
                TextView instrumentView = text(instruments, 12, theme.muted);
                instrumentView.setSingleLine(true); instrumentView.setEllipsize(android.text.TextUtils.TruncateAt.END);
                instrumentView.setPadding(0, dp(2), 0, dp(8));
                card.addView(instrumentView);
            } else {
                summaryRow.setPadding(0, dp(3), 0, dp(8));
            }
            LinearLayout actions = new LinearLayout(this); actions.setGravity(Gravity.CENTER_VERTICAL);
            Button play = cardButton("▶ 播放", theme.text, () -> play(entry));
            play.setTag("play_" + entry.id);
            playButtons.put(entry.id, play);
            actions.addView(play, new LinearLayout.LayoutParams(0, dp(40), 1));
            Button delete = cardButton("刪除", theme.dark ? 0xFFFF969F : 0xFFB03F48, () -> delete(entry));
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(0, dp(40), 1);
            deleteParams.setMargins(dp(8), 0, 0, 0);
            actions.addView(delete, deleteParams);
            card.addView(actions);
            card.setOnClickListener(v -> play(entry));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
            cardParams.setMargins(0, 0, 0, dp(8));
            body.addView(card, cardParams);
        }
        updatePlayButtons();
    }
    private void updatePlayButtons() {
        if (playback == null) return;
        String activeId = playback.id;
        boolean playing = playback.audio.playing;
        for (Map.Entry<String, Button> item : playButtons.entrySet()) {
            boolean active = item.getKey().equals(activeId);
            boolean pause = active && playing;
            String state = pause ? "pause" : active ? "resume" : "play";
            if (state.equals(playStates.get(item.getKey()))) continue;
            playStates.put(item.getKey(), state);
            item.getValue().setText(pause ? "❚❚ 暫停" : "▶ 播放");
            item.getValue().setContentDescription(pause ? "暫停播放" : active ? "繼續播放" : "播放");
        }
    }
    static List<ScoreLibrary.Entry> filterAndSort(List<ScoreLibrary.Entry> source, String query, int sortMode) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<ScoreLibrary.Entry> result = new ArrayList<>();
        for (ScoreLibrary.Entry entry : source) {
            if (needle.isEmpty() || entry.title.toLowerCase(Locale.ROOT).contains(needle)) result.add(entry);
        }
        if (sortMode == 1) result.sort((a, b) -> a.title.compareToIgnoreCase(b.title));
        else if (sortMode == 2) result.sort(Comparator.comparingDouble(entry -> entry.seconds));
        else result.sort((a, b) -> Long.compare(b.created, a.created));
        return result;
    }
    private void play(ScoreLibrary.Entry entry) {
        if (playback == null || busy) return;
        if (entry.id.equals(playback.id)) {
            if (playback.audio.playing) playback.pause(); else playback.play();
            updatePlayButtons();
            return;
        }
        final Score[] loaded = new Score[1]; final PlaybackProgram[] compiled = new PlaybackProgram[1];
        perform(() -> { loaded[0] = library.load(entry.id); compiled[0] = new PlaybackProgram(loaded[0]); }, () -> {
            if (playback == null) return;
            library.select(entry.id);
            playback.pause();
            busy = true;
            int selected = PianoSound.selected(this);
            if (selected == PianoSound.AUTO) SampleBankStore.start(this, compiled[0].requiredSounds);
            else if (selected != PianoSound.SYNTHETIC) SampleBankStore.start(this, selected);
            if (!SampleBankStore.ready(this, compiled[0].requiredSounds)) Toast.makeText(this, "音色載入中…", Toast.LENGTH_SHORT).show();
            handler.postDelayed(() -> {
                if (isDestroyed() || playback == null) { busy = false; return; }
                playback.load(entry.id, loaded[0], compiled[0]);
                waitForBanks(compiled[0].requiredSounds, 0, () -> {
                    if (isDestroyed() || playback == null) { busy = false; return; }
                    playback.play();
                    busy = false;
                    updatePlayButtons();
                });
            }, 80);
        });
    }
    private void waitForBanks(int[] requiredSounds, int attempts, Runnable action) {
        if (isDestroyed() || playback == null) return;
        if (SampleBankStore.ready(this, requiredSounds) || attempts >= 60) { action.run(); return; }
        handler.postDelayed(() -> waitForBanks(requiredSounds, attempts + 1, action), 50);
    }
    private void delete(ScoreLibrary.Entry entry) {
        dialog().setTitle("刪除樂譜？").setMessage("要刪除「" + entry.title + "」嗎？刪除後無法復原。")
            .setNegativeButton("取消", null).setPositiveButton("刪除", (d, which) -> perform(() -> library.delete(entry.id), () -> {
                if (playback != null) playback.deleted(entry.id);
                refresh();
            })).show();
    }
    private static String formatDuration(double seconds) {
        int value = (int) Math.max(0, seconds);
        return String.format(Locale.ROOT, "%d:%02d", value / 60, value % 60);
    }
    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(playbackRefresh);
        handler.post(playbackRefresh);
    }
    @Override protected void onPause() {
        handler.removeCallbacks(playbackRefresh);
        super.onPause();
    }
    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        files.shutdown();
        if (playback != null) playback.release();
        super.onDestroy();
    }
}
