# 案件29: 「LINE連携」ボタンが無反応(バグ修正) — 完了報告

## 概要

設定画面の「LINE連携」行をタップしても何も起きないバグを修正した。

## 根本原因

`SettingsTabScreen.kt` の LINE 連携行の `onClick = { activeModal = "line" }` は、
`activeModal` を読み取って何かを表示する処理が存在しなかったため、タップしても無反応だった。

## 実施内容

既存の Google カレンダー連携と同じ「`staticCompositionLocalOf` + MainActivity 注入」パターンを踏襲した。

1. **`shared/.../ui/App.kt`**
   - `LocalLineLinkHandler = staticCompositionLocalOf<(() -> Unit)?> { null }` を追加。

2. **`app/.../MainActivity.kt`**
   - `CompositionLocalProvider` に `LocalLineLinkHandler provides { ... }` を追加。
   - `Intent(Intent.ACTION_VIEW, Uri.parse(LINE_ADD_FRIEND_URL))` で公式 LINE 友だち追加 URL (`https://lin.ee/utP8awy`) を開く。
   - URL はファイル内 `private const val LINE_ADD_FRIEND_URL` として定数化。

3. **`shared/.../ui/discovery/SettingsTabScreen.kt`**
   - LINE 連携行の `onClick` を `activeModal = "line"` から `LocalLineLinkHandler.current?.invoke()` に変更。
   - 他の設定行（アカウント作成・通知設定・プライバシー・利用規約・プライバシーポリシー）は変更していない。

4. **テスト**
   - `SettingsTabLineLinkJvmTest` (Robolectric, JVM 実行可能): `SettingsTabScreen` 単体で LINE 行タップ時に `LocalLineLinkHandler` が呼ばれることを検証。
   - `MainActivityLineLinkTest` (Robolectric, JVM 実行可能): `MainActivity` 経由で設定タブ → LINE 行をタップし、`ACTION_VIEW` Intent が `https://lin.ee/utP8awy` へ発行されることを検証。
   - `SettingsTabLineLinkTest` (androidTest): 実機/エミュレータ向けに同じシナリオを検証するテストも追加。
   - Robolectric 実行のため、`app/build.gradle.kts` に Robolectric、Compose UI Test、WorkManager テスト用依存を追加し、`isIncludeAndroidResources = true` を有効化。

## テスト結果

```text
:app:testDebugUnitTest
MainActivityLineLinkTest > line連携行をタップすると公式アカウントUrlが開かれる PASSED
SettingsTabLineLinkJvmTest > line行をタップするとLocalLineLinkHandlerが呼ばれる PASSED
(その他既存テストも全て PASS)

:shared:testDebugUnitTest
全テスト PASS

:app:compileDebugAndroidTestKotlin
BUILD SUCCESSFUL ( instrumentation テストはコンパイル済み、実行は実機/エミュレータ必要 )
```

## コミット一覧

- `72132d2` feat(discovery): fix LINE link button and add tests

## 備考

- 本修正は LINE 連携ボタンのみを対象としている。「アカウントを作成」等、他の無反応ボタンは別案件として扱う。
- TASK.md は編集していない。
