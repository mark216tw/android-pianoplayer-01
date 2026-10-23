# 鋼琴音色響度平衡

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

共用音量滑桿作用於補償後的鋼琴與殘響。音色未完成載入而暫用合成時，使用合成音色曲線。仍須在手機喇叭、耳機與藍牙裝置比較實際聽感。
