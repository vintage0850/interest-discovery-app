package com.example.myapplication.shared.discovery

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlin.math.round

/**
 * backend/discovery（Ktor Client 経由）に接続する Discovery 機能のリポジトリ実装。
 *
 * @param baseUrl バックエンドのベース URL。実機では開発機の LAN 内 IP（例: http://192.168.x.x:8000）を指定する。
 * @param httpClient テスト用に差し替え可能な [HttpClient]。省略時は共通設定で生成する。
 */
class RealDiscoveryRepository(
    baseUrl: String = DEFAULT_BASE_URL,
    httpClient: HttpClient? = null,
    private val studentLabel: String = "test_user",
    private val settingsStorage: DiscoverySettingsStorage = InMemoryDiscoverySettingsStorage(),
    private val sessionStorage: SessionStorage = InMemorySessionStorage(),
    enableHttpLogging: Boolean = false
) : DiscoveryRepository {

    private val client = httpClient ?: defaultDiscoveryHttpClient(baseUrl, enableHttpLogging)

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

        // 永続化されたセッション ID があれば、存在確認して復帰する。
        sessionStorage.getLastSessionId()?.let { storedId ->
            val verify = client.get("/sessions/$storedId/summary")
            if (verify.status.isSuccess()) {
                sessionId = storedId
                return storedId
            }
        }

        // 復帰できなければ新規作成する。
        val response = client.post("/sessions") {
            contentType(ContentType.Application.Json)
            setBody(SessionCreateRequest(studentLabel = studentLabel))
        }
        if (!response.status.isSuccess()) {
            throw DiscoveryApiException("セッション作成に失敗しました (HTTP ${response.status.value})")
        }
        val created = response.body<SessionResponseDto>()
        sessionId = created.id
        sessionStorage.saveLastSessionId(created.id)
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

    override suspend fun getHomeState(): HomeData {
        val experiments = getSuggestedExperiments()
        return HomeData(
            todayExperiments = experiments,
            featuredExperiment = experiments.firstOrNull()
        )
    }
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
        cachedExperiments = emptyList()
    }
    override suspend fun skipExperiment(experimentId: String) {
        val response = client.post("/experiments/$experimentId/skip") {
            contentType(ContentType.Application.Json)
            setBody(ExperimentSkipRequest())
        }
        if (!response.status.isSuccess()) {
            throw DiscoveryApiException("実験のスキップに失敗しました (HTTP ${response.status.value})")
        }
        cachedExperiments = emptyList()
    }
    private suspend fun fetchSummary(): SessionSummaryDto {
        val id = ensureSession()
        val response = client.get("/sessions/$id/summary")
        if (!response.status.isSuccess()) {
            throw DiscoveryApiException("サマリーの取得に失敗しました (HTTP ${response.status.value})")
        }
        return response.body()
    }

    override suspend fun getDiscovery(): DiscoveryData {
        val summary = fetchSummary()
        val behavior = summary.behaviorSummary
        val observation = if (behavior.completedExperiments == 0) {
            "まだ実験が完了していません。実験を1つ完了すると、ここに観察結果が表示されます。"
        } else {
            "これまでに${behavior.completedExperiments}件の実験を完了しました" +
                (behavior.avgEnjoyment?.let { "（平均興味度 ${it.roundedTo1Decimal()}）。" } ?: "。")
        }
        // §34: 確信度が足りない仮説はサーバー側で保存されず null になる。
        // でっち上げの気づきを見せるより「まだ十分な根拠がない」と伝える。
        val hypothesis = summary.latestHypothesis?.summary
            ?: "まだはっきりした傾向は見えていません。もう少し試してみると、あなたらしい基準が見つかるかもしれません。"
        return DiscoveryData(
            observation = observation,
            hypothesis = hypothesis,
            hypothesisId = summary.latestHypothesis?.id,
            criteria = summary.criteria.map { it.toUiModel() }
        )
    }

    override suspend fun sendHypothesisFeedback(
        hypothesisId: Int,
        reaction: HypothesisReaction
    ): HypothesisFeedbackOutcome {
        // POSTレスポンス（HypothesisFeedbackResult）だけで状態更新する。
        // 追加でGETし直すと、POST成功後にGETが失敗した場合サーバー側は既に更新済みなのに
        // クライアントは失敗扱いになり、再送でconfidenceが二重に増減し得る（Gate4指摘）。
        val response = client.post("/hypotheses/$hypothesisId/feedback") {
            contentType(ContentType.Application.Json)
            setBody(HypothesisFeedbackRequest(reaction = reaction.name.lowercase()))
        }
        if (!response.status.isSuccess()) {
            throw DiscoveryApiException("仮説への反応の送信に失敗しました (HTTP ${response.status.value})")
        }
        val result = response.body<HypothesisFeedbackResultDto>()
        return HypothesisFeedbackOutcome(
            hypothesisSummary = result.updatedHypothesis.summary,
            hypothesisConfidence = result.updatedHypothesis.confidence,
            criterion = result.newCriterion?.toUiModel()
        )
    }

    override suspend fun getNextExperiment(): Experiment {
        if (cachedExperiments.isEmpty()) getSuggestedExperiments()
        if (cachedExperiments.isEmpty()) {
            throw DiscoveryApiException("表示できる実験がありません")
        }
        val nextIndex = (cycleIndex + 1) % cachedExperiments.size
        return cachedExperiments[nextIndex]
    }

    override suspend fun getDomainFields(): List<DomainField> {
        val summary = fetchSummary().behaviorSummary
        return DOMAIN_FIELD_META.map { meta ->
            val experimentCount = summary.domainExperimentCounts[meta.id] ?: 0
            val triedCount = summary.domainCompletedCounts[meta.id] ?: 0
            val status = when {
                experimentCount == 0 -> ExploreStatus.UNEXPLORED
                triedCount == 0 -> ExploreStatus.EXPLORED
                else -> ExploreStatus.TRIED
            }
            DomainField(
                id = meta.id,
                title = meta.title,
                description = meta.description,
                status = status,
                iconEmoji = meta.iconEmoji,
                experimentCount = experimentCount,
                triedCount = triedCount,
                recommendedSignal = domainToBehaviorSignal[meta.id] ?: BehaviorSignal.ANALYZE
            )
        }
    }

    override suspend fun getWeeklyNarrative(): WeeklyNarrative {
        val id = ensureSession()
        val response = client.get("/sessions/$id/report/weekly-narrative")
        if (!response.status.isSuccess()) {
            throw DiscoveryApiException("週次レポートの取得に失敗しました (HTTP ${response.status.value})")
        }
        val narrative = response.body<WeeklyNarrativeResponseDto>()
        return WeeklyNarrative(
            weeklyInsights = narrative.weeklyInsights,
            changeFromPast = narrative.changeFromPast
        )
    }

    override suspend fun getReportData(): ReportData {
        val summary = fetchSummary().behaviorSummary
        val narrative = try {
            getWeeklyNarrative()
        } catch (_: DiscoveryApiException) {
            WeeklyNarrative(
                weeklyInsights = "週次レポートは現在取得できません。",
                changeFromPast = "週次レポートは現在取得できません。"
            )
        }
        val signalCounts = resolveSignalCounts(summary)
        val topSignal = signalCounts.maxByOrNull { it.value }?.key ?: BehaviorSignal.ANALYZE
        return ReportData(
            totalCompletedCount = summary.completedExperiments,
            totalMinutesSpent = summary.totalMinutesSpent,
            topSignal = topSignal,
            weeklyInsights = narrative.weeklyInsights,
            changeFromPast = narrative.changeFromPast,
            signalDistribution = signalCounts.mapKeys { it.key.japaneseLabel }
        )
    }

    private fun resolveSignalCounts(summary: BehaviorSummaryDto): Map<BehaviorSignal, Int> {
        val fromActionTypes = summary.actionTypeCounts.mapNotNull { (key, count) ->
            BehaviorSignal.entries.firstOrNull { it.name.equals(key, ignoreCase = true) }?.to(count)
        }.toMap()
        if (fromActionTypes.isNotEmpty()) return fromActionTypes

        return summary.domainCompletedCounts.mapNotNull { (domain, count) ->
            domainToBehaviorSignal[domain]?.let { it to count }
        }.groupBy({ it.first }, { it.second })
            .mapValues { (_, counts) -> counts.sum() }
    }

    override suspend fun getSettings(): MyDataSettings {
        val stored = settingsStorage.load()
        val totalSignals = fetchSummary().behaviorSummary.totalSignals
        return stored.copy(savedSignalCount = totalSignals)
    }
    override suspend fun updateSettings(settings: MyDataSettings) {
        settingsStorage.save(settings)
    }
    override suspend fun resetAllData() {
        sessionId = null
        cachedExperiments = emptyList()
        cycleIndex = 0
        sessionStorage.clear()
    }

    override suspend fun completeOnboarding(
        nickname: String?,
        ageRange: String?,
        schoolStage: String?,
        optionalInterests: List<String>,
        initialSelfUnderstandingScore: Float
    ) {
        val id = ensureSession()
        val response = client.patch("/sessions/$id/onboarding") {
            contentType(ContentType.Application.Json)
            setBody(
                OnboardingUpdateRequest(
                    nickname = nickname,
                    ageRange = ageRange,
                    schoolStage = schoolStage,
                    optionalInterests = optionalInterests,
                    initialSelfUnderstandingScore = initialSelfUnderstandingScore
                )
            )
        }
        if (!response.status.isSuccess()) {
            throw DiscoveryApiException("オンボーディング情報の更新に失敗しました (HTTP ${response.status.value})")
        }
    }

    // ---- 案件18：既存セッション一覧・復帰 ----

    override suspend fun getSessionList(): List<SessionSummaryItem> {
        val response = client.get("/sessions") {
            url { parameters.append("student_label", studentLabel) }
        }
        if (!response.status.isSuccess()) {
            throw DiscoveryApiException("セッション一覧の取得に失敗しました (HTTP ${response.status.value})")
        }
        return response.body<List<SessionResponseDto>>().map {
            SessionSummaryItem(
                id = it.id,
                nickname = it.nickname,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt
            )
        }
    }

    override suspend fun switchToSession(id: Int) {
        sessionId = id
        sessionStorage.saveLastSessionId(id)
        cachedExperiments = emptyList()
        cycleIndex = 0
    }

    // ---- 案件18：心理軸アンケート ----

    override suspend fun submitPsychAxisSurvey(scores: Map<PsychAxis, Float>): List<PsychAxisUiModel> {
        val id = ensureSession()
        val response = client.post("/sessions/$id/psych-axis-survey") {
            contentType(ContentType.Application.Json)
            setBody(PsychAxisSurveySubmitRequest(scores = scores.mapKeys { it.key.name.uppercase() }))
        }
        if (!response.status.isSuccess()) {
            throw DiscoveryApiException("心理軸アンケートの送信に失敗しました (HTTP ${response.status.value})")
        }
        val results = response.body<List<PsychAxisResultResponseDto>>()
        return results.map {
            PsychAxisUiModel(
                axis = PsychAxis.fromString(it.axis),
                score = it.score
            )
        }
    }

    // ---- 案件18：ユーザー主導 Reflection ----

    override suspend fun addReflection(content: String, mood: Int?) {
        val id = ensureSession()
        val response = client.post("/sessions/$id/reflections") {
            contentType(ContentType.Application.Json)
            setBody(UserReflectionCreateRequest(content = content, mood = mood))
        }
        if (!response.status.isSuccess()) {
            throw DiscoveryApiException("振り返りの保存に失敗しました (HTTP ${response.status.value})")
        }
    }

    override suspend fun getReflections(): List<ReflectionUiModel> {
        val id = ensureSession()
        val response = client.get("/sessions/$id/reflections")
        if (!response.status.isSuccess()) {
            throw DiscoveryApiException("振り返り一覧の取得に失敗しました (HTTP ${response.status.value})")
        }
        return response.body<List<UserReflectionResponseDto>>().map {
            ReflectionUiModel(
                id = it.id.toString(),
                content = it.content,
                mood = it.mood,
                createdAt = it.createdAt
            )
        }
    }

    // ---- 案件19：Evidence 一覧 ----

    override suspend fun getEvidenceList(sessionId: Int): List<EvidenceUiModel> {
        val response = client.get("/sessions/$sessionId/evidence")
        if (!response.status.isSuccess()) {
            throw DiscoveryApiException("エビデンス一覧の取得に失敗しました (HTTP ${response.status.value})")
        }
        return response.body<List<EvidenceResponseDto>>().map { it.toUiModel() }
    }

    companion object {
        /** エミュレータから開発機 localhost を参照するための標準 URL。実機では呼び出し元でLAN IPを渡す。 */
        const val DEFAULT_BASE_URL = "http://10.0.2.2:8000"
    }
}

