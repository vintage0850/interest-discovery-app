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
    override suspend fun getExperiment(experimentId: String): Experiment {
        if (cachedExperiments.isEmpty()) getSuggestedExperiments()
        return cachedExperiments.firstOrNull { it.id == experimentId }
            ?: throw DiscoveryApiException("実験が見つかりません: $experimentId")
    }
    override suspend fun selectExperiment(experimentId: String) {
        val response = client.post("/experiments/$experimentId/select") {
            contentType(ContentType.Application.Json)
            setBody(ExperimentSelectRequest(selectionNote = DEFAULT_SELECTION_NOTE))
        }
        if (!response.status.isSuccess()) {
            throw DiscoveryApiException("実験の選択に失敗しました (HTTP ${response.status.value})")
        }
    }

    override suspend fun cycleNextExperiment(): Experiment {
        if (cachedExperiments.isEmpty()) getSuggestedExperiments()
        if (cachedExperiments.isEmpty()) {
            throw DiscoveryApiException("表示できる実験がありません")
        }
        cycleIndex = (cycleIndex + 1) % cachedExperiments.size
        return cachedExperiments[cycleIndex]
    }

    override suspend fun startExperiment(experimentId: String) {
        val response = client.post("/experiments/$experimentId/start")
        if (!response.status.isSuccess()) {
            throw DiscoveryApiException("実験の開始に失敗しました (HTTP ${response.status.value})")
        }
    }
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
    override suspend fun skipExperiment(experimentId: String) {
        val response = client.post("/experiments/$experimentId/skip") {
            contentType(ContentType.Application.Json)
            setBody(ExperimentSkipRequest())
        }
        if (!response.status.isSuccess()) {
            throw DiscoveryApiException("実験のスキップに失敗しました (HTTP ${response.status.value})")
        }
    }
    override suspend fun getDiscovery(): DiscoveryData = DiscoveryData(observation = "", hypothesis = "")
    override suspend fun getNextExperiment(): Experiment {
        if (cachedExperiments.isEmpty()) getSuggestedExperiments()
        if (cachedExperiments.isEmpty()) {
            throw DiscoveryApiException("表示できる実験がありません")
        }
        val nextIndex = (cycleIndex + 1) % cachedExperiments.size
        return cachedExperiments[nextIndex]
    }
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

private const val DEFAULT_SELECTION_NOTE = "アプリから選択"

@Serializable
private data class ExperimentSelectRequest(val selectionNote: String)

@Serializable
private data class ExperimentSkipRequest(val reason: String? = null)

@Serializable
private data class ExperimentResultRequest(
    val enjoyment: Int,
    val curiosity: Int,
    val retryIntent: Int,
    val confidence: Float,
    val reflection: String? = null
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
