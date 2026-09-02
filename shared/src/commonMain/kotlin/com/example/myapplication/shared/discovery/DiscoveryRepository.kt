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
    suspend fun getNextExperiment(): Experiment
    suspend fun getDomainFields(): List<DomainField>
    suspend fun getReportData(): ReportData
    suspend fun getSettings(): MyDataSettings
    suspend fun updateSettings(settings: MyDataSettings)
    suspend fun resetAllData()
    fun setScenario(scenario: FakeScenario)
    fun getCurrentScenario(): FakeScenario
}