/** backend/discovery API 呼び出し時のエラー。 */
class DiscoveryApiException(message: String) : Exception(message)

@Serializable
private data class OnboardingUpdateRequest(
    val nickname: String?,
    val ageRange: String?,
    val schoolStage: String?,
    val optionalInterests: List<String>?,
    val initialSelfUnderstandingScore: Float?
)

@Serializable
private data class SessionCreateRequest(val studentLabel: String)

@Serializable
private data class SessionResponseDto(
    val id: Int,
    val nickname: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)

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

@Serializable
private data class BehaviorSummaryDto(
    val completedExperiments: Int,
    val avgEnjoyment: Float? = null,
    val totalMinutesSpent: Int = 0,
    val totalSignals: Int = 0,
    val domainExperimentCounts: Map<String, Int> = emptyMap(),
    val domainCompletedCounts: Map<String, Int> = emptyMap(),
    val actionTypeCounts: Map<String, Int> = emptyMap()
)

@Serializable
private data class HypothesisResponseDto(
    val id: Int,
    val summary: String,
    val confidence: Float,
    @SerialName("supporting_evidence")
    val supportingEvidence: List<Int> = emptyList()
)

@Serializable
private data class EvidenceResponseDto(
    val id: Int,
    val sessionId: Int,
    val domain: String,
    val signalCount: Int,
    val summaryText: String,
    val createdAt: Instant
)

