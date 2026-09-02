# 興味発見(Discovery)機能 実データ化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `FakeDiscoveryRepository`のみで動いていた興味発見(Discovery)機能の中核ループ(実験の生成→選択→開始→完了→スキップ)を、既存の`backend/discovery` FastAPI実サービスに実データで接続する。

**Architecture:** `shared/src/commonMain/.../discovery/`に`RealDiscoveryRepository`を新規追加し、既存の`DiscoveryRepository`インターフェースを実装する。Ktor `HttpClient`でバックエンドを呼び出す実装パターンは、同リポジトリ内の`ReverseFaqApiClient.kt`(同じバックエンドの別エンドポイントを呼ぶ既存実装)を踏襲する。ホーム/レポート/設定など未対応のAPIはFakeの初期値相当の固定値を返す暫定実装として残す。

**Tech Stack:** Kotlin Multiplatform, Ktor Client 3.1.0 (`ContentNegotiation` + `kotlinx.serialization.json`, `JsonNamingStrategy.SnakeCase`), kotlin.test + kotlinx-coroutines-test、新規で ktor-client-mock (テスト用MockEngine)。

**Spec:** `docs/superpowers/specs/2026-09-02-discovery-real-api-integration-design.md`

## Global Constraints

- バックエンドのベースURLは開発中ハードコードのLAN IPを使う(設定画面は作らない)。
- セッション作成の`student_label`は固定文字列`"test_user"`を使う。
- JSONの命名規則は`JsonNamingStrategy.SnakeCase`を使い、Kotlin側は常にcamelCaseで書く(プロジェクト全体のNAMING_CONVENTIONS.mdに準拠)。
- `DomainType`→`BehaviorSignal`の対応表は固定: `tech→ANALYZE, art→CREATE, music→CREATE, sports→IMPROVE, science→INVESTIGATE, social→EXPLAIN, making→CREATE, nature→INVESTIGATE, business→ORGANIZE, other→ANALYZE`。
- `completeExperiment`の`confidence`は`(enjoyment + curiosity + retryIntent) / 15.0f`で自動算出する。
- ホーム/分野一覧/レポート/設定APIの追加、Googleログインとの統合は本計画の非目標(次フェーズ)。
- 本計画の範囲では、セッションIDはアプリプロセスのメモリ上にのみ保持する(アプリ再起動で失われる。永続化は非目標)。設計書の`DiscoverySessionStore`案はYAGNIのため採用せず、この計画ではメモリ内変数に簡略化する。

---

## Task 1: DiscoveryRepositoryインターフェースからFake専用メソッドを分離する

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/myapplication/shared/discovery/DiscoveryRepository.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/myapplication/shared/discovery/FakeDiscoveryRepository.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/myapplication/shared/discovery/DiscoveryState.kt`

**Interfaces:**
- Produces: `DiscoveryRepository`インターフェースは`setScenario`/`getCurrentScenario`を持たなくなる。これらは`FakeDiscoveryRepository`のpublicメソッド(`override`を外した通常メソッド)としてのみ存在する。

この変更は既存の`FakeDiscoveryRepositoryTest.kt` / `DiscoveryStateTest.kt`が引き続き通ることで検証する(新規テストは不要な純粋リファクタリング)。

- [ ] **Step 1: 既存テストがグリーンであることを確認する(ベースライン)**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.example.myapplication.shared.discovery.*"`
Expected: BUILD SUCCESSFUL(変更前の全テストが合格)

- [ ] **Step 2: `DiscoveryRepository.kt`から2メソッドを削除する**

`shared/src/commonMain/kotlin/com/example/myapplication/shared/discovery/DiscoveryRepository.kt`の以下2行を削除する:

```kotlin
    fun setScenario(scenario: FakeScenario)
    fun getCurrentScenario(): FakeScenario
```

- [ ] **Step 3: `FakeDiscoveryRepository.kt`の該当メソッドから`override`を外す**

`shared/src/commonMain/kotlin/com/example/myapplication/shared/discovery/FakeDiscoveryRepository.kt`の該当箇所を変更する:

変更前:
```kotlin
    override fun setScenario(scenario: FakeScenario) {
```
```kotlin
    override fun getCurrentScenario(): FakeScenario = scenario
```

