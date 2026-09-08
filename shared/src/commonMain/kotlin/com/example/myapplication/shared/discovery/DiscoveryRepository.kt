package com.example.myapplication.shared.discovery

import kotlinx.datetime.Instant

/**
 * 興味発見機能のリポジトリ抽象化インターフェース。
 */
interface DiscoveryRepository {
    suspend fun getHomeState(): HomeData
    suspend fun getExperiment(experimentId: String): Experiment
    suspend fun getSuggestedExperiments(): List<Experiment>
    suspend fun selectExperiment(experimentId: String)
    suspend fun cycleNextExperiment(): Experiment
    suspend fun startExperiment(experimentId: String)
    suspend fun completeExperiment(
        experimentId: String,
        enjoyment: Int,
        curiosity: Int,
        retryIntent: Int
    )
    suspend fun skipExperiment(experimentId: String)
    suspend fun getDiscovery(): DiscoveryData

    /** 興味仮説を再生成する。成否は呼び出し元で通知し、画面状態は後続の [getDiscovery] で再取得する。 */
    suspend fun updateHypothesis()

    suspend fun sendHypothesisFeedback(hypothesisId: Int, reaction: HypothesisReaction): HypothesisFeedbackOutcome
    suspend fun getNextExperiment(): Experiment
    suspend fun getDomainFields(): List<DomainField>
    suspend fun getWeeklyNarrative(): WeeklyNarrative
    suspend fun getMonthlyNarrative(): MonthlyNarrative = throw NotImplementedError("getMonthlyNarrative is not implemented")
    suspend fun getReportData(): ReportData
    suspend fun getSettings(): MyDataSettings
    suspend fun updateSettings(settings: MyDataSettings)
    suspend fun resetAllData()
    suspend fun completeOnboarding(
        nickname: String?,
        ageRange: String?,
        schoolStage: String?,
        optionalInterests: List<String>,
        initialSelfUnderstandingScore: Float
    )

    // ---- 案件18：既存セッション一覧・復帰 ----

    /** 同一 student_label のセッション一覧を取得する（updated_at 降順）。 */
    suspend fun getSessionList(): List<SessionSummaryItem>

    /** 指定した既存セッションに切り替える。 */
    suspend fun switchToSession(id: Int)

    // ---- 案件18：心理軸アンケート ----

    /** 心理4軸アンケート結果を送信する。 */
    suspend fun submitPsychAxisSurvey(scores: Map<PsychAxis, Float>): List<PsychAxisUiModel>

    // ---- 案件18：ユーザー主導 Reflection ----

    /** ユーザー主導の振り返りを追加する。 */
    suspend fun addReflection(content: String, mood: Int?)

    /** ユーザー主導の振り返り一覧を取得する（created_at 降順）。 */
    suspend fun getReflections(): List<ReflectionUiModel>

    // ---- 案件19：Evidence 一覧 ----

    /** 指定したセッションの Evidence 一覧を取得する（created_at 降順）。 */
    suspend fun getEvidenceList(sessionId: Int): List<EvidenceUiModel>

    // ---- 案件22：Google Calendar 連携通知 ----

    /** 通知候補となる選択済み実験を、ドメイン状態付きで取得する。 */
    suspend fun getNotificationCandidates(): List<NotificationCandidate>

    /** 現在のアクティブなセッションIDを取得する。 */
    suspend fun getActiveSessionId(): Int? = null
}

/** 直近7日とその前7日の比較から生成された週次レポート文。 */
data class WeeklyNarrative(
    val weeklyInsights: String,
    val changeFromPast: String
)

/**
 * セッション一覧表示用の軽量 DTO。
 */
data class SessionSummaryItem(
    val id: Int,
    val nickname: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)
