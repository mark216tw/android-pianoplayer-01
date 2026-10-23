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
| MusicXmlStorageUiTest | 儲存、靜音聲部、舊格式 |
| PianoViewTest | 完整音域、窄音域、黑鍵邊界、預設範圍 |
| ImportPolicyTest | 檔案選擇器 MIME 類型 |
| StartupTest | Activity 啟動、全螢幕、繪製 |
| BackgroundPlaybackTest | 服務控制、Activity 重建、UI 隱藏 |
| SampleBank*／PianoSampleTest | 取樣載入、三力度層、音域 |
| SampleBankLoudnessTest | 逐鍵與力度層響度、accent 跨層音量、gain 上限 |
| ReverbTest／LimiterTest／DamperTest | 音訊處理 |
| ThemeSettingsTest | 顯示模式、主題色、Hue、音色設定 |

## 5. 音色響度校正

需要 Python 3.12 以上與 `tools/requirements-audio.txt` 的套件：

```powershell
python -m pip install -r tools/requirements-audio.txt
python tools/measure_piano_loudness.py --check
python tools/rebalance_piano_gains.py --report docs/audio/bank-calibration.json
```

- `measure_piano_loudness.py --check` 量測力度 40／90／110 的起音與持續響度差。
- `rebalance_piano_gains.py` 只更新 `instrument.json` 的 `gain`，不改 WAV，SHA-256 維持不變。
- 目前 `--check` 的起音差約為 1.35／3.39／2.17 dB；完整報告在 `docs/audio/bank-calibration.json`。
- 修改 `prepare_piano.py` 或音色資產後，應重新執行並更新 `docs/audio/README.md` 與報告。

## 6. 真機驗收清單

- [ ] 可與舊版 mini 鋼琴並存（不同套件）
- [ ] 匯入 `.musicxml`、`.xml`、`.mxl`、JSON 包裝
- [ ] 非 MusicXML 顯示友善錯誤
- [ ] 卷軸由右往左移動，與鍵盤亮鍵同步
- [ ] 完整音域樂曲（A0–C8）所有音符都出現
- [ ] 點擊卷軸可跳轉
- [ ] 播放／暫停／停止／速度正常
- [ ] 背景播放、通知、鎖屏控制正常
- [ ] 聲部靜音即時生效並保存
- [ ] 深／淺色主題與 88 鍵可讀性
- [ ] 無錄音、手動彈奏、匯出按鈕

## 7. 效能觀察

卷軸每次只繪製可視時間窗內的音符；若大型樂曲在低階裝置掉帧，可先降低卷軸更新率或隱藏小節線。