変更後:
```kotlin
    fun setScenario(scenario: FakeScenario) {
```
```kotlin
    fun getCurrentScenario(): FakeScenario = scenario
```

- [ ] **Step 4: `DiscoveryState.kt`の呼び出し箇所を安全キャストに変更する**

`shared/src/commonMain/kotlin/com/example/myapplication/shared/discovery/DiscoveryState.kt:171`付近、変更前:

```kotlin
                        activeScenario = repository.getCurrentScenario()
```

変更後:

```kotlin
                        activeScenario = (repository as? FakeDiscoveryRepository)
                            ?.getCurrentScenario()
                            ?: it.activeScenario
```

同ファイル`changeScenario`関数(179行付近)、変更前:

```kotlin
    fun changeScenario(scenario: FakeScenario) {
        repository.setScenario(scenario)
        loadHomeData()
        loadDiscovery()
        loadReportData()
    }
```

変更後:

```kotlin
    fun changeScenario(scenario: FakeScenario) {
        (repository as? FakeDiscoveryRepository)?.setScenario(scenario)
        loadHomeData()
        loadDiscovery()
        loadReportData()
    }
```

- [ ] **Step 5: コンパイルとテストを再実行して確認する**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.example.myapplication.shared.discovery.*"`
Expected: BUILD SUCCESSFUL(Step 1と同じ件数が合格)

- [ ] **Step 6: コミット**

```bash
git add shared/src/commonMain/kotlin/com/example/myapplication/shared/discovery/DiscoveryRepository.kt shared/src/commonMain/kotlin/com/example/myapplication/shared/discovery/FakeDiscoveryRepository.kt shared/src/commonMain/kotlin/com/example/myapplication/shared/discovery/DiscoveryState.kt
git commit -m "refactor(discovery): move Fake-only scenario methods out of DiscoveryRepository interface"
```

---

## Task 2: ktor-client-mock依存関係の追加 + RealDiscoveryRepositoryの骨格とセッション作成/実験生成

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `shared/build.gradle.kts`
- Create: `shared/src/commonMain/kotlin/com/example/myapplication/shared/discovery/RealDiscoveryRepository.kt`
- Create: `shared/src/commonTest/kotlin/com/example/myapplication/shared/discovery/RealDiscoveryRepositoryTest.kt`

**Interfaces:**
- Consumes: `DiscoveryRepository`インターフェース(Task 1で整理済み。`setScenario`/`getCurrentScenario`は含まれない)。`Experiment`データクラス(`shared/.../discovery/DiscoveryModels.kt`、フィールド: `id: String, title: String, description: String, plannedMinutes: Int, actionType: BehaviorSignal, reason: String = "", testedHypothesis: String = "", domainFieldId: String = "design_ui"`)。`BehaviorSignal`enum。
- Produces: `class RealDiscoveryRepository(baseUrl: String = DEFAULT_BASE_URL, httpClient: HttpClient? = null, studentLabel: String = "test_user") : DiscoveryRepository`。コンパニオン定数`RealDiscoveryRepository.DEFAULT_BASE_URL: String`。以降のTaskはこのクラスに`override`メソッドを追加していく。

- [ ] **Step 1: `gradle/libs.versions.toml`に`ktor-client-mock`を追加する**

`gradle/libs.versions.toml`の`ktor-client-darwin`定義の直後に1行追加する:

```toml
ktor-client-mock = { group = "io.ktor", name = "ktor-client-mock", version.ref = "ktor" }
```

- [ ] **Step 2: `shared/build.gradle.kts`の`commonTest.dependencies`に追加する**

`shared/build.gradle.kts`の該当ブロック、変更前:

```kotlin
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
```

変更後:

```kotlin
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
```

- [ ] **Step 3: 失敗するテストを書く**

`shared/src/commonTest/kotlin/com/example/myapplication/shared/discovery/RealDiscoveryRepositoryTest.kt`を新規作成する:

