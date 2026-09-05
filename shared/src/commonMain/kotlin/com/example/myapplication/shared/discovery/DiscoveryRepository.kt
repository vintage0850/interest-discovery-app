package com.example.myapplication.shared.discovery

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
    suspend fun sendHypothesisFeedback(hypothesisId: Int, reaction: HypothesisReaction): HypothesisFeedbackOutcome
    suspend fun getNextExperiment(): Experiment
    suspend fun getDomainFields(): List<DomainField>
    suspend fun getWeeklyNarrative(): WeeklyNarrative
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
}

/** 直近7日とその前7日の比較から生成された週次レポート文。 */
data class WeeklyNarrative(
    val weeklyInsights: String,
    val changeFromPast: String
)