private fun EvidenceResponseDto.toUiModel(): EvidenceUiModel = EvidenceUiModel(
    id = id,
    domain = domain,
    signalCount = signalCount,
    summaryText = summaryText,
    createdAt = createdAt
)


@Serializable
private data class CriterionResponseDto(
    val id: Int,
    val label: String,
    val confidence: Float
)

private fun CriterionResponseDto.toUiModel(): CriterionUiModel = CriterionUiModel(
    id = id,
    label = label,
    confidence = confidence,
    confidenceLabel = HypothesisFeedbackPolicy.confidenceLabelFor(confidence)
)

@Serializable
private data class SessionSummaryDto(
    val behaviorSummary: BehaviorSummaryDto,
    val latestHypothesis: HypothesisResponseDto? = null,
    val criteria: List<CriterionResponseDto> = emptyList()
)

@Serializable
private data class WeeklyNarrativeResponseDto(
    val weeklyInsights: String,
    val changeFromPast: String
)

@Serializable
private data class HypothesisFeedbackRequest(val reaction: String)

@Serializable
private data class HypothesisFeedbackResponseDto(
    val id: Int,
    val hypothesisId: Int,
    val reaction: String
)

@Serializable
private data class HypothesisFeedbackResultDto(
    val feedback: HypothesisFeedbackResponseDto,
    val updatedHypothesis: HypothesisResponseDto,
    val newCriterion: CriterionResponseDto? = null
)

