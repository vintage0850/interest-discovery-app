# 案件5: サブタスク表示改善・カレンダー手動登録・通知時間手動設定

## 状態

実装中

## 担当

Kimi（実装）

## 対象ファイル

- `app/src/main/java/com/example/myapplication/data/Task.kt`
- `app/src/main/java/com/example/myapplication/data/AppDatabase.kt`
- `app/src/main/java/com/example/myapplication/data/TaskDao.kt`
- `app/src/main/java/com/example/myapplication/data/TaskRepository.kt`
- `app/src/main/java/com/example/myapplication/data/NotificationWindowPreferences.kt`
- `app/src/main/java/com/example/myapplication/data/calendar/GoogleCalendarApi.kt`
- `app/src/main/java/com/example/myapplication/data/calendar/GoogleCalendarSync.kt`
- `app/src/main/java/com/example/myapplication/work/TaskNotificationScheduler.kt`
- `app/src/main/java/com/example/myapplication/work/TaskNotificationReceiver.kt`
- `app/src/main/java/com/example/myapplication/work/BootReceiver.kt`
- `app/src/main/java/com/example/myapplication/work/FreeTimeCheckWorker.kt`
- `app/src/main/java/com/example/myapplication/OptionalTimePicker.kt`
- `app/src/main/java/com/example/myapplication/TaskEditDialog.kt`
- `app/src/main/java/com/example/myapplication/NotificationSettingsScreen.kt`
- `app/src/main/java/com/example/myapplication/AddTaskScreen.kt`
- `app/src/main/java/com/example/myapplication/TaskListScreen.kt`
- `app/src/main/java/com/example/myapplication/MainActivity.kt`
- `app/src/main/java/com/example/myapplication/TaskViewModel.kt`
- `app/src/main/AndroidManifest.xml`
- テストファイル群

## 実行ログ

```
2026-08-26: 実装開始。Kimi が Task 1 から順次実装中。
2026-08-26 Task 1: Task.eventHasTime 追加 + DB v6 移行。:app:assembleDebug BUILD SUCCESSFUL。スキーマ 6.json 生成済み。connectedDebugAndroidTest は実機/エミュレータ未接続のため実行不可（adb devices で 0 台）。コミット 8cf2d36。
2026-08-26 Task 2: TaskDao/TaskRepository 拡張。:app:testDebugUnitTest --tests "com.example.myapplication.TaskViewModelCalendarTest" BUILD SUCCESSFUL。コミット faf82e9。
2026-08-26 Task 3: NotificationWindowPreferences 追加。:app:testDebugUnitTest --tests "com.example.myapplication.data.NotificationWindowPreferencesTest" BUILD SUCCESSFUL。コミット 68a0af4。
2026-08-26 Task 4: GoogleCalendarSync 時刻指定・サブタスク対応。:app:testDebugUnitTest --tests "com.example.myapplication.data.calendar.GoogleCalendarSyncTest" BUILD SUCCESSFUL。コミット 026655a。
```

## 引き継ぎメモ

- 全 12 タスクを順番に実装し、各タスク最後に 1 タスク=1コミットする。
- 同じ修正に 2 回失敗したタスクがあれば、そこで停止して報告する。
- 全タスク完了後は `./gradlew :app:testDebugUnitTest :app:assembleDebug` を実行して結果を確認する。
