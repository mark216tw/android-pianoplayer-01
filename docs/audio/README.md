# 音色響度平衡

Salamander 銀行在 `tools/prepare_piano.py` 重建時，會對每個 root／力度層量測 BS.1770 K 加權的前 50ms 起音能量（70%）與 250ms 持續能量（30%），將三個力度層校正到同一參考響度（以 medium 層中位數為基準），寫入 `instrument.json` 的 `gain`。播放時只套用力度曲線 `(v/127)^0.85` 與該校正值，因此 accent 跨力度層時不會再額外產生約 7dB 的音量跳變。相鄰力度層在邊界前後 6 個 MIDI 力度單位以等功率交叉淡化。

合成音色沿用 `TimbreBalance` 的五個音高錨點（MIDI 24、48、72、96、108）。合成曲線以 Salamander 為基準，離線比較 A0–C8、固定力度 90、前 0.5 秒持續音與短旋律／和弦段落；使用 BS.1770 的 48 kHz K-weighting 係數量測相對數位響度，不是手機 SPL。

`tools/measure_piano_loudness.py` 會套用 `instrument.json` 的實際 gain 與 velocity→layer 選擇，輸出參考報告與合成曲線。`--check` 可檢查取樣音色在力度 40／90／110 的起音與持續響度差異。重建音色後應重新產生報告。舊的 `before.json` 對應 32 kHz 雙層版本，僅供歷史比較；目前取樣校正結果在 `bank-calibration.json`，合成曲線報告在 `calibrated.json`。

目前 `--check` 的起音差約為力度 40／90／110：**1.35／3.39／2.17 dB**；持續音差約 6.3–7.1 dB，主要來自高音自然衰減較快。逐鍵校正報告顯示各力度層的起音差已降到約 1.1–2.0 dB，三層響度中位數對齊。

重現（需 numpy、scipy、soundfile）：

```powershell
python tools/measure_piano_loudness.py --check
python tools/measure_piano_loudness.py --report docs/audio/before.json
python tools/measure_piano_loudness.py --calibrate --report docs/audio/calibrated.json
python tools/rebalance_piano_gains.py --report docs/audio/bank-calibration.json
```

## 小提琴音色

小提琴銀行由 `tools/prepare_violin.py` 重建，來源固定為 `sgossner/VSCO-2-CE@440300901dfe9275fd84e0b7763af1f8443ae62e`。分成三個部分：

- **Arco Vib**：p／f 兩層、15 個根音（G3–C7），44.1 kHz，`releaseSeconds = 0.05`。soft 層會偵測慢速膨脹的起音並裁掉前段，讓伴奏音更快出現。
- **Spiccato**：v1／v2 兩層、10 個根音（G3–C6），涵蓋 MIDI 21–96（低音／高音以移調），44.1 kHz，`releaseSeconds = 0.03`。播放時音長小於 `0.35` 秒會自動使用跳弓。
- **低音鋼琴**：小提琴聲部的 MIDI 21–54 直接引用既有 `assets/piano/` 的 Salamander 取樣，保留鋼琴 damper 與延音踏板行為；中提琴／大提琴低音預設改用移調小提琴，可在音色設定切換。
- **高音柔化**：Arco manifest 設定 `toneStartMidi = 84`、`toneEndMidi = 108`、`toneStartHz = 16000`、`toneEndHz = 5500`；Spic 設定 `toneStartMidi = 84`、`toneEndMidi = 96`、`toneStartHz = 16000`、`toneEndHz = 8000`；播放時套用隨音高增加的二級低通。

每個取樣先以整體 RMS 正規化，再套用 BS.1770 K 加權起音／持續響度校正。Arco 與 Spic 的 loud 層會再對齊到同一參考響度，避免切換奏法時音量跳動；峰值上限為 0.9。小提琴與跳弓 manifest 的 `outputGain` 為 1.0；播放時另套用「小提琴音量」偏好（預設 75%），讓小提琴整體聽感更接近 Salamander 大鋼琴。

音域路由：小提琴聲部的 MIDI 21–54 使用低音鋼琴；21–96 且音長小於 0.35 秒使用 Spic；其餘使用 Arco。97–108 不使用跳弓，並由 Arco 高音低通柔化。中提琴／大提琴／低音提琴預設允許低音使用移調小提琴，短音同樣使用 Spic。自動模式會依 MusicXML 聲部決定使用鋼琴、小提琴或兩者；混合樂譜會跳過低音鋼琴庫，直接以完整鋼琴音色庫補位。校正報告在 `docs/audio/violin-calibration.json` 與 `docs/audio/violin-spic-calibration.json`。

```powershell
python tools/prepare_violin.py --cache <cache>
python tools/measure_piano_loudness.py --check
python tools/rebalance_piano_gains.py --report docs/audio/bank-calibration.json
```

空間殘響量滑桿作用於混音後的鋼琴與小提琴。音色未完成載入而暫用合成時，使用合成音色曲線。仍須在手機喇叭、耳機與藍牙裝置比較實際聽感。
