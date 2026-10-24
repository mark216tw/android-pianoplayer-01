# 建置與測試

## 1. 環境

| 項目 | 需求 |
|---|---|
| JDK | 17 或相容 JDK 21 |
| Android SDK | Platform 36 |
| Gradle | 由 Wrapper 提供 |
| 系統 | Windows／macOS／Linux |

設定 `ANDROID_HOME`，或建立 `local.properties`：

```properties
sdk.dir=C:/path/to/Android/Sdk
```

## 2. 建置

```powershell
.\gradlew.bat assembleDebug
```

產物：`app/build/outputs/apk/debug/app-debug.apk`。

驗證四個音色庫、雜湊與授權：

```powershell
python tools/verify_piano_apk.py app/build/outputs/apk/debug/app-debug.apk
```

安裝：

```powershell
adb install -r "app/build/outputs/apk/debug/app-debug.apk"
```

### 測試發行版

```powershell
.\gradlew.bat assemblePrerelease
```

產物：`app/build/outputs/apk/prerelease/app-prerelease.apk`。

`prerelease` 使用 `release` 基底並額外設定：

- 版本名稱：`1.0.0-prerelease`
- R8 程式碼壓縮與資源縮減
- `debuggable false`
- 使用 Debug 金鑰簽署

此版本供測試與 GitHub Release 使用，不適合直接上架商店。

## 3. 測試

```powershell
.\gradlew.bat testDebugUnitTest
```

測試使用 API 26／29／35 模擬環境，可直接在 JDK 17 執行：

```powershell
.\gradlew.bat testDebugUnitTest
```

## 4. 測試涵蓋

| 測試 | 重點 |
|---|---|
| MusicXmlTest | XML、MXL、和弦、延音線、拍號、實體安全 |
| AdvancedMusicXmlTest | 力度、速度、反覆、跳轉、踏板、聲部 |
| WrappedMusicXmlTest | JSON 包裝、MXL 附件 |
| MusicXmlStorageUiTest | 儲存、作曲者、靜音聲部、舊格式、音色分配、低音弦樂遷移、聲部視窗高度 |
| ScoreLibraryTest | 音譜庫新增／更新／刪除、meta 作曲者／樂器與舊項目補寫 |
| PianoViewTest | 完整音域、窄音域、黑鍵邊界、預設範圍 |
| ImportPolicyTest／ScoreLibraryImportTest | 音譜庫檔案選擇器 MIME、MIDI 拒絕、匯入後留在音譜庫與維持目前曲目 |
| StartupTest | Activity 啟動、全螢幕、繪製 |
| BackgroundPlaybackTest | 服務控制、Activity 重建、UI 隱藏 |
| SampleBank*／PianoSampleTest | 取樣載入、力度層、音域、release／pedal 設定 |
| ViolinBankTest | 長弓 30 個取樣、兩力度層、88 鍵覆蓋、雜湊與合併授權 |
| SpicBankTest／PianoLowBankTest | 跳弓 21–96、低音鋼琴 21–54、局部 coverage 與檔案雜湊 |
| ViolinRoutingTest | 低音鋼琴、短音跳弓、高音跳弓與弦樂低音移調路由 |
| InstrumentDetectorTest／AutoInstrumentTest | MusicXML 樂器判定、中提琴／大提琴、每聲部音色與所需音色庫 |
| SampleBankLoudnessTest | 逐鍵與力度層響度、accent 跨層音量、gain 上限 |
| ReverbTest／LimiterTest／DamperTest | 音訊處理 |
| ThemeSettingsTest | 顯示模式、主題色、Hue、音色、小提琴音量與弦樂低音設定 |

## 5. 音色響度校正

需要 Python 3.12 以上與 `tools/requirements-audio.txt` 的套件：

```powershell
python -m pip install -r tools/requirements-audio.txt
python tools/measure_piano_loudness.py --check
python tools/rebalance_piano_gains.py --report docs/audio/bank-calibration.json
python tools/prepare_violin.py --cache <cache>
```

- `measure_piano_loudness.py --check` 量測力度 40／90／110 的起音與持續響度差。
- `rebalance_piano_gains.py` 只更新 `instrument.json` 的 `gain`，不改 WAV，SHA-256 維持不變。
- 目前 `--check` 的起音差約為 1.35／3.39／2.17 dB；完整報告在 `docs/audio/bank-calibration.json`。
- `prepare_violin.py` 從固定 VSCO 2 CE revision 下載長弓與跳弓取樣，重建 `assets/violin/`、`assets/violin-spic/` 與 `assets/piano-low/`，並輸出兩份小提琴校正報告。
- 修改 `prepare_piano.py`、`prepare_violin.py` 或音色資產後，應重新執行並更新 `docs/audio/README.md` 與報告。

## 6. 真機驗收清單

- [ ] 可與舊版 mini 鋼琴並存（不同套件）
- [ ] 匯入 `.musicxml`、`.xml`、`.mxl`、JSON 包裝
- [ ] 音譜庫匯入後停留、只加入清單、可再按 ▶ 播放
- [ ] 音譜庫每首顯示作曲者與聲部樂器，且沒有欄位標題
- [ ] 舊音譜開啟後會補上作曲者與樂器資料
- [ ] 非 MusicXML 顯示友善錯誤
- [ ] 卷軸由右往左移動，與鍵盤亮鍵同步
- [ ] 完整音域樂曲（A0–C8）所有音符都出現
- [ ] 拖曳進度列可跳轉
- [ ] 播放／暫停／停止／速度正常
- [ ] 按播放後卷軸與鍵盤立即開始，不再延遲約 1 秒
- [ ] 音譜庫切換鋼琴／小提琴時，開頭不再有低頻「咚」
- [ ] 背景播放、通知、鎖屏控制正常
- [ ] 聲部視窗高度足夠且可捲動
- [ ] 聲部靜音即時生效並保存
- [ ] 小提琴與大鋼琴音量平衡；小提琴音量滑桿可調整並保存
- [ ] 小提琴音色可載入長弓／跳弓／低音鋼琴，A0–C8 都有聲音，note-off 不明顯殘響
- [ ] 自動模式可正確分配鋼琴／小提琴；中提琴／大提琴使用弦樂，低音可切換移調小提琴／鋼琴
- [ ] 韋瓦第《春》1:30–1:53 的快速短音不再有低頻鋼琴打滾，高音短音音色一致
- [ ] 切換自動／鋼琴／小提琴／合成音色不 OOM、不爆音
- [ ] 高音 C6–C8 聽感已柔化
- [ ] 深／淺色主題與 88 鍵可讀性
- [ ] 無錄音、手動彈奏、匯出按鈕

## 7. 效能觀察

卷軸每次只繪製可視時間窗內的音符；若大型樂曲在低階裝置掉帧，可先降低卷軸更新率或隱藏小節線。
