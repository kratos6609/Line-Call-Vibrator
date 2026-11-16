# LineCallVibrator

解決小米手環（Zepp Life）對 LINE 來電「只震動一次」的問題。

## 問題背景
- 小米手環透過 Zepp Life 接收 LINE 來電通知，**預設只震動 1 次**，容易漏接
- Android 系統無原生「持續震動直到接聽/掛斷」設定
- LINE VoIP 來電通知在**接聽/掛斷後不會自動移除**，也無明確狀態變更，導致無法靠通知內容可靠判斷來電結束

## 解決方案（目前為妥協方案）
本 App 使用 `NotificationListenerService` 監聽 LINE 來電通知：
1. 偵測到 LINE 來電（`CATEGORY_CALL` + 關鍵字/Action）
2. 啟動 Foreground Service，每 **2.5 秒發送 1 則高優先級模擬通知**
3. 手環收到通知 → 震動一次 → **持續 6 次（約 15 秒）自動停止**（暫行上限，非理想設計）
4. 來電結束（通知被移除）→ 立即停止震動

> **已知限制**：因 LINE 來電通知接聽/掛斷後不會移除、無明確狀態欄位，**目前無法精準偵測「接聽/掛斷」以即時停止震動**。6 次上限是為了避免震動無限持續的妥協作法。

## 後續改進目標（Roadmap）
- [ ] **精準偵測接聽/掛斷**：研究 LINE 通知 `extras`、RemoteInput、Person、CallScreeningService 等方向
- [ ] **手環顯示來電人名**：模擬通知加入 `setPerson()` / `Notification.MessagingStyle` / `EXTRA_TITLE` 含聯絡人姓名
- [ ] 移除固定 6 次上限，改為「來電持續 → 接聽/掛斷即時停止」

## 功能特色
- ✅ 來電持續震動 6 次（可調整 `MAX_VIBRATION_COUNT`，暫行上限）
- ✅ 自動排除主動撥出（關鍵字：撥打中/outgoing/dialing）
- ✅ 自動排除未接/掛斷/語音訊息/通話記錄
- ✅ 前台服務常駐，Android 14+ 相容
- ✅ 無需 Root、無障礙服務、覆蓋層權限
- ✅ 極簡 UI：只需開啟「通知存取權」即可使用

## 安裝步驟
1. 下載 `app-debug.apk` 安裝
2. 開啟 App → 點選 **「開啟通知存取權設定」** → 開啟 `LineCallVibrator`
3. 系統設定 → 應用程式 → LineCallVibrator → **電池優化 → 不優化**（避免被殺）
4. Zepp Life → 我的 → 小米手環 → 應用通知 → 開啟 `LineCallVibrator` 震動

## 專案結構
```
LineCallVibrator/
├── app/
│   └── src/main/
│       ├── java/com/example/linecallvibrator/
│       │   ├── LineCallListenerService.kt   # 核心：監聽 LINE 通知、發送震動通知
│       │   ├── VibrationForegroundService.kt # 前台服務（常駐）
│       │   └── MainActivity.kt              # UI：開啟權限、測試按鈕
│       ├── AndroidManifest.xml              # 權限、Service 宣告
│       └── res/                             # Layout、Strings、Themes
├── build.gradle.kts                         # App 模組建置設定
├── settings.gradle                          # 專案設定
└── gradle/                                  # Gradle Wrapper
```

## 關鍵技術點
| 元件 | 說明 |
|------|------|
| `NotificationListenerService` | 系統級通知監聽，需使用者授權 |
| `Foreground Service (specialUse)` | Android 14+ 必須聲明 `foregroundServiceType="specialUse"` |
| 模擬通知 ID `9999` | 固定 ID 覆蓋更新，避免通知列堆疊 |
| 震動模式 `longArrayOf(0, 500, 200, 500)` | 手環可感知的震動模式 |
| `NotificationCompat.PRIORITY_MAX` | 最高優先級，確保 Zepp Life 轉發 |

## 同類專案調研
已搜尋 GitHub，**無完全相同方案**（針對 LINE VoIP 來電 + 小米手環持續震動的 NotificationListenerService 實作）。現有專案多為：
- 系統撥號器來電（`CallVibe`、`Vibe`）
- 直接控制手環藍牙（`miband-android`、`Notify & Fitness`），需配對、非通知轉發

## 建置
```bash
# 本機（需 JDK 17 + Android SDK）
./gradlew assembleDebug

# 或使用 GitHub Actions（推薦，無需本機環境）
# 每次 push 會自動產生 app-debug.apk
```

## 測試
1. App 中按 **「測試：啟動 6 次震動」** → 觀察手環
2. 真實 LINE 來電 → 應震動 6 次自動停
3. 主動打 LINE 給朋友 → **不應震動**

## 參數調整
在 `LineCallListenerService.kt`：
```kotlin
private const val MAX_VIBRATION_COUNT = 6   // 震動次數（暫行上限）
private const val VIBRATION_INTERVAL_MS = 2500L  // 間隔毫秒
```

## 授權
MIT License - 詳見 [LICENSE](LICENSE)

## 貢獻
歡迎 Issue / PR。請先閱讀 [CONTRIBUTING.md](CONTRIBUTING.md)。