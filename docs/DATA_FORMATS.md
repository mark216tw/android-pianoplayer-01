# 資料格式

## 1. 音譜庫

位置：APP 私有空間 `files/scores/`。

| 檔案 | 內容 |
|---|---|
| `<id>.json` | 樂曲資料 |
| `<id>.meta` | 標題、來源、建立／更新時間、長度 |

`<id>` 為 UUID。`ScoreLibrary` 使用 `AtomicFile` 寫入，避免讀到半份檔案。

### `<id>.meta`

| 欄位 | 型別 | 說明 |
|---|---|---|
| `title` | string | 顯示名稱 |
| `source` | string | 匯入來源，目前為「匯入」 |
| `created` | long | 建立時間（毫秒） |
| `updated` | long | 更新時間（毫秒） |
| `seconds` | double | 樂曲長度（秒） |

### `<id>.json`

| 欄位 | 型別 | 說明 |
|---|---|---|
| `title` | string | 曲名 |
| `bpm` | int | 起始 BPM |
| `beats` / `beatType` | int | 起始拍號 |
| `schemaVersion` | int | 目前為 2 |
| `endTick` | long | 演奏結尾 tick |
| `originalMeasureCount` | int | 原譜小節數 |
| `originalXml` | string | 原始 MusicXML（若有） |
| `notes` | array | `[pitch,start,duration,velocity,part,voice,staff,staffMask]` |
| `pedals` | array | `[tick,down,part]` |
| `tempos` | array | `[tick,bpm]` |
| `measures` | array | `[tick,duration,number,visit]` |
| `partNames` | object | part ID → 樂器名稱 |
| `mutedVoices` | array | 靜音聲部 key |
| `features` | array | 已處理記號 |
| `warnings` | array | 匯入提醒 |

## 2. 偏好設定

`SharedPreferences` 名稱：`MainActivity`。

| Key | 型別 | 預設 | 說明 |
|---|---|---|---|
| `displayMode` | int | 0 | 0 系統、1 淺色、2 深色 |
| `themeHue` | int | 158 | 主題色相 |
| `customTheme` | boolean | false | 是否使用自訂色相 |
| `pianoSound` | int | 0 | 0 Salamander、2 合成 |
| `pianoVolume` | int | 100 | 鋼琴音量 0–100 |
| `pianoReverb` | boolean | false | 空間殘響 |
| `pianoReverbMix` | int | 18 | 殘響量 0–60 |

音譜庫使用另一個 `SharedPreferences`：`scoreLibrary`，內含 `selected`（上次選取 ID）與 `migrated`。

## 3. 音色資產

`app/src/main/assets/piano/`：

- `instrument.json`：取樣對照、力度層、音域、逐鍵 `gain` 與 SHA-256；`gainCalibration` 記錄目前使用的 K 加權起音／持續響度校正參數。
- `note_XXX_soft/medium/loud.wav`：三力度層 WAV；變更增益時不需重建 WAV，SHA-256 維持不變。
- `ATTRIBUTION.txt`、`LICENSE.txt`：Salamander 授權。
- 校正報告：`docs/audio/bank-calibration.json`；重算指令為 `python tools/rebalance_piano_gains.py`。

## 4. 匯入格式

- MusicXML XML：`<score-partwise>`
- MXL：ZIP，需有 `META-INF/container.xml` 指向主 XML。
- JSON 包裝：`{"musicxml":"<score-partwise>..."}`

單檔上限 8 MB。
