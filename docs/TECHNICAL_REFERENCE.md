# 技術參考

## 1. 版本與環境

| 項目 | 值 |
|---|---|
| 套件 | `com.minipianoplayer.app` |
| 版本 | `1.0.0` / `versionCode 1` |
| minSdk / targetSdk | 26 / 36 |
| Java | 17 |
| 音訊取樣率 | 48 kHz，立體聲 PCM float |
| 發聲槽 | 72 槽，最多 64 同時發聲 |
| 樂譜時間 | 480 PPQ |

## 2. 鋼琴卷軸繪製

`PianoView` 將畫面分成上下兩區：

- 上方約 68%：卷軸。
- 下方約 32%：鍵盤。

卷軸參數：

- 可視未來時間 `futureSeconds = 7` 秒。
- 可視過去時間 `pastSeconds = 2.5` 秒。
- 播放游標位置：`contentWidth × past / (past + future)`。
- 時間→X：`x = playheadX + (noteSeconds - currentSeconds) × pxPerSecond`。
- 音高→Y：依 `base..topWhite` 線性映射，低音在下、高音在上。
- 音符寬度＝時值；高度依音高 lane 高度調整。
- 只繪製落在可視時間窗內的音符，並裁切到卷軸內容區。

顏色：

- 每個聲部依 `themeHue + channel × 43` 產生色相。
- 左手（staff 2）額外偏移 24°。
- 淺色模式使用較深的音符色（HSV 飽和度 `.80`、明度 `.66`）與不透明填色；深色模式維持主題淡彩與原有透明度。
- 靜音聲部降低透明度；目前發聲音符加白色外框與光暈。

## 3. 完整音域鍵盤

`PianoView.setScore()`：

1. 掃描所有音符，取得最低音與最高音。
2. 最低音往下找最近白鍵作為 `base`，最高音往上找最近白鍵作為 `topWhite`。
3. 建立 `whitePitches[]`，不假設起點一定是 C。
4. 相鄰白鍵相差 2 個半音時插入黑鍵。
5. `pitchCenter[128]` 供卷軸與鍵盤查詢。

範例：

| 樂曲音域 | 顯示 |
|---|---|
| C4–C5 | C4–C5，8 個白鍵 |
| C#4–A#4 | C4–B4，7 個白鍵 |
| A0–C8 | 88 鍵，52 個白鍵 |

音名顯示：

- 鍵寬 ≥ 28 dp：顯示全部白鍵音名。
- 鍵寬 15–28 dp：只顯示 C 音。
- 鍵寬 < 15 dp：不顯示個別音名。

## 4. 音訊

- `PianoAudio` 使用連續 AudioTrack 串流。
- 播放事件由 `PlaybackProgram` 預先編譯，音訊執行緒不安裝 XML 解析器。
- 取樣音色：Salamander 三力度層，逐鍵 K 加權起音／持續響度校正，邊界以等功率交叉淡化。
- 合成音色：正弦查表＋泛音＋衰減包絡。
- 踏板：依 part 記錄 pedal 狀態，放開時釋放未按住音符。
- 靜音：依聲部頻道淡出或恢復。
- 限幅：`Limiter` look-ahead；殘響：`Reverb`。
- `duck()` 供音訊焦點暫時降低音量。

## 5. 播放時鐘

`PlaybackClock` 同時追蹤：

- `sourceSeconds`：樂譜時間（受速度影響）。
- `elapsedSeconds`：實際經過時間。
- `age(id)`：音符實際發聲年齡，用於 seek 後重建取樣位置。

`TempoMap` 提供 tick／秒可逆換算，支援分段速度。

## 6. 主題

`UiTheme`：

- 模式：系統／淺色／深色。
- 色相：0–359，預設六款。
- `primary`、`background`、`surface`、`text`、`muted` 由色相與模式推導。
- 系統列與全螢幕由 `window()` 管理。

## 7. 測試重點

- MusicXML 解析：和弦、backup／forward、延音線、反覆、跳房子、D.C.／D.S.、Coda、力度、速度、踏板。
- 包裝格式：MXL container、JSON wrapper。
- 音域：C4–C5、黑鍵邊界、A0–C8。
- 儲存：ScoreStorage 原子寫入、聲部靜音。
- 背景播放：服務控制、Activity 重建、UI 隱藏。
- 匯入政策：檔案選擇器 MIME 類型。
- 音色響度：`SampleBankLoudnessTest` 檢查逐鍵與力度層響度差；`tools/measure_piano_loudness.py --check` 可重現量測。

## 8. APP 圖示

- Adaptive icon：`app/src/main/res/mipmap-anydpi/ic_launcher.xml`。
- 前景：`drawable/ic_piano_foreground.xml`，只保留鋼琴本體與琴鍵，已移除上方裝飾。
- 單色圖示：`drawable/ic_piano_monochrome.xml`，與前景一致。
- 背景：`drawable/ic_launcher_background.xml`，冷色系斜向漸層 `#B3EAF5 → #5FC7D8 → #1F6F8B`。
- 圖示與原創文件同採 MIT License；Salamander 採樣另依 CC BY 3.0。
