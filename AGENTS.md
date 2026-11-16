# AGENTS.md - 給 AI Agent 參考的專案指南

## 專案簡述
**LineCallVibrator** - Android App，解決小米手環（Zepp Life）收到 LINE 來電只震動一次的問題。
核心機制：`NotificationListenerService` 監聽 LINE 來電 → 每 2.5 秒發 1 則高優先級模擬通知 → 手環持續震動。

## 關鍵檔案與職責

| 檔案 | 職責 | 修改風險 |
|------|------|----------|
| `app/src/main/java/com/example/linecallvibrator/LineCallListenerService.kt` | **核心邏輯**：通知攔截、來電判斷、震動循環、排除邏輯 | 高 - 修改需實測 |
| `app/src/main/java/com/example/linecallvibrator/VibrationForegroundService.kt` | 前台服務（常駐），Android 14+ 需 `foregroundServiceType="specialUse"` | 低 |
| `app/src/main/java/com/example/linecallvibrator/MainActivity.kt` | UI：權限引導、測試按鈕 | 低 |
| `app/src/main/AndroidManifest.xml` | 權限、Service 宣告、intent-filter | 中 - 權限變更需實測 |
| `app/build.gradle.kts` | 依賴、compileSdk、minSdk | 低 |

## 核心參數（在 LineCallListenerService.kt）
```kotlin
private const val MAX_VIBRATION_COUNT = 6        // 震動次數（暫行上限，非理想設計）
private const val VIBRATION_INTERVAL_MS = 2500L  // 間隔毫秒
private const val SIMULATED_NOTIFICATION_ID = 9999
private const val LINE_PACKAGE = "jp.naver.line.android"
```

## 來電判斷邏輯（關鍵）
```kotlin
// 1. 必須是 LINE 封包
if (sbn.packageName != LINE_PACKAGE) return

// 2. 排除主動撥出
val outgoingKeywords = listOf("撥打中", "撥出", "outgoing", "dialing", "呼叫中")
if (outgoingKeywords.any { allText.contains(it) }) return

// 3. 排除結束狀態
val exclude = listOf("未接", "已結束", "語音訊息", "通話記錄", "missed", "ended", "voicemail"...)
if (exclude.any { allText.contains(it) }) return

// 4. 必須是 CALL 類別
if (n.category != Notification.CATEGORY_CALL) return

// 5. 有來電 Action 或關鍵字
val hasAction = n.actions?.any { it.title?.toString()?.lowercase()?.contains("接聽") == true } ...
val hasKeyword = listOf("來電中", "incoming", "calling"...).any { allText.contains(it) }
if (hasAction || hasKeyword) startVibration()
```

## 已知限制與 Roadmap（重要）
| 限制 | 現狀 | 目標方向 |
|------|------|----------|
| **接聽/掛斷無法即時停止** | LINE VoIP 通知接聽後不會移除、無明確狀態欄位 | 研究 `extras`、RemoteInput、Person、CallScreeningService |
| **固定 6 次上限** | 妥協作法，避免無限震動 | 移除上限，改為精準偵測即時停止 |
| **手環不顯示來電人名** | 模擬通知未帶入聯絡人資訊 | 加入 `setPerson()` / `MessagingStyle` / `EXTRA_TITLE` 含姓名 |

## 建置與測試指令
```bash
# 本機建置（需 JDK 17 + Android SDK 34）
cd ~/.hermes/workspace/LineCallVibrator
./gradlew assembleDebug --no-daemon -Dorg.gradle.jvmargs="-Xmx768m"

# 安裝到裝置
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 即時看 log
adb logcat | grep -E "LineCallListener|VibrationForeground"

# 測試模擬來電（需另一台手機打真實 LINE 來電）
# 或在 App 按「測試：啟動 6 次震動」
```

## 常見坑與解法
| 現象 | 原因 | 解法 |
|------|------|------|
| 手環完全不震動 | Zepp Life 未授權該 App 通知 | Zepp Life → 我的 → 裝置 → 應用通知 → 開啟 LineCallVibrator 震動 |
| 來電不觸發 | 通知存取權未開 | 設定 → 通知存取權 → 開啟 LineCallVibrator |
| Service 被殺 | 電池優化 | 設定 → 應用 → LineCallVibrator → 電池 → 不優化 |
| 主動撥出也震動 | 關鍵字不夠 | 在 `outgoingKeywords` 加入實際通知文字 |
| 接聽後不停 | 通知未移除、無特徵 | 目前靠「通知移除」+「6 次上限」雙保險 |

## 修改建議流程
1. **先跑測試**：`./gradlew assembleDebug` 確保能編譯
2. **單一變更**：每次只改一個邏輯點（如關鍵字、次數、間隔）
3. **實機測試**：真實 LINE 來電才是唯一標準
4. **Commit 訊息**：`feat/fix/refactor: 簡短說明`

## 給 AI 的特別提醒
- **不要**加入 AccessibilityService、Overlay、鄰近感測器等複雜機制（已嘗試過失敗）
- **不要**動 `NotificationListenerService` 的生命週期方法，除非必要
- 模擬通知用 **固定 ID 9999** 覆蓋更新，避免通知列堆疊
- 前台服務 **必須** 用 `specialUse` 類型（Android 14+）
- 所有字串比對用 `.lowercase()` 並包含中英關鍵字
- **核心挑戰**：如何從 LINE 通知精準偵測「接聽/掛斷」事件，這是目前最大技術債