```kotlin
package com.example.myapplication.shared.discovery

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlin.test.Test
import kotlin.test.assertEquals

private val SESSION_BODY = """
    {"id": 1, "student_label": "test_user", "status": "active",
     "created_at": "2026-09-02T00:00:00+00:00", "updated_at": "2026-09-02T00:00:00+00:00"}
""".trimIndent()

private fun experimentsBody(id: Int = 10, domain: String = "tech") = """
    [{"id": $id, "session_id": 1, "title": "t", "description": "d",
      "domain": "$domain", "planned_minutes": 5, "status": "generated",
      "selected_at": null, "started_at": null, "completed_at": null,
      "skipped_at": null, "selection_note": null, "skip_reason": null,
      "actual_minutes": null, "created_at": "2026-09-02T00:00:00+00:00"}]
""".trimIndent()

private fun mockClient(handler: (path: String) -> Pair<HttpStatusCode, String>): Pair<HttpClient, MutableList<String>> {
    val requestedPaths = mutableListOf<String>()
    val engine = MockEngine { request ->
        val path = request.url.encodedPath
        requestedPaths.add(path)
        val (status, body) = handler(path)
        respond(
            content = body,
            status = status,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
    }
    val client = HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                namingStrategy = JsonNamingStrategy.SnakeCase
            })
        }
    }
    return client to requestedPaths
}

class RealDiscoveryRepositoryTest {

    @Test
    fun getSuggestedExperiments_createsSessionThenGeneratesExperiments() = runTest {
        val (client, paths) = mockClient { path ->
            when (path) {
                "/sessions" -> HttpStatusCode.Created to SESSION_BODY
                "/sessions/1/experiments/generate" -> HttpStatusCode.Created to experimentsBody()
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        val experiments = repo.getSuggestedExperiments()

        assertEquals(listOf("/sessions", "/sessions/1/experiments/generate"), paths)
        assertEquals(1, experiments.size)
        assertEquals("10", experiments.first().id)
        assertEquals(BehaviorSignal.ANALYZE, experiments.first().actionType)
    }

    @Test
    fun getSuggestedExperiments_secondCallReusesSessionAndCache() = runTest {
        val (client, paths) = mockClient { path ->
            when (path) {
                "/sessions" -> HttpStatusCode.Created to SESSION_BODY
                "/sessions/1/experiments/generate" -> HttpStatusCode.Created to experimentsBody()
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        repo.getSuggestedExperiments()
        repo.getSuggestedExperiments()

        assertEquals(listOf("/sessions", "/sessions/1/experiments/generate"), paths)
    }
}
```

- [ ] **Step 4: テストを実行し、コンパイルエラー(`RealDiscoveryRepository`未定義)で失敗することを確認する**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.example.myapplication.shared.discovery.RealDiscoveryRepositoryTest"`
Expected: コンパイルエラーで失敗(`Unresolved reference: RealDiscoveryRepository`)

- [ ] **Step 5: `RealDiscoveryRepository`の骨格を実装する**

`shared/src/commonMain/kotlin/com/example/myapplication/shared/discovery/RealDiscoveryRepository.kt`を新規作成する(このTaskではインターフェースの未実装メソッドは仮実装として`TODO()`を置かず、Task 3〜6で順次埋める前提のため、まず`getSuggestedExperiments`とセッション作成のみ実装し、他のメソッドは一時的に最小限のダミー実装を入れて全体をコンパイル可能にする):

