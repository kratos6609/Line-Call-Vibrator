# 貢獻指南

歡迎貢獻！請遵循以下流程。

## 開發環境
- JDK 17
- Android SDK 34 (API 34)
- Gradle 8.5+ (使用專案內 Wrapper)

## 分支策略
```
main (受保護，只接受 PR 合併)
  ↑
feature/xxx  ← 功能/修正分支，命名：feature/描述、fix/描述、refactor/描述
```

## Commit 訊息規範
```
<type>: <簡短說明>

類型：
feat     新功能
fix      修復 Bug
refactor 重構（不改行為）
docs     文件更新
chore    雜項（建置腳本、依賴更新等）
```

範例：
```
feat: add outgoing call exclusion keywords
fix: vibration stops immediately after answer
refactor: simplify notification filter logic
```

## PR 流程
1. Fork 專案 → 建立 feature 分支
2. 修改程式碼，**單一職責** per commit
3. 本機跑 `./gradlew assembleDebug` 確保編譯通過
4. 實機測試（真實 LINE 來電）
5. 發 PR → 填寫模板 → 等待 CI 通過 + Review
6. Merge 到 main

## 測試清單（PR 必須通過）
- [ ] `./gradlew assembleDebug` 成功
- [ ] App 安裝、開啟通知存取權無 Crash
- [ ] **測試按鈕「啟動 6 次震動」** → 手環震 6 次
- [ ] **真實 LINE 來電** → 手環震 6 次自動停
- [ ] **主動打 LINE 給朋友** → 手環**不震動**
- [ ] 接聽/掛斷/未接 → 震動立即停止
- [ ] logcat 無錯誤（`LineCallListener`、`VibrationForeground`）

## 程式碼風格
- Kotlin 官方 coding conventions
- 縮排 4 spaces，無 tab
- 類別/函數加 KDoc 註解（public API 必須）
- 避免 magic numbers，用 `companion object const val`

## 問題回報
使用 GitHub Issues，請提供：
- 手機型號、Android 版本
- 小米手環型號、Zepp Life 版本
- 重現步驟
- `adb logcat | grep LineCallListener` 相關輸出