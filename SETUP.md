# セットアップ手順（Google Calendar API 連携）

このアプリは Google Calendar REST API + OAuth 2.0 を使ってカレンダーと連携します。
ビルドして動かすには、**Google Cloud Console 側の設定**と **`local.properties` への値の記入**が必要です。

初めての方は上から順にそのまま進めてください。所要時間はおよそ 20〜30 分です。

- 対象パッケージ名: `com.example.myapplication`
- 使用スコープ: `https://www.googleapis.com/auth/calendar.events`

> **この手順書は 2026-08 時点の Console 画面に合わせてあります。**
> かつての「API とサービス → OAuth 同意画面」は **Google Auth Platform**
> （左メニュー: 概要 / ブランディング / 対象 / クライアント / データアクセス / 検証センター / 設定）に
> 置き換わりました。古い記事の手順とは画面構成が異なります。
> 直リンク: <https://console.cloud.google.com/auth/overview>

---

## 全体の流れ

1. Google Cloud プロジェクトを作る
2. Google Calendar API を有効にする
3. Google Auth Platform（旧 OAuth 同意画面）を構成する（テストユーザー登録を忘れずに）
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
   - 入力すると、下に「プロジェクト ID: `myapplication-calendar`」のように**自動生成された ID** が表示されます。
     この ID は後から変更できません。以降の URL で使うので控えておいてください。
5. 「請求先アカウント」の選択を求められた場合は、既定のものを選んで構いません。
   **Calendar API の利用に課金は発生しません**（このプロジェクトで有効にするのは Calendar API だけです）。
6. 「作成」をクリックします。
7. 作成完了後、**上部のプロジェクト選択メニューで作ったプロジェクトが選択されている**ことを必ず確認してください。
   別プロジェクトを選んだまま以降の作業を進めるのは、最もよくあるミスです。
   - 作成直後は**元のプロジェクトが選択されたまま**になることがあります。切り替わっていなければ手動で選び直してください。
   - 以降、URL の末尾に `?project=<プロジェクト ID>` を付けて開けば、選択ミスを確実に防げます。

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

## 3. Google Auth Platform を構成する（旧「OAuth 同意画面」）

<https://console.cloud.google.com/auth/overview?project=＜プロジェクト ID＞> を開きます。
左メニューの「API とサービス」→「OAuth 同意画面」からも、同じ画面にリダイレクトされます。

未構成のプロジェクトでは「**Google Auth Platform はまだ構成されていません**」と表示されます。
「**開始**」をクリックすると、4 ステップのウィザードが始まります。

| ステップ | 入力内容 |
|---|---|
| ① アプリ情報 | **アプリ名**: 任意（例: `MyApplication`）。同意画面にこの名前が表示されます<br>**ユーザー サポートメール**: プルダウンから自分のメールアドレスを選択 |
| ② 対象 | **「外部」** を選択<br>個人 Google アカウントでは「内部」は選べません（Workspace 組織アカウント専用） |
| ③ 連絡先情報 | 自分のメールアドレスを入力。Google からプロジェクト変更の通知が届きます |
| ④ 終了 | 「Google API サービス: ユーザーデータに関するポリシーに同意します」にチェック |

各ステップで「次へ」、最後に「**作成**」をクリックします。

作成が終わると左メニューが
「概要 / ブランディング / 対象 / クライアント / データアクセス / 検証センター / 設定」の 7 項目に変わります。
以降の作業はこのメニューから行います。

> ウィザードの入力内容は後から「ブランディング」「対象」の各画面で変更できます。

### 3-1. テストユーザーの登録（**必須・重要**）

左メニュー「**対象**」を開きます（<https://console.cloud.google.com/auth/audience>）。

1. 「公開ステータス: **テスト中**」「ユーザーの種類: **外部**」になっていることを確認します。
2. 下へスクロールして「**テストユーザー**」セクションの「**+ Add users**」をクリックします。
3. 右から出るパネルに、**アプリで実際にログインする Google アカウントのメールアドレス**を入力します。
   - 開発機で使うアカウント、実機で使うアカウントをすべて登録してください。
   - プロジェクトのオーナー自身も、ここに登録しないと弾かれます。
