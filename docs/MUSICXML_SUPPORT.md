# MusicXML 支援

## 1. 支援格式

| 格式 | 支援 |
|---|---|
| MusicXML `.musicxml` | 是 |
| MusicXML `.xml` | 是 |
| 壓縮 MusicXML `.mxl` | 是（需有 container.xml） |
| JSON 包裝 | 是（`musicxml` 欄位） |
| Standard MIDI File `.mid`／`.midi` | 否 |
| 音訊檔 | 否 |

只接受 `score-partwise`。`score-timewise` 會顯示不支援訊息。

## 2. 已處理記號

- 音符、休止符、和弦、附點、tie／tied
- 多聲部、多譜表、backup／forward
- 力度（dynamics、`sound dynamics`、重音）
- 漸強／漸弱
- 速度（metronome、`sound tempo`、分段速度）
- 反覆、跳房子、D.C.／D.S.、Fine、Coda
- 踏板（direction 與 `sound damper-pedal`）
- 移調
- 跨譜表延音線

無固定音高的打擊樂會略過並列入提醒。裝飾音目前略過。

## 3. 解讀規則

- 以 `divisions` 換算為 480 PPQ。
- 反覆與跳轉會展開成有限演奏順序，最多 10,000 小節。
- 延音線合併為單一長音。
- 力度百分比轉為 MIDI velocity，套用於播放音量與卷軸顏色透明度。
- Salamander 三力度層已等響度校正；accent 主要改變音色與約 1dB 音量，不會再額外造成跨層的 7–8dB 跳變。
- 文字 `ritard.`／`accel.` 不自動猜測速度；會列在匯入提醒。
- 變拍號會保留，播放時間軸使用各段實際拍號。

## 4. 鋼琴範圍

- 音符音高限制在 MIDI 21–108（A0–C8）。
- 超出範圍的音符會略過並提醒。
- 鍵盤依樂曲實際最低／最高音顯示完整範圍。

## 5. 多樂器樂譜

自動模式會讀取 `part-name`、`part-abbreviation`、`score-instrument/instrument-name` 與 `midi-instrument/midi-program`，替每個聲部分配鋼琴或小提琴；中提琴、大提琴與低音提琴會使用小提琴音色，低音預設以移調小提琴補位。無法辨識的樂器改用鋼琴並在匯入摘要提醒。混合樂譜會同時載入兩種音色，聲部選單可個別靜音；「弦樂低音使用小提琴（移調）」可切換中提琴／大提琴低音是否改用小提琴。手動選音色時，所有聲部仍使用同一個音色。

## 6. 不支援或簡化

- 裝飾音、滑音、琶音等裝飾效果。
- 歌詞、和弦符號、五線譜排版。
- 音訊檔、MIDI 檔或即時 MIDI 輸入事件。
- 即時網路下載樂譜。
- 樂譜匯出。
