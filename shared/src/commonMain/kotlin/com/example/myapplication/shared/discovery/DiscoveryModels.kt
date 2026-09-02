package com.example.myapplication.shared.discovery

import kotlinx.serialization.Serializable

/**
 * 行動シグナルの種類（性格診断ではなく行動特性のシグナル）。
 */
@Serializable
enum class BehaviorSignal(val label: String, val japaneseLabel: String) {
    ANALYZE("ANALYZE", "分析する"),
    CREATE("CREATE", "つくる"),
    COMPARE("COMPARE", "比べる"),
    EXPLAIN("EXPLAIN", "説明する"),
    ORGANIZE("ORGANIZE", "整理する"),
    INVESTIGATE("INVESTIGATE", "調べる"),
    IMPROVE("IMPROVE", "改善する");

    companion object {
        fun fromString(value: String): BehaviorSignal =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: ANALYZE
    }
}

/**
 * シグナルの状態（成長中、中立など）
 */
@Serializable
enum class SignalTrend {
    GROWING,
    NEUTRAL,
    DECLINING
}

/**
 * UI表示用のシグナルモデル
 */
@Serializable
data class SignalUiModel(
    val signal: BehaviorSignal,
    val trend: SignalTrend = SignalTrend.NEUTRAL,
    val count: Int = 1
)

/**
 * 探索ステータス（Exploreでの状態）
 */
@Serializable
enum class ExploreStatus(val label: String) {
    UNEXPLORED("未探索"),
    EXPLORED("Explore済み"),
    TRIED("Try済み"),
    DIVE_CANDIDATE("Dive候補 🔥")
}

/**
 * 探索分野カテゴリー
 */
@Serializable
data class DomainField(
    val id: String,
    val title: String,
    val description: String,
    val status: ExploreStatus,
    val iconEmoji: String,
    val experimentCount: Int,
    val triedCount: Int,
    val recommendedSignal: BehaviorSignal
)

/**
 * 行動実験モデル
 */
@Serializable
data class Experiment(
    val id: String,
    val title: String,
    val description: String,
    val plannedMinutes: Int,
    val actionType: BehaviorSignal,
    val reason: String = "",
    val testedHypothesis: String = "",
    val domainFieldId: String = "design_ui"
)

/**
 * 発見・仮説データ（Discover画面用）
 */
@Serializable
data class DiscoveryData(
    val observation: String,
    val hypothesis: String,
    val testingFocus: String = "実際に手を動かして作る（CREATE）ことにも興味が広がるか観察中",
    val recentChanges: String = "先週と比べて「比べる」「分析する」シグナルが急上昇しています",
    val evidenceReason: String = "直近3回の実験で高評価（4〜5点）を付け、予定時間より長く取り組んだため",
    val disclaimer: String = "※ これは現時点の行動から導き出した仮説です。",
    val nextExperiment: Experiment? = null
)

/**
 * 週次・月次レポートデータ
 */
@Serializable
data class ReportData(
    val totalCompletedCount: Int = 4,
    val totalMinutesSpent: Int = 25,
    val topSignal: BehaviorSignal = BehaviorSignal.ANALYZE,
    val weeklyInsights: String = "「構造を見比べる」「UIを分析する」活動に自然と時間が伸びる傾向があります。",
    val changeFromPast: String = "先月は「つくる」中心でしたが、今月は「仕組みを見る」「比べる」ことへの関心が高まっています。",
    val signalDistribution: Map<String, Int> = mapOf(
        "分析する" to 4,
        "比べる" to 3,
        "つくる" to 2,
        "整理する" to 1
    )
)

/**
 * ホーム画面の表示状態データ
 */
@Serializable
data class HomeData(
    val greetingTitle: String = "こんにちは",
    val greetingSubtitle: String = "今日、5分だけ試してみよう",
    val todayCompleted: Boolean = false,
    val todayExperiments: List<Experiment> = emptyList(),
    val featuredExperiment: Experiment? = null,
    val alternativeExperimentsCount: Int = 2,
    val completedThisWeek: Int = 2,
    val signals: List<SignalUiModel> = emptyList(),
    val discoveryInsight: String? = null
)

/**
 * 設定・マイデータ画面用モデル
 */
@Serializable
data class MyDataSettings(
    val notificationsEnabled: Boolean = true,
    val reminderTime: String = "18:00",
    val dataSharingEnabled: Boolean = false,
    val savedSignalCount: Int = 10
)

/**
 * アプリのボトムナビゲーションタブ
 */
enum class AppTab(val title: String, val iconEmoji: String) {
    HOME("ホーム", "🏠"),
    DISCOVER("発見", "🔍"),
    EXPLORE("探索", "🧭"),
    REPORT("レポート", "📊"),
    SETTINGS("設定", "⚙️")
}

/**
 * デバッグ・検証用のフェイクシナリオ
 */
enum class FakeScenario {
    NORMAL,
    FIRST_TIME_USER,
    LOADING,
    ERROR,
    EMPTY_DISCOVERY
}