```kotlin
package com.example.myapplication.shared.discovery

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

/**
 * backend/discovery（Ktor Client 経由）に接続する Discovery 機能のリポジトリ実装。
 *
 * @param baseUrl バックエンドのベース URL。実機では開発機の LAN 内 IP（例: http://192.168.x.x:8000）を指定する。
 * @param httpClient テスト用に差し替え可能な [HttpClient]。省略時は共通設定で生成する。
 */
class RealDiscoveryRepository(
    baseUrl: String = DEFAULT_BASE_URL,
    httpClient: HttpClient? = null,
    private val studentLabel: String = "test_user"
) : DiscoveryRepository {

    private val client = httpClient ?: defaultDiscoveryHttpClient(baseUrl)

    private var sessionId: Int? = null
    private var cachedExperiments: List<Experiment> = emptyList()
    private var cycleIndex: Int = 0

    private val domainToBehaviorSignal = mapOf(
        "tech" to BehaviorSignal.ANALYZE,
        "art" to BehaviorSignal.CREATE,
        "music" to BehaviorSignal.CREATE,
        "sports" to BehaviorSignal.IMPROVE,
        "science" to BehaviorSignal.INVESTIGATE,
        "social" to BehaviorSignal.EXPLAIN,
        "making" to BehaviorSignal.CREATE,
        "nature" to BehaviorSignal.INVESTIGATE,
        "business" to BehaviorSignal.ORGANIZE,
        "other" to BehaviorSignal.ANALYZE
    )

    private fun ExperimentResponseDto.toExperiment(): Experiment = Experiment(
        id = id.toString(),
        title = title,
        description = description,
        plannedMinutes = plannedMinutes,
        actionType = domainToBehaviorSignal[domain] ?: BehaviorSignal.ANALYZE,
        domainFieldId = domain
    )

    private suspend fun ensureSession(): Int {
        sessionId?.let { return it }
        val response = client.post("/sessions") {
            contentType(ContentType.Application.Json)
            setBody(SessionCreateRequest(studentLabel = studentLabel))
        }
        if (!response.status.isSuccess()) {
            throw DiscoveryApiException("セッション作成に失敗しました (HTTP ${response.status.value})")
        }
        val created = response.body<SessionResponseDto>()
        sessionId = created.id
        return created.id
    }

    override suspend fun getSuggestedExperiments(): List<Experiment> {
        if (cachedExperiments.isNotEmpty()) return cachedExperiments
        val id = ensureSession()
        val response = client.post("/sessions/$id/experiments/generate") {
            contentType(ContentType.Application.Json)
            setBody(ExperimentGenerateRequest())
        }
        if (!response.status.isSuccess()) {
            throw DiscoveryApiException("実験候補の生成に失敗しました (HTTP ${response.status.value})")
        }
        val generated = response.body<List<ExperimentResponseDto>>()
        cachedExperiments = generated.map { it.toExperiment() }
        return cachedExperiments
    }

    // 以下は Task 3〜6 で実装する。現時点ではコンパイルを通すための最小実装。
    override suspend fun getHomeState(): HomeData = HomeData()
    override suspend fun getExperiment(experimentId: String): Experiment =
        cachedExperiments.first { it.id == experimentId }
    override suspend fun selectExperiment(experimentId: String) {}
    override suspend fun cycleNextExperiment(): Experiment = cachedExperiments.first()
    override suspend fun startExperiment(experimentId: String) {}
    override suspend fun completeExperiment(
        experimentId: String,
        enjoyment: Int,
        curiosity: Int,
        retryIntent: Int
    ) {}
    override suspend fun skipExperiment(experimentId: String) {}
    override suspend fun getDiscovery(): DiscoveryData = DiscoveryData(observation = "", hypothesis = "")
    override suspend fun getNextExperiment(): Experiment = cachedExperiments.first()
    override suspend fun getDomainFields(): List<DomainField> = emptyList()
    override suspend fun getReportData(): ReportData = ReportData()
    override suspend fun getSettings(): MyDataSettings = MyDataSettings()
    override suspend fun updateSettings(settings: MyDataSettings) {}
    override suspend fun resetAllData() {
        sessionId = null
        cachedExperiments = emptyList()
        cycleIndex = 0
    }

    companion object {
        /** エミュレータから開発機 localhost を参照するための標準 URL。実機では呼び出し元でLAN IPを渡す。 */
        const val DEFAULT_BASE_URL = "http://10.0.2.2:8000"
    }
}

/** backend/discovery API 呼び出し時のエラー。 */
class DiscoveryApiException(message: String) : Exception(message)

@Serializable
private data class SessionCreateRequest(val studentLabel: String)

@Serializable
private data class SessionResponseDto(val id: Int)

@Serializable
private data class ExperimentGenerateRequest(val nCandidates: Int = 3)

@Serializable
private data class ExperimentResponseDto(
    val id: Int,
    val title: String,
    val description: String,
    val domain: String,
    val plannedMinutes: Int
)

private fun defaultDiscoveryHttpClient(baseUrl: String): HttpClient {
    return HttpClient {
        defaultRequest {
            url(baseUrl)
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                namingStrategy = JsonNamingStrategy.SnakeCase
            })
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.ALL
        }
    }
}
```