4. 「**保存**」をクリックします。
   - **1 回目のクリックで入力欄のテキストがチップ（丸囲みのタグ）に変わるだけのことがあります。**
     カウンタが `1/100` になっていることを確認し、**もう一度「保存」を押してください。**
   - 保存後、テストユーザー一覧にメールアドレスの行が現れれば成功です。
     パネルのスピナーが回りっぱなしになる場合は、ページを再読み込みして一覧を確認してください
     （実際には登録が完了していることがあります）。

> ここを飛ばすと、アプリからのログイン時に **`403 access_denied`** になります。
> 詳しくは末尾の「よくあるハマりどころ」を参照してください。

テストユーザーは **最大 100 人**まで。この上限はアプリの全期間でカウントされます。

### 3-2. スコープの追加

左メニュー「**データアクセス**」を開きます（<https://console.cloud.google.com/auth/scopes>）。

1. 「**スコープを追加または削除**」をクリックします。
2. 右側に出るパネルのフィルタ欄に `calendar.events` と入力して絞り込みます。
3. **`https://www.googleapis.com/auth/calendar.events`** にチェックを入れます。
   - このスコープは「**制限付きのスコープ**」に分類されます。公開ステータスが「テスト中」の間は審査なしで使えます。
   - `.../auth/calendar`（フルアクセス）は権限が過剰なので選ばないでください。
4. 「更新」→「**Save**」をクリックします。

> **この画面は必須ではありません。** Android アプリのスコープ要求は実行時の
> `AuthorizationClient.authorize()` が行うため、ここに未登録でもテスト運用では動きます。
> 登録しておく意味は、`403 insufficient_permissions` の予防と、将来の本番公開・審査への備えです。
>
> なお **この「データアクセス」画面はブラウザごと固まることがあります**（2026-08 時点で複数回再現）。
> 固まったらタブを閉じて開き直してください。急ぎでなければ後回しで構いません。

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

左メニュー「**クライアント**」を開きます（<https://console.cloud.google.com/auth/clients>）。
旧「API とサービス」→「認証情報」に相当する画面です。

1. 上部の「**+ クライアントを作成**」をクリックします。
2. 「アプリケーションの種類」で **「Android」** を選択します。
   - 「ウェブ アプリケーション」ではありません。一覧の先頭にあるので誤選択しやすい箇所です。
   - 選択して初めて、名前・パッケージ名・SHA-1 の入力欄が現れます。
3. 以下を入力します。
   - **名前**: 任意（例: `MyApplication Android debug`）。既定値は `Android クライアント 1` です
   - **パッケージ名**: `com.example.myapplication`
   - **SHA-1 証明書のフィンガープリント**: 手順 4 で取得した値（コロン区切りのまま貼り付け可）
4. 「**アプリの所有権を確認する（省略可）**」は**スキップして構いません**。開発・テストには不要です。
5. 「**作成**」をクリックします。
6. 「OAuth クライアントを作成しました」ダイアログに **クライアント ID**
   （`123456789012-xxxxxxxxxxxxxxxxxxxx.apps.googleusercontent.com` の形式）が表示されるので、
   右側のコピーアイコンでコピーします。
   - 後からでも「クライアント」一覧の該当行から確認できます。
   - Android 用クライアントには**クライアント シークレットはありません**。これは正常です。
     「JSON をダウンロード」も、このアプリでは使いません。
7. 画面下部に「**設定が有効になるまで 5 分から数時間かかることがあります**」と表示されます。
   作成直後に `10: DEVELOPER_ERROR` が出る場合は、しばらく待ってから再試行してください。

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

**原因:** 公開ステータスが「テスト中」なのに、ログインしようとしているアカウントが**テストユーザーに登録されていない**。

**対処:** Google Auth Platform の「**対象**」→「テストユーザー」（<https://console.cloud.google.com/auth/audience>）に、そのアカウントのメールアドレスを追加してください。プロジェクトのオーナー本人であっても登録が必要です。追加後、アプリ側で一度ログアウトしてから再試行してください。

