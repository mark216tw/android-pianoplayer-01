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
| `composer` | string | 作曲者（舊項目會自動補寫） |
| `instruments` | string | 聲部樂器名稱，去重後以「、」串接 |
| `created` | long | 建立時間（毫秒） |
| `updated` | long | 更新時間（毫秒） |
| `seconds` | double | 樂曲長度（秒） |

### `<id>.json`

| 欄位 | 型別 | 說明 |
|---|---|---|
| `title` | string | 曲名 |
| `composer` | string | 作曲者（MusicXML `creator type="composer"`） |
| `bpm` | int | 起始 BPM |
| `beats` / `beatType` | int | 起始拍號 |
| `schemaVersion` | int | 目前為 5 |
| `endTick` | long | 演奏結尾 tick |
| `originalMeasureCount` | int | 原譜小節數 |
| `originalXml` | string | 原始 MusicXML（若有） |
| `notes` | array | `[pitch,start,duration,velocity,part,voice,staff,staffMask,articulation]`；articulation 0 一般、1 斷奏、2 持音；舊檔缺少時視為 0 |
| `pedals` | array | `[tick,down,part]` |
| `tempos` | array | `[tick,bpm]` |
| `measures` | array | `[tick,duration,number,visit]` |
| `partNames` | object | part ID → 樂器名稱 |
| `partSounds` | object | part ID → 音色（0 鋼琴、3 小提琴） |
| `partInstruments` | object | part ID → 偵測到的樂器標籤 |
| `lowStringParts` | array | 中提琴／大提琴／低音提琴 part ID；低音可使用移調小提琴 |
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
| `pianoSound` | int | 4 | 4 自動、0 Salamander、3 小提琴、2 合成；舊值 1 仍視為 Salamander |
| `pianoVolume` | int | 100 | 保留欄位；目前未使用 |
| `violinVolume` | int | 75 | 小提琴／跳弓音量 50–100% |
| `stringLowViolin` | boolean | true | 中提琴／大提琴低音使用移調小提琴；關閉時以鋼琴補位 |
| `pianoReverb` | boolean | false | 空間殘響 |
| `pianoReverbMix` | int | 18 | 殘響量 0–60 |

音譜庫使用另一個 `SharedPreferences`：`scoreLibrary`，內含 `selected`（上次選取 ID）與 `migrated`。

## 3. 音色資產

`app/src/main/assets/piano/`：

- `instrument.json`：取樣對照、力度層、音域、逐鍵 `gain` 與 SHA-256；`gainCalibration` 記錄目前使用的 K 加權起音／持續響度校正參數。
- `note_XXX_soft/medium/loud.wav`：三力度層 WAV；變更增益時不需重建 WAV，SHA-256 維持不變。
- `ATTRIBUTION.txt`、`LICENSE.txt`：Salamander 授權。
- 校正報告：`docs/audio/bank-calibration.json`；重算指令為 `python tools/rebalance_piano_gains.py`。

`app/src/main/assets/violin/`：

- `instrument.json`：VSCO 2 CE Solo Violin Arco Vib；`releaseSeconds` 為 0.05、`sustainPedal` 為 `false`、`outputGain` 為 1.0，`naturalLowMidi`／`naturalHighMidi` 為 55／96，`coverageLowMidi`／`coverageHighMidi` 為 21／108。高音衰減欄位為 `toneStartMidi` 84、`toneEndMidi` 108、`toneStartHz` 16000、`toneEndHz` 5500。
- `note_XXX_soft/loud.wav`：p／f 兩力度層 WAV，30 個根音取樣，全部 44.1 kHz。
- `ATTRIBUTION.txt`、`LICENSE.txt`：VSCO 2 CE 的 CC0 來源、Salamander 低音鋼琴來源，以及合併的 CC0／CC BY 3.0 全文。

`app/src/main/assets/violin-spic/`：

- `instrument.json`：Spiccato 取樣對照；`releaseSeconds` 為 0.03、`sustainPedal` 為 `false`、`outputGain` 為 1.0，涵蓋 MIDI 21–96；`naturalLowMidi`／`naturalHighMidi` 為 55／84，超出範圍以移調播放。高音衰減欄位為 `toneStartMidi` 84、`toneEndMidi` 96、`toneStartHz` 16000、`toneEndHz` 8000。
- `note_XXX_soft/loud.wav`：v1／v2 兩力度層 WAV，20 個根音取樣。

`app/src/main/assets/piano-low/`：

- `instrument.json`：從鋼琴 manifest 篩選 MIDI 21–54，涵蓋 21–54、3 個力度層；樣本檔名指向 `assets/piano/`。
- `ATTRIBUTION.txt`、`LICENSE.txt`：沿用 Salamander 的 CC BY 3.0 授權。

- 重建指令：`python tools/prepare_violin.py --cache <directory>`；校正報告：`docs/audio/violin-calibration.json`、`docs/audio/violin-spic-calibration.json`。

## 4. 匯入格式

- MusicXML XML：`<score-partwise>`
- MXL：ZIP，需有 `META-INF/container.xml` 指向主 XML。
- JSON 包裝：`{"musicxml":"<score-partwise>..."}`

單檔上限 8 MB。