- [ ] **Step 6: テストを実行してグリーンになることを確認する**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.example.myapplication.shared.discovery.RealDiscoveryRepositoryTest"`
Expected: BUILD SUCCESSFUL(2 tests passed)

- [ ] **Step 7: コミット**

```bash
git add gradle/libs.versions.toml shared/build.gradle.kts shared/src/commonMain/kotlin/com/example/myapplication/shared/discovery/RealDiscoveryRepository.kt shared/src/commonTest/kotlin/com/example/myapplication/shared/discovery/RealDiscoveryRepositoryTest.kt
git commit -m "feat(discovery): add RealDiscoveryRepository with session creation and experiment generation"
```

---

## Task 3: selectExperiment / startExperiment / skipExperiment の実データ化

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/myapplication/shared/discovery/RealDiscoveryRepository.kt`
- Modify: `shared/src/commonTest/kotlin/com/example/myapplication/shared/discovery/RealDiscoveryRepositoryTest.kt`

**Interfaces:**
- Consumes: Task 2で作った`mockClient`ヘルパー、`SESSION_BODY`/`experimentsBody()`、`RealDiscoveryRepository(httpClient = client)`。
- Produces: `selectExperiment`/`startExperiment`/`skipExperiment`がそれぞれ`POST /experiments/{id}/select`・`/start`・`/skip`を呼ぶようになる。

- [ ] **Step 1: 失敗するテストを追加する**

`RealDiscoveryRepositoryTest.kt`のクラス内に以下のテストを追加する:

```kotlin
    @Test
    fun selectExperiment_postsToSelectEndpoint() = runTest {
        val (client, paths) = mockClient { path ->
            when (path) {
                "/experiments/10/select" -> HttpStatusCode.OK to experimentsBody().removeSurrounding("[", "]")
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        repo.selectExperiment("10")

        assertEquals(listOf("/experiments/10/select"), paths)
    }

    @Test
    fun startExperiment_postsToStartEndpoint() = runTest {
        val (client, paths) = mockClient { path ->
            when (path) {
                "/experiments/10/start" -> HttpStatusCode.OK to experimentsBody().removeSurrounding("[", "]")
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        repo.startExperiment("10")

        assertEquals(listOf("/experiments/10/start"), paths)
    }

    @Test
    fun skipExperiment_postsToSkipEndpoint() = runTest {
        val (client, paths) = mockClient { path ->
            when (path) {
                "/experiments/10/skip" -> HttpStatusCode.OK to experimentsBody().removeSurrounding("[", "]")
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        repo.skipExperiment("10")

        assertEquals(listOf("/experiments/10/skip"), paths)
    }
```

- [ ] **Step 2: テストを実行し失敗することを確認する**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.example.myapplication.shared.discovery.RealDiscoveryRepositoryTest"`
Expected: 上記3件がFAIL(現状の仮実装はHTTPリクエストを送らないため`paths`が空)

- [ ] **Step 3: `RealDiscoveryRepository.kt`の該当メソッドを実装する**

Task 2で入れた仮実装を置き換える:

```kotlin
    override suspend fun selectExperiment(experimentId: String) {
        val response = client.post("/experiments/$experimentId/select") {
            contentType(ContentType.Application.Json)
            setBody(ExperimentSelectRequest(selectionNote = DEFAULT_SELECTION_NOTE))
        }
        if (!response.status.isSuccess()) {
            throw DiscoveryApiException("実験の選択に失敗しました (HTTP ${response.status.value})")
        }
    }

    override suspend fun startExperiment(experimentId: String) {
        val response = client.post("/experiments/$experimentId/start")
        if (!response.status.isSuccess()) {
            throw DiscoveryApiException("実験の開始に失敗しました (HTTP ${response.status.value})")
        }
    }

    override suspend fun skipExperiment(experimentId: String) {
        val response = client.post("/experiments/$experimentId/skip") {
            contentType(ContentType.Application.Json)
            setBody(ExperimentSkipRequest())
        }
        if (!response.status.isSuccess()) {
            throw DiscoveryApiException("実験のスキップに失敗しました (HTTP ${response.status.value})")
        }
    }
```

`companion object`の直前(private data class群)に以下を追加する:

```kotlin
private const val DEFAULT_SELECTION_NOTE = "アプリから選択"

@Serializable
private data class ExperimentSelectRequest(val selectionNote: String)

@Serializable
private data class ExperimentSkipRequest(val reason: String? = null)
```

- [ ] **Step 4: テストを実行してグリーンになることを確認する**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.example.myapplication.shared.discovery.RealDiscoveryRepositoryTest"`
Expected: BUILD SUCCESSFUL(全テスト合格、5 tests)

- [ ] **Step 5: コミット**

```bash
git add shared/src/commonMain/kotlin/com/example/myapplication/shared/discovery/RealDiscoveryRepository.kt shared/src/commonTest/kotlin/com/example/myapplication/shared/discovery/RealDiscoveryRepositoryTest.kt
git commit -m "feat(discovery): wire select/start/skip experiment to backend endpoints"
```

---

## Task 4: completeExperimentのconfidence自動算出付き実データ化

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/myapplication/shared/discovery/RealDiscoveryRepository.kt`
- Modify: `shared/src/commonTest/kotlin/com/example/myapplication/shared/discovery/RealDiscoveryRepositoryTest.kt`

**Interfaces:**
- Produces: `completeExperiment(experimentId, enjoyment, curiosity, retryIntent)`が`POST /experiments/{id}/complete`に`confidence = (enjoyment+curiosity+retryIntent)/15.0f`を含めて送信する。

- [ ] **Step 1: 失敗するテストを追加する**

`RealDiscoveryRepositoryTest.kt`に、送信された`confidence`の値を検証するテストを追加する。リクエストボディを検証するため、`mockClient`とは別に本文を捕捉するヘルパーを使う:

```kotlin
    @Test
    fun completeExperiment_computesConfidenceFromThreeScores() = runTest {
        var capturedBody = ""
        val engine = MockEngine { request ->
            capturedBody = (request.body as io.ktor.client.request.forms.TextContent).text
            respond(
                content = """
                    {"id": 1, "experiment_id": 10, "enjoyment": 5, "curiosity": 4,
                     "retry_intent": 3, "confidence": 0.8, "reflection": null,
                     "created_at": "2026-09-02T00:00:00+00:00"}
                """.trimIndent(),
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    namingStrategy = JsonNamingStrategy.SnakeCase
                })
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        repo.completeExperiment(experimentId = "10", enjoyment = 5, curiosity = 4, retryIntent = 3)

        assertEquals(true, capturedBody.contains("\"confidence\":0.8"))
    }
```

- [ ] **Step 2: テストを実行し失敗することを確認する**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.example.myapplication.shared.discovery.RealDiscoveryRepositoryTest"`
Expected: `completeExperiment_computesConfidenceFromThreeScores`がFAIL(仮実装はHTTPリクエストを送らない)

- [ ] **Step 3: `completeExperiment`を実装する**

Task 2の仮実装を置き換える:

```kotlin
    override suspend fun completeExperiment(
        experimentId: String,
        enjoyment: Int,
        curiosity: Int,
        retryIntent: Int
    ) {
        val confidence = (enjoyment + curiosity + retryIntent) / 15.0f
        val response = client.post("/experiments/$experimentId/complete") {
            contentType(ContentType.Application.Json)
            setBody(
                ExperimentResultRequest(
                    enjoyment = enjoyment,
                    curiosity = curiosity,
                    retryIntent = retryIntent,
                    confidence = confidence
                )
            )
        }
        if (!response.status.isSuccess()) {
            throw DiscoveryApiException("実験の完了報告に失敗しました (HTTP ${response.status.value})")
        }
    }
```

private data class群に追加する:

```kotlin
@Serializable
private data class ExperimentResultRequest(
    val enjoyment: Int,
    val curiosity: Int,
    val retryIntent: Int,
    val confidence: Float,
    val reflection: String? = null
)
```

- [ ] **Step 4: テストを実行してグリーンになることを確認する**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.example.myapplication.shared.discovery.RealDiscoveryRepositoryTest"`
Expected: BUILD SUCCESSFUL(全テスト合格、6 tests)

- [ ] **Step 5: コミット**

```bash
git add shared/src/commonMain/kotlin/com/example/myapplication/shared/discovery/RealDiscoveryRepository.kt shared/src/commonTest/kotlin/com/example/myapplication/shared/discovery/RealDiscoveryRepositoryTest.kt
git commit -m "feat(discovery): complete experiment with auto-computed confidence"
```

---

## Task 5: cycleNextExperiment / getNextExperiment / getExperiment をキャッシュから返す

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/myapplication/shared/discovery/RealDiscoveryRepository.kt`
- Modify: `shared/src/commonTest/kotlin/com/example/myapplication/shared/discovery/RealDiscoveryRepositoryTest.kt`

**Interfaces:**
- Produces: 3メソッドとも追加のHTTPリクエストを発生させず、`cachedExperiments`(Task 2で導入済み)とインデックスを使ってメモリ上で解決する。

- [ ] **Step 1: 失敗するテストを追加する**

```kotlin
    @Test
    fun cycleNextExperiment_advancesWithoutExtraHttpCall() = runTest {
        val (client, paths) = mockClient { path ->
            when (path) {
                "/sessions" -> HttpStatusCode.Created to SESSION_BODY
                "/sessions/1/experiments/generate" -> HttpStatusCode.Created to twoExperimentsBody()
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)
        repo.getSuggestedExperiments()
        paths.clear()

        val next = repo.cycleNextExperiment()

        assertEquals(emptyList<String>(), paths)
        assertEquals("11", next.id)
    }

    @Test
    fun getExperiment_returnsFromCache() = runTest {
        val (client, _) = mockClient { path ->
            when (path) {
                "/sessions" -> HttpStatusCode.Created to SESSION_BODY
                "/sessions/1/experiments/generate" -> HttpStatusCode.Created to twoExperimentsBody()
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)
        repo.getSuggestedExperiments()

        val experiment = repo.getExperiment("11")

        assertEquals("11", experiment.id)
    }
```

同ファイルのトップレベル(既存の`experimentsBody`の直後)に、2件返すヘルパーを追加する:

```kotlin
private fun twoExperimentsBody() = """
    [{"id": 10, "session_id": 1, "title": "t1", "description": "d1",
      "domain": "tech", "planned_minutes": 5, "status": "generated",
      "selected_at": null, "started_at": null, "completed_at": null,
      "skipped_at": null, "selection_note": null, "skip_reason": null,
      "actual_minutes": null, "created_at": "2026-09-02T00:00:00+00:00"},
     {"id": 11, "session_id": 1, "title": "t2", "description": "d2",
      "domain": "art", "planned_minutes": 10, "status": "generated",
      "selected_at": null, "started_at": null, "completed_at": null,
      "skipped_at": null, "selection_note": null, "skip_reason": null,
      "actual_minutes": null, "created_at": "2026-09-02T00:00:00+00:00"}]
""".trimIndent()
```

- [ ] **Step 2: テストを実行し失敗することを確認する**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.example.myapplication.shared.discovery.RealDiscoveryRepositoryTest"`
Expected: `cycleNextExperiment_advancesWithoutExtraHttpCall`と`getExperiment_returnsFromCache`がFAIL

- [ ] **Step 3: 3メソッドを実装する**

Task 2の仮実装を置き換える:

```kotlin
    override suspend fun getExperiment(experimentId: String): Experiment {
        if (cachedExperiments.isEmpty()) getSuggestedExperiments()
        return cachedExperiments.firstOrNull { it.id == experimentId }
            ?: throw DiscoveryApiException("実験が見つかりません: $experimentId")
    }

    override suspend fun cycleNextExperiment(): Experiment {
        if (cachedExperiments.isEmpty()) getSuggestedExperiments()
        if (cachedExperiments.isEmpty()) {
            throw DiscoveryApiException("表示できる実験がありません")
        }
        cycleIndex = (cycleIndex + 1) % cachedExperiments.size
        return cachedExperiments[cycleIndex]
    }

    override suspend fun getNextExperiment(): Experiment {
        if (cachedExperiments.isEmpty()) getSuggestedExperiments()
        if (cachedExperiments.isEmpty()) {
            throw DiscoveryApiException("表示できる実験がありません")
        }
        val nextIndex = (cycleIndex + 1) % cachedExperiments.size
        return cachedExperiments[nextIndex]
    }
```

- [ ] **Step 4: テストを実行してグリーンになることを確認する**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.example.myapplication.shared.discovery.RealDiscoveryRepositoryTest"`
Expected: BUILD SUCCESSFUL(全テスト合格、8 tests)

- [ ] **Step 5: コミット**

```bash
git add shared/src/commonMain/kotlin/com/example/myapplication/shared/discovery/RealDiscoveryRepository.kt shared/src/commonTest/kotlin/com/example/myapplication/shared/discovery/RealDiscoveryRepositoryTest.kt
git commit -m "feat(discovery): resolve cycleNextExperiment/getNextExperiment/getExperiment from cache"
```

---

## Task 6: App.ktでFakeからRealへ切り替え、実機で動作確認する

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/myapplication/shared/ui/App.kt`

**Interfaces:**
- Consumes: `RealDiscoveryRepository(baseUrl = ...)`(Task 2〜5で完成済み)。

- [ ] **Step 1: `App.kt`のDI箇所を変更する**

`shared/src/commonMain/kotlin/com/example/myapplication/shared/ui/App.kt`のimport、変更前:

```kotlin
import com.example.myapplication.shared.discovery.FakeDiscoveryRepository
```

変更後:

```kotlin
import com.example.myapplication.shared.discovery.RealDiscoveryRepository
```

同ファイルの生成箇所(41行目付近)、変更前:

```kotlin
        val discoveryState = remember {
            DiscoveryState(FakeDiscoveryRepository(), scope)
        }
```

変更後(LAN IPは開発機の実際のアドレスに置き換える。実行前に開発機で`ipconfig`を実行し、Wi-Fiアダプタの IPv4 アドレスを確認すること):

```kotlin
        val discoveryState = remember {
            DiscoveryState(
                RealDiscoveryRepository(baseUrl = "http://192.168.1.100:8000"),
                scope
            )
        }
```

- [ ] **Step 2: ビルドを確認する**

Run: `./gradlew :shared:assembleDebug :shared:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: バックエンドを起動する**

Run(別ターミナルで): `cd backend && uvicorn main:app --reload --host 0.0.0.0 --port 8000`
Expected: `Uvicorn running on http://0.0.0.0:8000`。同一Wi-Fi内の実機からアクセスできることを確認する(開発機のファイアウォールでポート8000がブロックされていないこと)。

- [ ] **Step 4: 実機でアプリを起動し、発見(Discover)タブで動作確認する**

Run: `./gradlew :app:installDebug`
実機でアプリを起動し、ホーム→発見タブへ遷移。実験候補が表示され(バックエンドの`GEMINI_API_KEY`は設定済みのためGemini生成の実データが返る)、1件を選択→開始→タイマー→振り返り(1〜5点×3問)→完了まで一連の操作が例外なく完了することを確認する。バックエンドのログ(uvicornのコンソール出力)に`POST /sessions`・`POST /sessions/1/experiments/generate`・`POST /experiments/{id}/select`等のリクエストが記録されていることも確認する。

- [ ] **Step 5: コミット**

```bash
git add shared/src/commonMain/kotlin/com/example/myapplication/shared/ui/App.kt
git commit -m "feat(discovery): switch app DI from FakeDiscoveryRepository to RealDiscoveryRepository"
```

---

## Self-Review Notes

- **spec coverage:** スコープ対象6メソッド(getSuggestedExperiments/selectExperiment/startExperiment/completeExperiment/skipExperiment/cycleNextExperiment/getExperiment)は全てTask 2〜5でカバー。インターフェース整理(spec②)はTask 1。データマッピング表(spec⑤)とconfidence算出式(spec⑥)はTask 2・4に反映。App.ktへの配線(spec④のフロー)はTask 6。
- **非目標:** ホーム/レポート/設定/分野一覧はTask 2で固定値実装のみ行い、追加のテストは書かない(spec通り非目標)。
- **設計からの変更点:** spec記載の`DiscoverySessionStore`(expect/actual永続化)は、本計画ではメモリ内変数に簡略化した(Global Constraints参照)。永続化が必要になった場合は別タスクとして起票する。
