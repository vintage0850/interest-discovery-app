package com.example.myapplication.shared.discovery

import kotlinx.datetime.Instant
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
 * 通知候補のドメイン状態。
 * backend の [NotificationCandidateDomainStatus] と整合させる。
 */
@Serializable
enum class NotificationCandidateDomainStatus(val label: String) {
    DIVE_CANDIDATE("深掘り候補"),
    TRIED("Try済み"),
    EXPLORED("Explore済み"),
    UNEXPLORED("未探索")
}

/**
 * Google Calendar 空き時間通知の候補。
 */
@Serializable
data class NotificationCandidate(
    val experiment: Experiment,
    val domainStatus: NotificationCandidateDomainStatus,
    val reason: String
)

/**
 * 通知の重複抑止情報。
 * Android 端末内 [DiscoverySettingsStorage] へ永続化する。
 */
@Serializable
data class NotificationLog(
    val lastNotifiedAt: Instant? = null,
    val notifiedExperimentDates: Map<String, String> = emptyMap()
)

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
 * 仮説への生徒の反応（同感/わからない/違う）。
 */
@Serializable
enum class HypothesisReaction {
    AGREE,
    UNSURE,
    DISAGREE
}

/**
 * 生徒に同感された、個人の意思決定基準（Criterion）のUI表示用モデル。
 * confidenceLabel は §18 の通り数値ではなく定性的なラベルにする。
 */
@Serializable
data class CriterionUiModel(
    val id: Int,
    val label: String,
    val confidence: Float,
    val confidenceLabel: String
)

/**
 * 仮説へのフィードバック送信結果。POSTレスポンスを1往復でUIへ反映するための最小情報。
 * §34のno-insightガードにより仮説そのものが常に存在するとは限らないため、
 * この結果は「送信対象だった仮説」に対する更新のみを表す。
 */
data class HypothesisFeedbackOutcome(
    val hypothesisSummary: String,
    val hypothesisConfidence: Float,
    val criterion: CriterionUiModel?
)

/**
 * バックエンド（`backend/discovery/repository.py`）と同じconfidence閾値。
 * Real/Fake両実装、UIラベル変換で使う値をここに集約し、ドリフトを防ぐ
 * （Codex Gate4非ブロッキング指摘への対応）。バックエンド側の値を変更する場合はこちらも合わせる。
 */
object HypothesisFeedbackPolicy {
    const val AGREE_DELTA = 0.15f
    const val UNSURE_DELTA = 0f
    const val DISAGREE_DELTA = -0.15f
    const val CRITERION_PROMOTION_THRESHOLD = 0.6f
    const val CONSISTENT_LABEL_THRESHOLD = 0.85f

    fun deltaFor(reaction: HypothesisReaction): Float = when (reaction) {
        HypothesisReaction.AGREE -> AGREE_DELTA
        HypothesisReaction.UNSURE -> UNSURE_DELTA
        HypothesisReaction.DISAGREE -> DISAGREE_DELTA
    }

    fun confidenceLabelFor(confidence: Float): String = when {
        confidence >= CONSISTENT_LABEL_THRESHOLD -> "Consistent"
        confidence >= CRITERION_PROMOTION_THRESHOLD -> "Appearing"
        else -> "Beginning"
    }
}

/**
 * 行動シグナルと実験結果の矛盾（Discover画面の「気になる発見」用）。
 * backend の `_detect_discrepancies()` が生成する各要素をそのまま受け取る。
 */
@Serializable
data class DiscrepancyUiModel(
    val type: String,
    val domain: String,
    val experimentId: Int,
    val message: String
)

/**
 * 発見・仮説データ（Discover画面用）
 */
@Serializable
data class DiscoveryData(
    val observation: String,
    val hypothesis: String,
    val hypothesisId: Int? = null,
    val testingFocus: String = "実際に手を動かして作る（CREATE）ことにも興味が広がるか観察中",
    val recentChanges: String = "先週と比べて「比べる」「分析する」シグナルが急上昇しています",
    val evidenceReason: String = "直近3回の実験で高評価（4〜5点）を付け、予定時間より長く取り組んだため",
    val disclaimer: String = "※ これは現時点の行動から導き出した仮説です。",
    val nextExperiment: Experiment? = null,
    val criteria: List<CriterionUiModel> = emptyList(),
    val discrepancies: List<DiscrepancyUiModel> = emptyList()
)

/**
 * 直近30日とその前30日の比較から生成された月次レポート文。
 */
@Serializable
data class MonthlyNarrative(
    val periodStart: String,
    val periodEndExclusive: String,
    val monthlyInsights: String,
    val progressWave: String,
    val continuityInsight: String
)

/**
 * 気付きレポートの種別（週次／月次）。
 */
@Serializable
enum class ReportType(val value: String) {
    WEEKLY("weekly"),
    MONTHLY("monthly");

    companion object {
        fun fromValue(value: String?): ReportType? =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
    }
}

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
    ),
    val monthlyNarrative: MonthlyNarrative? = null,
    val monthlyErrorMessage: String? = null
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

/**
 * 心理軸アンケートの4軸。
 * バックエンド [backend/discovery/models.py] の PsychAxis と整合させる。
 */
@Serializable
enum class PsychAxis(val label: String, val japaneseLabel: String) {
    INVESTIGATE("INVESTIGATE", "探究する"),
    CREATE("CREATE", "つくる"),
    EXECUTE("EXECUTE", "実行する"),
    COMMUNICATE("COMMUNICATE", "伝える");

    companion object {
        fun fromString(value: String): PsychAxis =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: INVESTIGATE
    }
}

/**
 * 心理軸アンケートの結果表示用 UI モデル。
 */
@Serializable
data class PsychAxisUiModel(
    val axis: PsychAxis,
    val score: Float
)

/**
 * ユーザー主導 Reflection（日記的振り返り）の UI モデル。
 * 作成日時は UTC の [Instant] を使う（TIMEZONE.md 準拠）。
 */
@Serializable
data class ReflectionUiModel(
    val id: String,
    val content: String,
    val mood: Int? = null,
    val createdAt: Instant
)

/**
 * 行動シグナルから集約されたエビデンス（Evidence）の UI モデル。
 * 作成日時は UTC の [Instant] を使う（TIMEZONE.md 準拠）。
 */
@Serializable
data class EvidenceUiModel(
    val id: Int,
    val domain: String,
    val signalCount: Int,
    val summaryText: String,
    val createdAt: Instant
)