なお、テストモードのテストユーザーは **最大 100 人**まで、また一度与えた認可も **7 日で失効**します（失効するとアプリから再度ログインが必要になります）。長期運用するには「対象」画面の「**アプリを公開**」で本番環境に切り替える必要があり、制限付きのスコープ（`calendar.events`）を含むため Google の審査が必要になります。

### `10: DEVELOPER_ERROR` / `Status{statusCode=DEVELOPER_ERROR}`

**原因:** OAuth クライアント ID に登録した **パッケージ名または SHA-1 が、実際にインストールした APK と一致していない**。

**チェックリスト:**

- パッケージ名は `com.example.myapplication` で登録したか（`applicationIdSuffix` を使っていないか）
- SHA-1 は今ビルドしている PC の `debug.keystore` のものか（別 PC の値ではないか）
- release ビルドを debug 用のクライアント ID で動かそうとしていないか
- Android 用（ウェブ用ではない）クライアントとして作成したか
- **クライアントを作成した直後ではないか**（反映に 5 分〜数時間かかることがあります）

`./gradlew signingReport` の出力と、Google Auth Platform の「クライアント」画面の登録値を並べて突き合わせてください。

### `BuildConfig.GOOGLE_OAUTH_CLIENT_ID` が空文字のまま

- `local.properties` の場所が違う（`app/` 配下ではなく**プロジェクトルート**に置く）
- キー名のタイポ（`GOOGLE_OAUTH_CLIENT_ID` は全て大文字、アンダースコア区切り）
- 値をダブルクォートで囲んでしまっている
- Gradle Sync / 再ビルドをしていない

### `403 insufficient_permissions` / API 呼び出しだけ失敗する

**原因:** 「データアクセス」で `.../auth/calendar.events` スコープを追加していない、または既存アカウントが古いスコープのまま認可されている。

**対処:** 手順 3-2（<https://console.cloud.google.com/auth/scopes>）でスコープを追加したうえで、[アカウントのアクセス権限ページ](https://myaccount.google.com/permissions) から該当アプリのアクセス権を削除し、再度アプリからログインし直してください。

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

### `gcloud` コマンドでこの手順を自動化したい

**できません。** プロジェクト作成（`gcloud projects create`）と API 有効化
（`gcloud services enable calendar-json.googleapis.com`）までは CLI でできますが、
**Android 用 OAuth クライアント ID の発行には API / CLI の口がありません。**
Console の画面操作が必須です。

- `gcloud alpha iam oauth-clients` は Workforce Identity 連携用で、別物です
- `gcloud alpha iap oauth-clients` は IAP / ウェブ用で、Android クライアントは作れません

---

## リリース署名（Google Play 提出用）

2026-08-21 に、Play Store 提出に必要なリリース署名設定を追加した。

- `local.properties` に `RELEASE_STORE_FILE` / `RELEASE_STORE_PASSWORD` / `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD` の4項目を記入すると、
  `./gradlew :app:assembleRelease` / `:app:bundleRelease` が自動的に署名される。未記入なら release ビルドは未署名のまま（開発用ビルドは通る）。
- このリポジトリの `release.keystore.jks`（プロジェクトルート直下）は、開発機での動作確認用として自動生成したキーストア。`.gitignore` 済みでコミットされない。
- **本番の Play Store 提出に使うキーストアは、この自動生成ファイルをそのまま使ってよいが、必ず以下を行うこと。**
  1. `release.keystore.jks` と `local.properties` 内の4つのパスワード/エイリアスを、リポジトリ外（パスワードマネージャー、暗号化した外部ドライブ等）にバックアップする。
  2. Play Console の初回アップロード時に **Play App Signing** を有効化する（Google 推奨・デフォルト）。有効化すると、万一このアップロード鍵を紛失しても Play Console の申請フォームからリセットできる。有効化しないまま紛失すると、このアプリは二度と更新できなくなる。
  3. Google Calendar OAuth クライアント（Google Auth Platform の「クライアント」画面）に登録する SHA-1 は、**このアップロード鍵の SHA-1 ではなく**、Play Console「設定 → アプリの署名」に表示される **アプリ署名鍵証明書の SHA-1**（Play App Signing 有効化後にのみ表示される）を使うこと。手順4の「debug 用と release 用で SHA-1 は違います」の表を参照。

