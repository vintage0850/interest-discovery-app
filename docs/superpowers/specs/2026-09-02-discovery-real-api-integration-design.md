# 興味発見(Discovery)機能の実データ化 — 設計

## 背景

`shared/src/commonMain/kotlin/.../discovery/` のCompose UIは、これまで一貫して
`FakeDiscoveryRepository`のみを使用しており、実際のバックエンドと接続されたことがない。

一方、本リポジトリの`backend/`には`discovery_router`としてFastAPI実装済みの
実験/仮説ベースのAPIが既に存在し、ポート8000で稼働中である(同バックエンドは
`main.py`上`"Reverse FAQ Backend"`という別名を持つが、discoveryルーターも
併せてマウントされている)。ただしこのAPIはUI側の`DiscoveryRepository`インターフェースが
要求するデータ契約(`HomeData`/`DomainField`/`ReportData`/`MyDataSettings`)を
直接には提供しない。セッション/シグナル/実験/仮説という別のドメインモデルを持つ。

本設計は、まず「実験の生成→選択→開始→完了」という中核ループのみを実データ化する
(過去のユーザー合意: アプローチC→A の C 部分)。ホーム集計・分野カタログ・レポート・
設定は今回スコープ外とし、Fakeの初期値を返す暫定実装のまま残す。

## スコープ

### 対象
- `RealDiscoveryRepository`(新規)による以下のメソッドの実データ化:
  - `getSuggestedExperiments()` — セッションが無ければ作成し、実験候補をGemini生成
  - `selectExperiment(experimentId)`
  - `startExperiment(experimentId)`
  - `completeExperiment(experimentId, enjoyment, curiosity, retryIntent)`
  - `skipExperiment(experimentId)`
  - `cycleNextExperiment()` — 生成済み候補リスト内で次の1件をクライアント側で選ぶ(追加API呼び出しなし)
  - `getExperiment(experimentId)` — ローカルキャッシュ(直近取得した候補リスト)から返す

### 非対象(Fakeのまま残す)
- `getHomeState()`の挨拶文・`todayCompleted`・`completedThisWeek`・`signals`
- `getDomainFields()` / `getDiscovery()` / `getReportData()` / `getSettings()` / `updateSettings()` / `resetAllData()`
- Google ログインとの連携(別サブプロジェクト)

## アーキテクチャ

### 1. インターフェースの整理
`DiscoveryRepository`から`setScenario(scenario: FakeScenario)`と
`getCurrentScenario(): FakeScenario`を削除する。これらはFake実装専用の関心事であり、
Real実装にとって無意味なためインターフェースに漏れている設計上の問題。
`FakeDiscoveryRepository`に直接持たせ、呼び出し元(デバッグ/プレビュー用コード)は
`FakeDiscoveryRepository`型で直接呼ぶよう修正する。

### 2. RealDiscoveryRepository
`shared/src/commonMain/kotlin/.../discovery/RealDiscoveryRepository.kt`を新規作成。

```kotlin
class RealDiscoveryRepository(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val sessionStore: DiscoverySessionStore, // session_idの永続化を抽象化
) : DiscoveryRepository { ... }
```

- `baseUrl`はハードコードのLAN IP(例: `http://192.168.x.x:8000`)を呼び出し元(DI箇所)で注入する。
- `sessionStore`はexpect/actualで各プラットフォームの永続化(Android: SharedPreferences等)を吸収する薄いインターフェース。`getSessionId(): Int?` / `saveSessionId(id: Int)`のみ持つ。

### 3. セッション作成
初回の`getSuggestedExperiments()`呼び出し時、`sessionStore`にセッションIDが無ければ
`POST /sessions`(`student_label = "test_user"`固定)でセッションを作成し保存する。
以降はこのセッションIDを使い回す。