@Serializable
private data class PsychAxisSurveySubmitRequest(val scores: Map<String, Float>)

@Serializable
private data class PsychAxisResultResponseDto(
    val id: Int,
    val sessionId: Int,
    val axis: String,
    val score: Float,
    val createdAt: Instant,
    val updatedAt: Instant
)

@Serializable
private data class UserReflectionCreateRequest(
    val content: String,
    val mood: Int? = null
)

@Serializable
private data class UserReflectionResponseDto(
    val id: Int,
    val sessionId: Int,
    val content: String,
    val mood: Int?,
    val createdAt: Instant
)

private fun Float.roundedTo1Decimal(): String {
    val rounded = round(this * 10) / 10
    return rounded.toString()
}

private data class DomainFieldMeta(
    val id: String,
    val title: String,
    val description: String,
    val iconEmoji: String
)

private val DOMAIN_FIELD_META = listOf(
    DomainFieldMeta("tech", "テクノロジー・プログラミング", "アルゴリズムや自動化、コンピュータが動くロジックを体験します。", "💻"),
    DomainFieldMeta("art", "アート・デザイン", "画面レイアウトや視覚的な情報の整理、表現の工夫を探求します。", "🎨"),
    DomainFieldMeta("music", "音楽", "音を作る・聴く体験を探求します。", "🎵"),
    DomainFieldMeta("sports", "スポーツ・運動", "体を動かし、技術や記録の向上を試します。", "🏃"),
    DomainFieldMeta("science", "サイエンス・観察", "身の回りの現象や仮説検証の面白さを体験します。", "🔬"),
    DomainFieldMeta("social", "社会・コミュニケーション", "人との関わり方や伝え方を探求します。", "🗣️"),
    DomainFieldMeta("making", "モノづくり・工作", "実物の手触りや立体的な構造を観察・試作します。", "📦"),
    DomainFieldMeta("nature", "自然・環境", "生き物や環境の変化を観察します。", "🌿"),
    DomainFieldMeta("business", "ビジネス・仕組み", "お店の仕掛けや世の中のサービスの仕組みを観察します。", "📈"),
    DomainFieldMeta("other", "その他", "分類にとらわれず、興味のおもむくままに試します。", "✨")
)

/** [MyDataSettings] の永続化先。プラットフォームごとに差し替え可能。 */
interface DiscoverySettingsStorage {
    fun load(): MyDataSettings
    fun save(settings: MyDataSettings)
}

/** 永続化しない既定実装。テストや未対応プラットフォームで使用する。 */
class InMemoryDiscoverySettingsStorage : DiscoverySettingsStorage {
    private var current = MyDataSettings()
    override fun load(): MyDataSettings = current
    override fun save(settings: MyDataSettings) {
        current = settings
    }
}

internal fun defaultDiscoveryHttpClient(baseUrl: String, enableHttpLogging: Boolean): HttpClient {
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
        if (enableHttpLogging) {
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.ALL
            }
        }
    }
}
