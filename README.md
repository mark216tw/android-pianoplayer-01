# 鋼琴曲播放 · Android

一款離線的 MusicXML 鋼琴樂曲播放器。匯入樂譜後，畫面會以「橫向鋼琴卷軸＋完整音域鍵盤」同步呈現演奏：音符由右往左捲動，鋼琴鍵即時亮起。APP 不提供錄音、手動彈奏或樂譜匯出。

![版本](https://img.shields.io/badge/版本-1.0.0-167C70)
![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84)
![專案授權](https://img.shields.io/badge/程式碼授權-MIT-blue)

## 專案資訊

| 項目 | 內容 |
|---|---|
| APP 名稱 | 鋼琴曲播放 |
| 套件名稱 | `com.minipianoplayer.app` |
| 版本 | `1.0.0`，版本代碼 `1` |
| 最低系統 | Android 8.0，API 26 |
| 編譯／目標 SDK | API 36，Android 16 |
| 開發技術 | Java 17、原生 Android View、AudioTrack |
| 起源 | 以 mini 鋼琴專案為基礎改寫 |

## 主要功能

- **MusicXML 匯入**：支援 `.musicxml`、`.xml`、`.mxl` 與 JSON 包裝格式；內容必須是可解析的 `score-partwise` MusicXML，非樂譜檔會顯示錯誤訊息。
- **完整音域鍵盤**：自動依樂曲最低音與最高音顯示完整範圍，最多可容納 88 鍵（A0–C8、52 個白鍵），不裁切任何音符。鍵寬較窄時自動只標示 C 音。
- **橫向鋼琴卷軸**：時間軸水平、音高軸垂直，音符由右往左捲動；固定播放游標、小節線與小節編號、左右手分色、目前發聲音符發光。
- **點擊跳轉**：點擊或拖曳卷軸可直接跳到該時間點。
- **播放控制**：播放／暫停、停止、進度跳轉、0.5–1.5 倍速、目前 BPM 與原譜小節位置。
- **鋼琴音色**：Salamander 大鋼琴三力度層採樣、逐鍵 K 加權響度校正、等功率力度層交叉淡化、合成鋼琴、獨立鋼琴音量與空間殘響。
- **聲部控制**：可勾選要播放的聲部，設定會保存。
- **音譜庫**：匯入後自動加入，可播放、改名、刪除，並記住上次選取的樂曲。
- **背景播放**：切換頁面、Home 或鎖屏時持續播放；提供前景服務、播放通知、鎖屏媒體控制與音訊焦點處理。
- **外觀**：全螢幕橫向、系統／淺色／深色模式、六款主題色與自訂 Hue。
- **離線使用**：無需帳號、網路或麥克風權限；樂譜保存在 APP 私有空間。

## 近期更新

- 重新校準 Salamander 逐鍵與力度層響度，修正高音起音偏大與 accent 跨力度層時的約 7dB 音量跳變。
- APP 圖示移除上方裝飾，背景改為冷色系漸層。
- 校正方法與離線重現方式見 [音色響度平衡](docs/audio/README.md)。

## 開始使用

1. 建置並安裝 APK，開啟「鋼琴曲播放」。
2. 按「↥ 匯入」選擇 MusicXML 樂譜。
3. 匯入成功後會顯示摘要，按 ▶ 開始播放。
4. 卷軸會由右往左移動，鋼琴鍵同步亮起；可點擊卷軸跳轉。
5. 按「音譜庫」管理已匯入的樂曲。
6. 按右上角齒輪調整顯示模式與主題色；按「⋮」調整音色、聲部或查看匯入摘要。

`examples/` 內含可直接匯入的範例樂譜，包括 `turkish-march-k331-piano.musicxml`。

## 快速建置

需要 JDK 17 或相容的 JDK 21、Android SDK Platform 36。設定 `ANDROID_HOME`，或建立不納入 Git 的 `local.properties`：

```properties
sdk.dir=C:/path/to/Android/Sdk
```

Windows PowerShell：

```powershell
.\gradlew.bat assembleDebug
adb install -r "app/build/outputs/apk/debug/app-debug.apk"
```

macOS／Linux：

```sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

單元測試使用 API 26／29／35 模擬環境：

```powershell
.\gradlew.bat testDebugUnitTest
```

## 文件索引

| 文件 | 用途 |
|---|---|
| [專案說明](docs/PROJECT_OVERVIEW.md) | 目標、功能範圍與限制 |
| [使用指南](docs/USER_GUIDE.md) | 匯入、播放、卷軸、設定與音譜庫 |
| [系統架構](docs/ARCHITECTURE.md) | 模組、執行緒與資料流 |
| [技術參考](docs/TECHNICAL_REFERENCE.md) | 版本、音訊、卷軸繪製與主題 |
| [資料格式](docs/DATA_FORMATS.md) | 曲目 JSON、偏好設定與音色對照表 |
| [MusicXML 支援](docs/MUSICXML_SUPPORT.md) | 記號支援、解讀規則與限制 |
| [建置與測試](docs/BUILD_AND_TEST.md) | 環境、指令、測試與真機驗收 |
| [疑難排解](docs/TROUBLESHOOTING.md) | 音訊、匯入、資料與 Android 問題 |
| [音色響度平衡](docs/audio/README.md) | 逐鍵與力度層校正、量測工具與報告 |
| [版本紀錄](CHANGELOG.md) | 版本功能與修正 |
| [第三方聲明](THIRD_PARTY_NOTICES.md) | 音色、Gradle Wrapper 與相依套件來源 |

## 驗證與限制

- 已通過 MusicXML 解析、反覆／跳轉、速度、聲部、儲存、主題、採樣、響度校正、卷軸音域與背景播放的框架測試。
- 背景播放、鎖屏控制、不同裝置的喇叭／耳機聽感與 88 鍵在窄螢幕上的可讀性仍需真機驗收。
- 目前只播放 MusicXML；不支援 MIDI、音訊檔或線上樂譜。
- 不含錄音、手動彈奏、節拍器、樂譜匯出或演奏資料量化。

## 授權

本專案自行開發的程式碼、原創文件與原創圖示採用 **MIT License**：

- [MIT 授權原文](LICENSE)
- [MIT 繁體中文譯文](LICENSE.zh-TW.md)

Salamander 採樣為 **CC BY 3.0**；Gradle Wrapper 為 **Apache-2.0**。完整範圍及檔案連結請見 [第三方聲明](THIRD_PARTY_NOTICES.md)。
