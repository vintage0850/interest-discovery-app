# セットアップ手順（Google Calendar API 連携）

このアプリは Google Calendar REST API + OAuth 2.0 を使ってカレンダーと連携します。
ビルドして動かすには、**Google Cloud Console 側の設定**と **`local.properties` への値の記入**が必要です。

初めての方は上から順にそのまま進めてください。所要時間はおよそ 20〜30 分です。

- 対象パッケージ名: `com.example.myapplication`
- 使用スコープ: `https://www.googleapis.com/auth/calendar.events`

---

## 全体の流れ

1. Google Cloud プロジェクトを作る
2. Google Calendar API を有効にする
3. OAuth 同意画面を設定する（テストユーザー登録を忘れずに）
4. SHA-1 フィンガープリントを取得する
5. Android 用 OAuth クライアント ID を作る
6. `local.properties` に書く
7. ビルドして確認する

---

## 1. Google Cloud プロジェクトを作る

1. [Google Cloud Console](https://console.cloud.google.com/) を開き、Google アカウントでログインします。
2. 画面上部のプロジェクト選択メニュー（左上、ロゴの右）をクリックします。
3. 「新しいプロジェクト」をクリックします。
4. プロジェクト名を入力します（例: `MyApplication-Calendar`）。
5. 「作成」をクリックします。
6. 作成完了後、**上部のプロジェクト選択メニューで作ったプロジェクトが選択されている**ことを必ず確認してください。
   別プロジェクトを選んだまま以降の作業を進めるのは、最もよくあるミスです。

---

## 2. Google Calendar API を有効にする

1. 左メニューから「API とサービス」→「ライブラリ」を開きます。
2. 検索欄に `Google Calendar API` と入力します。
3. 検索結果の「Google Calendar API」をクリックします。
4. 「有効にする」をクリックします。

有効化されると、API の管理画面に遷移します。

> **メモ:** ここで有効にするのは Google Calendar API **のみ**です。
> 「People API」「Google Sign-In API」などは今回不要です。

---

## 3. OAuth 同意画面を設定する

1. 左メニューから「API とサービス」→「OAuth 同意画面」を開きます。
2. User Type（対象ユーザー）で **「外部」** を選択し、「作成」をクリックします。
   - Google Workspace 組織アカウントの場合のみ「内部」が選べます。個人アカウントでは「外部」しか選べません。
3. 「アプリ情報」を入力します。
   - アプリ名: 任意（例: `MyApplication`）。同意画面にこの名前が表示されます。
   - ユーザーサポートメール: 自分のメールアドレスを選択
   - デベロッパーの連絡先情報: 自分のメールアドレスを入力
4. 「保存して次へ」をクリックします。

### 3-1. スコープの追加

1. 「スコープを追加または削除」をクリックします。
2. 右側に出るパネルの下部にあるフィルタ欄に `calendar.events` と入力して絞り込みます。
3. **`https://www.googleapis.com/auth/calendar.events`** にチェックを入れます。
   - このスコープは「制限付きスコープ」に分類されます。テスト段階（公開ステータスが「テスト」）では審査なしで使えます。
   - `.../auth/calendar`（フルアクセス）は権限が過剰なので選ばないでください。
4. 「更新」をクリックし、続いて「保存して次へ」をクリックします。

### 3-2. テストユーザーの登録（**必須・重要**）

1. 「テストユーザー」の画面で「+ ADD USERS」をクリックします。
2. **アプリで実際にログインする Google アカウントのメールアドレス**を入力します。
   - 開発機で使うアカウント、実機で使うアカウントをすべて登録してください。
   - プロジェクトのオーナー自身も、ここに登録しないと弾かれます。
3. 「保存」→「保存して次へ」をクリックします。

> ここを飛ばすと、アプリからのログイン時に **`403 access_denied`** になります。
> 詳しくは末尾の「よくあるハマりどころ」を参照してください。

---

## 4. SHA-1 フィンガープリントを取得する

Android 用の OAuth クライアント ID には、署名証明書の **SHA-1 フィンガープリント**が必要です。

### 方法 A: Gradle を使う（推奨）

プロジェクトルートで以下を実行します。

PowerShell の場合:

```powershell
cd C:\Users\<ユーザー名>\AndroidStudioProjects\MyApplication
.\gradlew signingReport
```

Git Bash の場合:

```bash
cd /c/Users/<ユーザー名>/AndroidStudioProjects/MyApplication
./gradlew signingReport
```

出力の中から `Variant: debug` / `Config: debug` のブロックを探し、`SHA1:` の行をコピーします。

```
Variant: debug
Config: debug
Store: C:\Users\<ユーザー名>\.android\debug.keystore
Alias: AndroidDebugKey
MD5:  ...
SHA1: A1:B2:C3:D4:E5:F6:07:18:29:3A:4B:5C:6D:7E:8F:90:A1:B2:C3:D4
SHA-256: ...
```

### 方法 B: keytool を直接使う

Android Studio 同梱の JDK に含まれる `keytool` を使います。

PowerShell の場合:

```powershell
& "$env:JAVA_HOME\bin\keytool.exe" -list -v `
  -alias androiddebugkey `
  -keystore "$env:USERPROFILE\.android\debug.keystore" `
  -storepass android -keypass android
```

`JAVA_HOME` が未設定なら、Android Studio 同梱の JDK を直接指定します（パスは環境により異なります）:

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -list -v `
  -alias androiddebugkey `
  -keystore "$env:USERPROFILE\.android\debug.keystore" `
  -storepass android -keypass android
```

Git Bash の場合:

```bash
keytool -list -v \
  -alias androiddebugkey \
  -keystore "$USERPROFILE/.android/debug.keystore" \
  -storepass android -keypass android
```

出力中の `SHA1:` の値（`AA:BB:CC:...` 形式、コロン区切り 20 バイト）をコピーします。

### debug 用と release 用で SHA-1 は違います（要注意）

| ビルド種別 | 署名に使われるキーストア | SHA-1 |
|---|---|---|
| debug | `~/.android/debug.keystore`（自動生成、全プロジェクト共通） | 開発 PC ごとに異なる |
| release | 自分で作成した release キーストア | debug とは**別の値** |
| Play ストア配信 | Google Play アプリ署名（Google 側の鍵で再署名） | Play Console 側の値 |

つまり:

- **開発 PC が複数ある / チーム開発の場合**、各 PC の debug SHA-1 をすべて OAuth クライアントに登録するか、PC ごとにクライアント ID を分ける必要があります。
- **release ビルドを配布する前に**、release キーストアの SHA-1 を追加した OAuth クライアント ID を別途作成してください。debug 用のままだと本番で `10: DEVELOPER_ERROR` になります。
- **Google Play アプリ署名を使う場合**、登録すべきは自分の release キーストアではなく、Play Console の「設定 → アプリの署名」に表示される **アプリ署名鍵証明書の SHA-1** です。

release キーストアの SHA-1 は次で取得できます:

```powershell
& "$env:JAVA_HOME\bin\keytool.exe" -list -v -alias <エイリアス名> -keystore <release.jks へのパス>
```

---

## 5. Android 用 OAuth クライアント ID を作る

1. 左メニューから「API とサービス」→「認証情報」を開きます。
2. 上部の「+ 認証情報を作成」→「OAuth クライアント ID」をクリックします。
3. アプリケーションの種類で **「Android」** を選択します。
   - 「ウェブ アプリケーション」ではありません。間違えやすいので注意してください。
4. 以下を入力します。
   - 名前: 任意（例: `MyApplication Android debug`）
   - **パッケージ名: `com.example.myapplication`**
   - **SHA-1 証明書のフィンガープリント: 手順 4 で取得した値**（コロン区切りのまま貼り付け可）
5. 「作成」をクリックします。
6. 表示された **クライアント ID**（`123456789012-xxxxxxxxxxxxxxxxxxxx.apps.googleusercontent.com` の形式）をコピーします。
   - 後からでも「認証情報」一覧の該当行をクリックすれば確認できます。
   - Android 用クライアントには**クライアント シークレットはありません**。これは正常です。

---

## 6. `local.properties` に書く

プロジェクトルートの `local.properties` に、次の 1 行を追記します。

```properties
GOOGLE_OAUTH_CLIENT_ID=123456789012-xxxxxxxxxxxxxxxxxxxx.apps.googleusercontent.com
```

`local.properties` がまだ無い場合は、サンプルからコピーして作成してください。

PowerShell:

```powershell
Copy-Item local.properties.example local.properties
```

Git Bash:

```bash
cp local.properties.example local.properties
```

記入時の注意:

- **値をクォートで囲まない**でください（`GOOGLE_OAUTH_CLIENT_ID="..."` は誤り。クォートごと値として読まれます）
- `=` の前後にスペースを入れない
- 行末に余分なスペースを入れない
- `local.properties` は `.gitignore` 済みです。**絶対にコミットしないでください**

この値は Gradle が読み取り、次の定数としてアプリから参照できます。

```kotlin
BuildConfig.GOOGLE_OAUTH_CLIENT_ID  // 型: String
```

未設定の場合は **空文字 `""`** になります（ビルドは通ります）。アプリ側は空文字を「未設定」として扱い、カレンダー連携機能を無効化する実装になっています。

---

## 7. ビルドして確認する

```bash
./gradlew :app:assembleDebug
```

生成された `BuildConfig` に値が入っているかは、次のファイルで確認できます。

```
app/build/generated/source/buildConfig/debug/com/example/myapplication/BuildConfig.java
```

以下のような行があれば成功です。

```java
public static final String GOOGLE_OAUTH_CLIENT_ID = "123456789012-xxxx.apps.googleusercontent.com";
```

`local.properties` を書き換えても値が変わらない場合は、Gradle の設定キャッシュが効いている可能性があります。次で再ビルドしてください。

```bash
./gradlew :app:assembleDebug --rerun-tasks
```

---

## よくあるハマりどころ

### `403 access_denied` / 「このアプリは Google で確認されていません」で先に進めない

**原因:** OAuth 同意画面の公開ステータスが「テスト」なのに、ログインしようとしているアカウントが**テストユーザーに登録されていない**。

**対処:** 「API とサービス」→「OAuth 同意画面」→「テストユーザー」に、そのアカウントのメールアドレスを追加してください。プロジェクトのオーナー本人であっても登録が必要です。追加後、アプリ側で一度ログアウトしてから再試行してください。

なお、テストモードのテストユーザーは **最大 100 人**まで、また一度与えた認可も **7 日で失効**します（失効するとアプリから再度ログインが必要になります）。長期運用するには同意画面を「本番環境」に公開する必要があり、制限付きスコープ（`calendar.events`）を含むため Google の審査が必要になります。

### `10: DEVELOPER_ERROR` / `Status{statusCode=DEVELOPER_ERROR}`

**原因:** OAuth クライアント ID に登録した **パッケージ名または SHA-1 が、実際にインストールした APK と一致していない**。

**チェックリスト:**

- パッケージ名は `com.example.myapplication` で登録したか（`applicationIdSuffix` を使っていないか）
- SHA-1 は今ビルドしている PC の `debug.keystore` のものか（別 PC の値ではないか）
- release ビルドを debug 用のクライアント ID で動かそうとしていないか
- Android 用（ウェブ用ではない）クライアントとして作成したか

`./gradlew signingReport` の出力と Cloud Console の登録値を並べて突き合わせてください。

### `BuildConfig.GOOGLE_OAUTH_CLIENT_ID` が空文字のまま

- `local.properties` の場所が違う（`app/` 配下ではなく**プロジェクトルート**に置く）
- キー名のタイポ（`GOOGLE_OAUTH_CLIENT_ID` は全て大文字、アンダースコア区切り）
- 値をダブルクォートで囲んでしまっている
- Gradle Sync / 再ビルドをしていない

### `403 insufficient_permissions` / API 呼び出しだけ失敗する

**原因:** 同意画面で `.../auth/calendar.events` スコープを追加していない、または既存アカウントが古いスコープのまま認可されている。

**対処:** 同意画面にスコープを追加したうえで、[アカウントのアクセス権限ページ](https://myaccount.google.com/permissions) から該当アプリのアクセス権を削除し、再度アプリからログインし直してください。

### `403 Google Calendar API has not been used in project ... before or it is disabled`

**原因:** 手順 2 の API 有効化を、**別のプロジェクト**で行った（または未実施）。

**対処:** エラーメッセージ中のプロジェクト番号と、OAuth クライアント ID の先頭の数字が一致しているか確認してください。一致していなければ、正しいプロジェクトで Calendar API を有効化します。有効化の反映には数分かかることがあります。

### `401 Unauthorized` が時間経過後に出る

アクセストークンの有効期限は約 1 時間です。

このアプリは**リフレッシュトークンを一切扱いません**（Android クライアントでは取得できません）。
代わりに、期限が切れたら `AuthorizationClient.authorize()` を呼び直してアクセストークンを取り直す設計です。
同意済みのアカウントであれば、この再認可はユーザー操作なしで暗黙に完了します。

そのため 401 が続く場合は、次のどちらかを疑ってください。

- **同意が取り消されている**: [アカウントのアクセス権限ページ](https://myaccount.google.com/permissions) からアプリのアクセス権が削除されていないか
- **テストモードの期限切れ**: 公開ステータスが「テスト」の場合、付与された認可は一定期間（7 日）で失効します。アプリから再度ログインし直してください

---

## 参考リンク

- [Google Calendar API リファレンス](https://developers.google.com/calendar/api/v3/reference)
- [Android での認可（AuthorizationClient）](https://developers.google.com/identity/authorization/android)
- [OAuth 2.0 スコープ一覧](https://developers.google.com/identity/protocols/oauth2/scopes#calendar)
