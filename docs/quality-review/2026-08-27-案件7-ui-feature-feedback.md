# 品質レビュー: 案件7 UI/機能フィードバック

- 総合判定: **CHANGES REQUIRED**
- Gate 3.5判定: **CHANGES REQUIRED**
- Gate 4判定: **CHANGES REQUIRED**
- レビュー担当: Codex（設計・規約軸の独立レビューを併用）
- レビュー日: 2026-08-27
- 対象: `master` (`4a9aa4a`) ... `HEAD` (`13df158`)
- 対象仕様: `docs/superpowers/specs/2026-08-27-案件7-ui-feature-feedback-design.md`

## レビュー範囲と証跡

- `git diff master...HEAD`の12ファイル（807行追加、121行削除）を確認した。
- `git diff --check master...HEAD` は成功した。
- 仕様書はこのworktree/`HEAD`には存在せず、main checkoutの同名ファイルを読んで対照した。レビューの再現性のため、仕様書も追跡対象に含める必要がある。
- GradleはCodex環境で再実行せず、`TASK.md`案件7のClaude検証記録 `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin --no-daemon` = **BUILD SUCCESSFUL（失敗0）** を採用した。
- `connectedDebugAndroidTest`は端末未接続のため未実施。指示どおりブロッキング理由にしていない。

## Gate 3.5: 設計・SOLID・保守性

### 良い点

- 表示用ソートをDBから分離し、Roomスキーマを変更していない。
- サブタスクのタイトルと完了状態はDAOの列単位更新に分け、全列上書きによる巻き戻りを避けている。
- サブタスク名変更は既存のタスクID単位`Mutex`と`doSyncToCalendar`を再利用し、ADR-001の同期失敗方針を変更していない。
- `CalendarLinkSummary`を純粋な表示データ変換として切り出し、JVMテスト可能にした点は責務分離として適切である。

### ブロッキング指摘

1. **カテゴリタブがIDで安定キー化されていない。**
   - `TaskListScreen.kt:309-315`は`tabs.forEachIndexed`の位置ベースで`Tab`を構成し、`key(tab.filter)`がない。
   - 仕様の項目2受入条件3と不一致で、削除後に前後タブのslot状態が引き継がれる余地が残る。

2. **ソートの安定タイブレークが仕様どおりでない。**
   - `TaskListScreen.kt:77-88`は優先順位・締切が同じ場合に`createdAt`を比較せず、直ちに`id`で比較する。
   - 仕様§3.2/項目7受入条件6が定める `createdAt` 昇順 → `id` 昇順を両Comparatorに実装し、逆転データのテストを追加する必要がある。

3. **編集導線の48dp保証と明示的なアクセシビリティ操作名がない。**
   - `TaskListScreen.kt:563-569`のメインタスク領域は子要素の実測高依存で、`heightIn(min = 48.dp)`等がない。`clickable`に「タスクを編集する」という操作名もない。
   - `TaskListScreen.kt:675-683`のサブタスクテキスト領域はbodyMediumと上下8dpのみで、48dpを明示保証しない。親RowやCheckboxの高さはText自身のクリック領域にはならない。

### 保守性の注意（非ブロッキング）

- `TaskListScreen.kt`はフィルタ正規化、ソート、一覧表示、複数ダイアログを担う700行超となった。Fowlerの **Divergent Change** に近いというjudgement callである。今回は大規模リファクタは求めないが、サブタスク編集UIや純粋ロジックの小さな分離は検討価値がある。
- `selectedFilter = currentFilter` (`TaskListScreen.kt:250`) はComposition中のstate書き込みである。表示の即時正規化と保存stateの更新を分け、`LaunchedEffect`等で副作用を明示する方が保守しやすい。
- 実装は12ファイルを1コミットで行っており、仕様§7とAGENTS.mdの「1作業単位は最大3ファイル、テスト合格後に1コミット」に不適合。Red実行証跡も記録されていない。履歴改変は必須修正としないが、後追いテストは後追いと正確に記録すること。

## Gate 4: テスト・型・ビルド・セキュリティ・入力値・エラー処理

### 通過を確認した項目

- Claude検証記録により、JVMテスト、debug APKビルド、androidTest Kotlinコンパイルは成功済み。
- スキーマ、Manifest、権限、認証スコープの変更はない。新たな秘密情報、資格情報ログ、非暗号HTTP通信は差分にない。`Authorization`ヘッダー秘匿も変更されていない。
- 空白のサブタスク名と変更なしを早期returnし、ローカル列更新後のCalendar同期失敗は既存の`doSyncToCalendar`/`notifyCalendarFailure`に委ねている。ローカル名変更は同期失敗時も保持される。