### 4. 実験候補の生成とキャッシュ
`POST /sessions/{id}/experiments/generate`(`n_candidates = 3`)を呼び、
返却された`ExperimentResponse`のリストをメモリ上に保持する(`getExperiment`/
`cycleNextExperiment`はこのキャッシュを参照)。既に候補を生成済みの場合は
再生成せずキャッシュを返す(呼び出しごとにGemini課金・新規実験が積み上がるのを防ぐ)。

### 5. データマッピング
`ExperimentResponse` → Kotlin `Experiment`:

| backend (ExperimentResponse) | Kotlin (Experiment) | 変換 |
|---|---|---|
| `id: Int` | `id: String` | `.toString()` |
| `title` | `title` | そのまま |
| `description` | `description` | そのまま |
| `planned_minutes` | `plannedMinutes` | そのまま |
| `domain: DomainType` | `domainFieldId: String` | `domain.value`をそのまま文字列として使用 |
| `domain: DomainType` | `actionType: BehaviorSignal` | 下記固定対応表 |
| (なし) | `reason` / `testedHypothesis` | 空文字のまま(バックエンドに該当データなし) |

固定対応表(`DomainType` → `BehaviorSignal`):

```
tech      -> ANALYZE
art       -> CREATE
music     -> CREATE
sports    -> IMPROVE
science   -> INVESTIGATE
social    -> EXPLAIN
making    -> CREATE
nature    -> INVESTIGATE
business  -> ORGANIZE
other     -> ANALYZE
```

### 6. completeExperimentのconfidence補完
UIの`completeExperiment(experimentId, enjoyment, curiosity, retryIntent)`には
バックエンドが要求する`confidence: Float(0.0-1.0)`が無い。
`confidence = (enjoyment + curiosity + retryIntent) / 15.0f`で自動算出して送信する
(3項目とも1-5点、合計最大15点を0.0-1.0に正規化)。

### 7. エラーハンドリング
Ktorの通信例外・4xx/5xxは全て呼び出し元に伝播させる(例外をそのままthrow)。
UI側(Compose画面)で表示中のエラー処理パターンに合わせる形で、
呼び出し元のViewModel相当層で捕捉してエラー状態に落とす(既存のUI側エラー表示の
仕組みを踏襲し、新規のエラーUIは作らない)。

## テスト方針(TDD)

`shared/src/commonTest/kotlin/.../discovery/RealDiscoveryRepositoryTest.kt`を新規作成。
KtorのMockEngineを使い、以下をRed→Greenで検証する:

- `getSuggestedExperiments()`: セッション未作成時に`POST /sessions`→`POST /sessions/{id}/experiments/generate`の順にリクエストが飛ぶこと。2回目の呼び出しではセッション作成が省略されること。
- `selectExperiment` / `startExperiment` / `skipExperiment`: それぞれ正しいパスへPOSTされること。
- `completeExperiment`: リクエストボディの`confidence`が正しい正規化式で計算されていること。
- `cycleNextExperiment`: 追加のHTTPリクエストが発生せず、キャッシュ内の次の候補を返すこと。
- エラー系: バックエンドが4xx/5xxを返した場合に例外が伝播すること。

## 影響範囲

- `shared/src/commonMain/kotlin/.../discovery/DiscoveryRepository.kt`(インターフェース変更)
- `shared/src/commonMain/kotlin/.../discovery/FakeDiscoveryRepository.kt`(scenario系メソッド移動)
- `shared/src/commonMain/kotlin/.../discovery/RealDiscoveryRepository.kt`(新規)
- `shared/src/commonMain/kotlin/.../discovery/DiscoverySessionStore.kt`(新規、expect)
- 各プラットフォームの`actual DiscoverySessionStore`(androidMain等、新規)
- `shared/src/commonMain/kotlin/.../ui/App.kt`(DI箇所、Fake→Real差し替え)
- `shared/src/commonTest/kotlin/.../discovery/RealDiscoveryRepositoryTest.kt`(新規)

## 非目標

- ホーム/レポート/設定APIの追加(次フェーズ=アプローチA)
- Google ログインとの統合
- Gemini APIキー・モデル名(`gemini-2.5-flash`)自体の妥当性検証
