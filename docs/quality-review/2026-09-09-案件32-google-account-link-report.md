# 案件32: Googleアカウント連携(設定画面「アカウントを作成」の実装) — 完了報告

## 概要

設定画面の「アカウントを作成」行をタップしても何も起きない状態(案件29と同根の未消費state)を修正し、Googleアカウント連携を実装した。
本連携はプロフィール表示のみの軽量連携で、displayName/email/photoUrlを取得して設定画面に表示するだけである。バックエンド(DiscoverySession/DB)への変更は行わない。

## 実施内容

既存の Google カレンダー連携・LINE 連携と同じ「`staticCompositionLocalOf` + MainActivity 注入」パターンを踏襲した。

1. **`app/.../data/account/GoogleAccountManager.kt`(新規)**
   - Credential Manager API(`androidx.credentials` + `googleid`ライブラリの`GetSignInWithGoogleOption`)でGoogleサインインを行う。
   - 取得するのは身元情報(displayName/email/photoUrl)のみで、アクセストークンやAPIスコープは要求しない。
   - 状態は`GoogleAccountState`(NotConfigured / NotLinked / Linked)として`StateFlow`で公開する。
   - 取得した情報は端末のSharedPreferencesにのみ保存し、バックエンドへは送信しない。
   - `signOut()`で端末の連携情報を削除し、`NotLinked`に戻す。
   - 既存の`GoogleAuthManager`(`app/.../data/calendar/`)とは独立しており、カレンダー機能の変更がアカウント表示に影響しない。

2. **`shared/.../ui/App.kt`**
   - `LocalGoogleAccountLinkHandler = staticCompositionLocalOf<(() -> Unit)?> { null }`を追加(前回セッションで未コミットの変更として存在)。
   - `googleAccountDisplayName: String? = null`パラメータを追加し、`DiscoveryState.setGoogleAccountDisplayName()`へ反映する`LaunchedEffect`を追加。

3. **`app/.../MainActivity.kt`**
   - `GoogleAccountManager.get(application)`でインスタンスを取得。
   - `accountState`をcollectし、`Linked.displayName`を`App`の`googleAccountDisplayName`へ渡す。
   - `CompositionLocalProvider`に`LocalGoogleAccountLinkHandler provides { lifecycleScope.launch { googleAccountManager.signIn() } }`を追加。

4. **`shared/.../discovery/DiscoveryState.kt`**
   - `SettingsUiState`に`googleAccountDisplayName: String? = null`を追加。
   - `setGoogleAccountDisplayName(displayName: String?)`を追加し、UI表示用の状態を更新する。

5. **`shared/.../ui/discovery/SettingsTabScreen.kt`**
   - 「アカウントを作成」行の`onClick`を`activeModal = "account"`から`LocalGoogleAccountLinkHandler.current?.invoke()`に変更。
   - 連携済みの場合はタイトルを「Google アカウント」、サブタイトルに`googleAccountDisplayName`を表示する。
   - 未連携時はタイトル「アカウントを作成」、サブタイトル「未ログイン」のまま。
   - 連携済み時のタップは無反応(再サインインしない)とした。

6. **依存関係**
   - `app/build.gradle.kts`にCredential Manager / googleidの依存を追加(前回セッションで未コミットの変更として存在)。
   - `gradle/libs.versions.toml`に`credentials = "1.3.0"`、`googleId = "1.1.1"`とライブラリ定義を追加(前回セッションで未コミットの変更として存在)。

7. **テスト**
   - `SettingsTabGoogleAccountLinkJvmTest` (Robolectric, JVM 実行可能): 設定画面の「アカウントを作成」行タップ時に`LocalGoogleAccountLinkHandler`が呼ばれること、および連携済み状態でアカウント名が表示されることを検証。

## テスト結果

```text
:app:testDebugUnitTest
SettingsTabGoogleAccountLinkJvmTest > アカウントを作成行をタップするとLocalGoogleAccountLinkHandlerが呼ばれる PASSED
SettingsTabGoogleAccountLinkJvmTest > Googleアカウント連携済みの場合はアカウント名が表示される PASSED
(その他既存テストも全て PASS)

:shared:testDebugUnitTest
全テスト PASS
```

## コミット一覧

- (作成予定) feat(discovery): implement Google account link in settings

## 備考

- 本実装は既存の`GoogleAuthManager`(`app/.../data/calendar/`)を変更していない。
- バックエンド(DB/API)には一切触れていない。
- 今回のスコープ外: バックエンドとの紐付け(DiscoverySessionへのユーザーID付与)、複数端末間のデータ同期、既存カレンダー連携との統合。
- `GetSignInWithGoogleOption`に渡すクライアント ID は、原則として GCP の「Web アプリケーション」タイプの OAuth クライアント ID が必要。現状では既存の`BuildConfig.GOOGLE_OAUTH_CLIENT_ID`を流用しているが、必要に応じて別途 Web クライアント ID を設定すること。