### ブロッキング指摘

4. **仕様で禁止された新しい50文字制限をサブタスク名変更に適用している。**
   - `TaskListScreen.kt:100, 709-713`は`TASK_TITLE_MAX_LENGTH = 50`をサブタスク名に適用する。
   - 項目5受入条件10は「既存のサブタスク名には新しい文字数制限を遡及適用しない」としており、50文字超の既存名を保ったまま編集できない。制限とカウンタを除去する必要がある。

5. **カレンダー連携状態の明示が受入条件と不一致である。**
   - `SettingsHubScreen.kt:53-70`は未認可状態のsubtitleを`null`、認可済みを「連携中」とし、「未設定」「未接続」「接続済み」のいずれかを表示するという項目1受入条件4を満たさない。
   - 状態文言を明示し、`CalendarLinkSummaryTest`を受入文言に合わせる必要がある。

6. **仕様で必須とされたUI/DAOの回帰テストがない。**
   - `SettingsScreenTest`/`TaskListScreenTest`/`TaskDaoSubTaskTest`/`TaskListOrderingTest`に相当する追加がない。
   - 現在のJVMテストは純粋関数、Repositoryの委譲、ViewModelのFake更新を確認するが、設定ハブの遷移・戻る操作、タブ削除後のslot状態、48dp、TalkBack semantics、チェックボック/カレンダー/スワイプと編集操作の分離、実Room DAOでの列非干渉を検証しない。
   - `TaskListLogicTest`にも`createdAt`と`id`が逆になる同点データがなく、ソート不備を見逃している。`compileDebugAndroidTestKotlin`の成功は、存在しない受入テストの合格を意味しない。

## 仕様適合軸

- スコープ対象の項目1・2・3・5・7に対応する実装自体は入っており、項目4・6のスコープクリープ、Roomスキーマ変更、認証/API変更はない。
- ただし、指摘1〜6により、仕様軸も **CHANGES REQUIRED** とする。

## 必要な修正と再確認

1. カテゴリTabを`key(tab.filter)`で安定キー化し、削除後のUI回帰テストを追加する。
2. 両ソートに`createdAt`タイブレークを追加し、`createdAt`/`id`の全同点条件をJVMテストで固定する。
3. メイン/サブタスクの編集領域に48dp以上と明示的なTalkBack操作名を与え、操作分離をCompose UIテストする。
4. サブタスクリネームの50文字制限を除去し、長い既存名の編集を回帰テストする。
5. 設定ハブに「未設定」「未接続」「接続済み」を明示し、対応テストを修正する。
6. 仕様§6のCompose/DAOテストを追加し、同じ3 Gradleタスクと`git diff --check` を再実行する。実機未接続なら`connectedDebugAndroidTest`は引き続き保留でよい。
7. 仕様書をブランチの追跡対象に含め、後続担当者が`HEAD`だけで再レビューできるようにする。

## 集計

- Standards/Gate 3.5: blocking 3件、process nonconformance 1件、非ブロッキングのsmell 2件。最大は安定キー・ソート規則・編集操作領域の受入条件不適合。
- Spec/Gate 4: blocking 3件。最大は禁止された文字数制限と、必須のUI/DAO回帰テスト欠落。

---

## 再レビュー（2026-08-27 / 修正コミット `1689f81`）

- 総合判定: **CHANGES REQUIRED**
- Gate 3.5判定: **CHANGES REQUIRED**
- Gate 4判定: **CHANGES REQUIRED**
- 対象: `git diff master...HEAD`（`master` = `4a9aa4a`、`HEAD` = `1689f81`）
- コミット: `13df158`、`1689f81`

### 検証記録

- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin --no-daemon` は、指示に従い `TASK.md` のClaude検証記録 **BUILD SUCCESSFUL（失敗0）** を採用した。
- `connectedDebugAndroidTest` は端末未接続のため未実施だが、ブロッキング理由にしていない。
- 再実行した `git diff --check master...HEAD` は、追跡追加された仕様書 `docs/superpowers/specs/2026-08-27-案件7-ui-feature-feedback-design.md:296` の `new blank line at EOF` 1件により失敗した。

### 7件の再確認

1. **カテゴリタブの安定キー: 解消**
   - `TaskListScreen.kt:315-323` で各 `Tab` を `key(tab.filter)` で包み、カテゴリIDを安定キーとしている。
   - `TaskListScreenTest.kt:38-69` に、選択中カテゴリ削除後のタブ消滅と「すべて」選択のUI回帰テストがある。

2. **ソートの `createdAt` タイブレーク: 解消**
   - `TaskListScreen.kt:81-94` の両Comparatorは、主キー同点後に `createdAt` 昇順 → `id` 昇順を適用する。
   - `TaskListLogicTest.kt:85-186` が両モードの `createdAt` 逆転データと、`createdAt` 同点時の `id` 順を固定している。

3. **48dp / TalkBack操作名: 部分解消、引き続きブロッキング**
   - 48dpは `TaskListScreen.kt:571-580, 686-696` の `heightIn(min = 48.dp)` と `TaskListScreenTest.kt:71-155` で解消している。
   - 一方、実装は操作文を `contentDescription` へ連結しただけで、`clickable(onClickLabel = "タスクを編集する", ...)` 等の明示的な `OnClick` 操作ラベルがない。現テストも `onNodeWithContentDescription` のみで、TalkBackのクリック操作名を検証しない。前回指摘の「明示的なTalkBack操作名」は未解消と判定する。

4. **サブタスク名の50文字制限の遡及適用: 解消**
   - `TaskListScreen.kt:705-735` のリネームUIに文字数制限はなく、trim後の非空・変更ありのみを保存条件にしている。
   - `TaskListScreenTest.kt:216-250`、`TaskDaoSubTaskTest.kt:71-84`、`TaskViewModelCalendarTest.kt:583-617` に50文字超の回帰テストがある。

5. **設定ハブの3状態明示: 解消**
   - `SettingsHubScreen.kt:52-70` は「未設定」「未接続」「接続済み」を明示し、メールアドレスがある場合は併記する。
   - `CalendarLinkSummaryTest.kt:15-57` は3状態とメール無しの接続済みを検証している。

6. **UI/DAO回帰テス不足: 部分解消、引き続きブロッキング**
   - `SettingsScreenTest.kt`、`TaskListScreenTest.kt`、`TaskDaoSubTaskTest.kt` は追加された。設定項目のコールバック・戻る操作・3状態、カテゴリ削除相当のstate更新、48dp、リネームUI、実Room DAOの列非干渉は新たに検証対象となった。
   - しかし、仕様書§6の必須項目である「カレンダー／完了チェック操作が編集を開かないこと」の操作分離テストがない。`TaskListScreenTest.kt:49-56, 82-89, 110-117, 140-147, 169-176, 196-203, 230-237` は完了・カレンダーcallbackをすべてno-opにし、この分離を検証できない。スワイプ削除と編集の分離テストもない。
   - 「並び順の切替で表示順が変わること」に対し、`TaskListScreenTest.kt:187-214` は空リストでFilterChipの選択状態だけを検証し、タスク行の表示順は検証していない。「一覧トップバーの設定導線が1個」の検証もない。
   - `SettingsScreenTest.kt:79-94` の未設定テストは `onNodeWithText("未設定")` の完全一致を使うが、実表示は `SettingsHubScreen.kt:55` の「未設定：SETUP.md ...」である。デフォルトの部分一致無しでは該当ノードを取得できず、実行時に失敗するテストである。

7. **仕様書の未追跡: 解消**
   - `docs/superpowers/specs/2026-08-27-案件7-ui-feature-feedback-design.md` は `1689f81` で追加され、`git ls-files --error-unmatch` と `git cat-file -e HEAD:<path>` の両方が成功する。

### 判定理由と必要な修正

- 7件中5件（1、2、4、5、7）は解消したが、3のTalkBack操作ラベルと6の必須UI回帰テスが未解消のため **CHANGES REQUIRED** とする。
- メイン／サブタスクの `clickable` に明示的な `onClickLabel` を設定し、Semanticsの `OnClick` 操作ラベルを検証するCompose UIテストを追加する。
- カレンダー・メイン完了チェック・サブタスク完了チェック・スワイプ削除が編集を開かないこと、ソート切替で実際の行順が変わること、トップバーの設定導線が1個であることをCompose UIテストで固定する。未設定状態テストのテキストマッチも修正する。
- 仕様書末尾の余分な空行を除去し、`git diff --check master...HEAD` を成功させる。
