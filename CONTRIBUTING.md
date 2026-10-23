# 貢獻指南

感謝你協助改進「鋼琴曲播放」。開始前請閱讀 [專案說明](docs/PROJECT_OVERVIEW.md)、[系統架構](docs/ARCHITECTURE.md) 與 [建置與測試](docs/BUILD_AND_TEST.md)。

## 回報問題

請提供：

1. APP 版本、裝置型號與 Android 版本。
2. 使用步驟、預期結果與實際結果。
3. 音訊輸出方式（喇叭、耳機、藍牙）。
4. 相關 MusicXML 檔或可重現的最小片段。
5. 若為卷軸或鍵盤問題，請附螢幕截圖。

## 開發流程

1. Fork 並建立分支。
2. 維持既有 Java 與原生 View 架構。
3. 音訊與 UI 狀態需遵守既有執行緒邊界。
4. MusicXML 解析變更請補測試；卷軸音域變更請補 `PianoViewTest`。
5. 同步更新 `CHANGELOG.md` 與相關文件。
6. 提交 Pull Request 前執行測試與建置。

## 文件與音色變更

- 根目錄保留專案入口與授權文件：`README.md`、`CHANGELOG.md`、`CONTRIBUTING.md`、`LICENSE`、`THIRD_PARTY_NOTICES.md`。
- 詳細說明放在 `docs/`；音色校正另見 `docs/audio/README.md`。
- 修改採樣或響度時，執行：

```powershell
python tools/measure_piano_loudness.py --check
python tools/rebalance_piano_gains.py --report docs/audio/bank-calibration.json
```

`rebalance_piano_gains.py` 只更新 `instrument.json` 的 `gain`，不會改寫 WAV 或變更其 SHA-256。

## 提交前檢查

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

請勿提交 `build/`、`.gradle/`、`local.properties`、簽章檔或任何密鑰。