## release ビルドの minify / シュリンク

- `isMinifyEnabled = true` / `isShrinkResources = true` を有効化済み（2026-08-21）。R8 + `proguard-rules.pro` の追加ルールで、Retrofit・kotlinx.serialization・Room・Play Services Identity のリフレクション経由コードが壊れないことをエミュレータでの実機起動確認済み。
- 依存ライブラリを追加・更新した場合は、`./gradlew :app:assembleRelease` の成功だけでなく、**実機/エミュレータでの起動確認**を必ず行うこと（ビルドが通っても実行時にクラスが見つからず落ちることがある。今回もこの経路で起動即クラッシュのバグを検出した）。

## compileSdk / targetSdk 36（2026-08-21 変更）

Google Play の Target API レベル要件により、2026-08-31 以降の新規アプリ・更新は Android 16（API 36）をターゲットにする必要があるため、35 から 36 へ引き上げた。要件は今後も定期的に変わるため、次回の大型更新時に [Meet Google Play's target API level requirement](https://developer.android.com/google/play/requirements/target-sdk) で最新の期限を確認すること。

---

## 通知機能の操作方法と既知の制約（2026-08-26 追記）

案件5で追加された「手動通知時刻の設定」「通知有効時間帯（ウィンドウ）の設定」「端末再起動後の再予約」について、利用者向けの操作手順と実機未確認の制約をまとめる。

### 操作方法

#### 1. タスク追加時に手動通知時刻を設定する

1. タスク一覧画面右下の「＋」（フローティングボタン）をタップし、タスク追加画面を開く。
2. 「通知時刻を設定」スイッチを ON にする。
3. 時刻ピッカーで通知したい時刻を選ぶ。
4. 保存すると、指定時刻に AlarmManager から通知が発行される。

#### 2. 既存タスクの通知時刻を変更・解除する

1. タスク一覧で該当タスクのタイトルをタップして編集ダイアログを開く。
2. 「通知時刻」欄で時刻を変更するか、スイッチを OFF にして解除する。
3. 確定すると、既存の予約が解除されて新しい時刻で再予約される（解除の場合は予約のみ解除）。

#### 3. 通知有効時間帯（ウィンドウ）を設定する

1. タスク一覧画面のメニューから「通知設定」を開く。
2. 「開始」と「終了」の時刻を選ぶ。
3. この時間帯の内だけ「空き時間です」通知（案件3）が発行される。手動通知時刻はウィンドウの影響を受けない。

#### 4. 端末再起動後の再予約

手動通知は端末を再起動すると AlarmManager の予約が失われるが、
`RECEIVE_BOOT_COMPLETED` 権限を使って起動時に自動的に再予約される。
ユーザーが特別な操作をする必要はない。

### 必要な権限

- `POST_NOTIFICATIONS`（Android 13+）：初回起動時に権限ダイアログが表示される。拒否してもアプリはクラッシュしないが、通知が届かなくなる。
- `SCHEDULE_EXACT_ALARM`（Android 12+）：正確な時刻に発火させるために設定アプリで ON にする必要がある。OFF の場合は `setAndAllowWhileIdle`（数分の誤差を許容）にフォールバックする。
- `RECEIVE_BOOT_COMPLETED`：再起動後の再予約に使う。インストール直後や権限を剥奪されていると再予約されない。

### 既知の制約（実機未確認を含む）

- **AlarmManager の実際の発火時刻は保証されない。** Doze モードやアプリスタンバイ、電池最適化によって、指定時刻から数分〜数時間遅れることがある。重要な通知には向かない。
- **exact alarm 権限が無い場合の誤差は最大数分。** Android 12+ で `SCHEDULE_EXACT_ALARM` が OFF の場合、フォールバック先の `setAndAllowWhileIdle` は OS の判断で遅延する。
- **端末再起動後の再予約は未実機確認。** ユニットテストでは未来の未完了タスクが抽出・再予約されることを検証しているが、実際の端末再起動での復元は未確認。
- **通知の実際の表示は未実機確認。** 通知チャンネル作成・Permission チェックのコードはあるが、実端末で指定時刻に通知が表示されることは未確認。
- **「始める」アクションボタンからの遷移は未実機確認。** 通知をタップしてタスクを「進行中」にする機能（案件3との連携）は、エミュレータ/実機での動作確認が残っている。
- **通知とカレンダー予定の編集が同時に行われる場合、タスク ID 単位の Mutex で直列化している。** タイトルと予定時刻を同時に変更しても、Google カレンダー側には両方の新値が 1 回の API 呼び出しで反映される（ユニットテスト済み）。

---

## 参考リンク

- [Google Auth Platform（旧 OAuth 同意画面）](https://console.cloud.google.com/auth/overview)
- [Google Calendar API リファレンス](https://developers.google.com/calendar/api/v3/reference)
- [Android での認可（AuthorizationClient）](https://developers.google.com/identity/authorization/android)
- [OAuth 2.0 スコープ一覧](https://developers.google.com/identity/protocols/oauth2/scopes#calendar)

## iOSアプリのセットアップ（Mac環境で実施）

`:shared` モジュールをiOSシミュレータで動かすための `iosApp` Xcodeプロジェクトは、
Windows環境では作成・ビルドできない（Kotlin/NativeのiOSターゲットも同様）。
以下の手順はMacで実施すること。

### 前提

- Xcode（最新の安定版）
- このリポジトリを `git pull` 済みであること（`iosApp/iosApp/iOSApp.swift` と
  `iosApp/iosApp/ContentView.swift` が含まれている）

### 手順

1. `./gradlew :shared:compileKotlinIosSimulatorArm64` を実行し、`:shared` がiOSシミュレータ向けに
   コンパイルできることを確認する（Windowsでは実行できなかった検証）。エラーが出た場合はここで解消する。
2. Xcodeで「Create a new Xcode project」→「iOS」→「App」を選択する。
   - Product Name: `iosApp`
   - Interface: SwiftUI
   - Language: Swift
   - 保存先: このリポジトリの `iosApp/` 直下（既存の `iosApp/iosApp/*.swift` を上書きしないよう、
     プロジェクト作成後に生成された `ContentView.swift`/`iOSApp.swift`（またはApp名と同名のファイル）を
     このリポジトリのファイルで置き換える）
3. `:shared` が生成するフレームワークをXcodeプロジェクトにリンクする。
   Kotlin Multiplatformの公式ドキュメント（"Connect the framework to your iOS project"）に従い、
   ビルドフェーズに `:shared` のGradleタスクを呼ぶRun Scriptを追加する方法が最も簡単
   （`kotlinlang.org/docs/multiplatform/multiplatform-integrate-in-existing-app.html` 等を参照）。
4. Xcodeでシミュレータを選択してビルド・実行し、タスク一覧画面が表示されることを確認する。
5. 確認できたら `git add iosApp/ && git commit` でXcodeプロジェクトファイル一式をコミットする
   （`.xcodeproj/project.pbxproj` を含む。これはWindows側では生成できないためMacでのコミットが必須）。

### 既知の制約

- `IosDatabaseDriverFactory`（`shared/src/iosMain/.../DatabaseDriverFactory.ios.kt`）が
  `PRAGMA foreign_keys=ON` を明示していない点はKMP Phase 1 Aの最終レビューで指摘済み。
  `NativeSqliteDriver` のデフォルト挙動を確認し、外部キー制約（`ON DELETE SET NULL`/`CASCADE`）が
  期待通り効くか確認すること。効いていない場合は `androidMain` 版と同様に明示的な
  `PRAGMA foreign_keys=ON;` の実行が必要。
