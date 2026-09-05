package com.example.myapplication.shared.discovery

import kotlinx.coroutines.delay

private const val WEEKLY_INSIGHTS =
    "「構造を見比べる」「UIを分析する」活動に自然と時間が伸びる傾向があります。"
private const val CHANGE_FROM_PAST =
    "先月は「つくる」中心でしたが、今月は「仕組みを見る」「比べる」ことへの関心が高まっています。"

/**
 * 完全なインメモリ状態で動作する Discovery 機能のリポジトリ実装。
 */
class FakeDiscoveryRepository(
    initialScenario: FakeScenario = FakeScenario.NORMAL,
    private val enableArtificialDelay: Boolean = true
) : DiscoveryRepository {

    private var scenario: FakeScenario = initialScenario

    // 5つの初期実験プール
    private val allExperiments = listOf(
        Experiment(
            id = "exp-1",
            title = "好きなゲームのUIを観察する",
            description = "普段遊んでいるゲームの画面を1つ見て、操作しやすい部分や分かりにくい部分を3つ見つけてみよう。",
            plannedMinutes = 5,
            actionType = BehaviorSignal.ANALYZE,
            reason = "ゲームや視覚的なデザインに関心があるようです。",
            testedHypothesis = "視覚的な構造や情報配置の分析に興味があるかもしれません。",
            domainFieldId = "design_ui"
        ),
        Experiment(
            id = "exp-2",
            title = "3つの図形でミニポスターをつくる",
            description = "まる・さんかく・しかくと短いキャッチコピーだけで、シンプルなポスターを1枚考えてみよう。",
            plannedMinutes = 10,
            actionType = BehaviorSignal.CREATE,
            reason = "デザインや視覚的な構成に興味がありそうです。",
            testedHypothesis = "実際に手を動かして何かを形づくることに面白さを感じるかもしれません。",
            domainFieldId = "design_ui"
        ),
        Experiment(
            id = "exp-3",
            title = "よく使う2つのアプリ画面を比べる",
            description = "よく使うアプリを2つ開いて、情報の並べ方やボタンの位置の違いを3つ書き出してみよう。",
            plannedMinutes = 5,
            actionType = BehaviorSignal.COMPARE,
            reason = "画面の細かな違いによく気づく傾向があります。",
            testedHypothesis = "複数のデザインを見比べて違いを捉えるのが得意かもしれません。",
            domainFieldId = "design_ui"
        ),
        Experiment(
            id = "exp-4",
            title = "ゲームのルールを初心者に教える",
            description = "よく知っているゲームのルールを1つ選び、初めて遊ぶ友達に伝えるとしたらどう説明するか考えてみよう。",
            plannedMinutes = 5,
            actionType = BehaviorSignal.EXPLAIN,
            reason = "アイデアや論理を整理して伝えることに関心がありそうです。",
            testedHypothesis = "仕組みを分かりやすく整理して人に伝えることに興味があるかもしれません。",
            domainFieldId = "words_logic"
        ),
        Experiment(
            id = "exp-5",
            title = "コンビニの棚の並びを観察する",
            description = "商品の並び方を観察して、なぜその位置に置かれているのか理由を3つ想像してみよう。",
            plannedMinutes = 10,
            actionType = BehaviorSignal.ORGANIZE,
            reason = "身の回りの空間や配置の工夫によく気づく傾向があります。",
            testedHypothesis = "日常のなかの規則性や人の行動の理由を考えるのが好きかもしれません。",
            domainFieldId = "business_structure"
        )
    )

    // 探索分野一覧
    private val domainFields = listOf(
        DomainField(
            id = "design_ui",
            title = "デザイン・UI・情報構造",
            description = "画面レイアウトや視覚的な情報の整理、使いやすさの工夫を探求します。",
            status = ExploreStatus.TRIED,
            iconEmoji = "🎨",
            experimentCount = 5,
            triedCount = 3,
            recommendedSignal = BehaviorSignal.ANALYZE
        ),
        DomainField(
            id = "business_structure",
            title = "ビジネス・仕組み・経済",
            description = "お店の仕掛けや世の中のサービスの仕組み、人とお金の動きを観察します。",
            status = ExploreStatus.DIVE_CANDIDATE,
            iconEmoji = "📈",
            experimentCount = 4,
            triedCount = 1,
            recommendedSignal = BehaviorSignal.COMPARE
        ),
        DomainField(
            id = "tech_code",
            title = "テクノロジー・プログラミング",
            description = "アルゴリズムや自動化、コンピュータが動くロジックを体験します。",
            status = ExploreStatus.EXPLORED,
            iconEmoji = "💻",
            experimentCount = 6,
            triedCount = 1,
            recommendedSignal = BehaviorSignal.CREATE
        ),
        DomainField(
            id = "words_logic",
            title = "ことば・ストーリー・論理",
            description = "人に伝える文章やルールの構成、感情を動かす表現の力を試します。",
            status = ExploreStatus.UNEXPLORED,
            iconEmoji = "📝",
            experimentCount = 4,
            triedCount = 0,
            recommendedSignal = BehaviorSignal.EXPLAIN
        ),
        DomainField(
            id = "craft_space",
            title = "モノづくり・建築・空間",
            description = "実物の手触りや空間の配置、立体的な構造を観察・試作します。",
            status = ExploreStatus.UNEXPLORED,
            iconEmoji = "📦",
            experimentCount = 5,
            triedCount = 0,
            recommendedSignal = BehaviorSignal.CREATE
        ),
        DomainField(
            id = "science_nature",
            title = "サイエンス・自然・観察",
            description = "身の回りの現象や生き物の法則、仮説検証の面白さを体験します。",
            status = ExploreStatus.UNEXPLORED,
            iconEmoji = "🔬",
            experimentCount = 4,
            triedCount = 0,
            recommendedSignal = BehaviorSignal.INVESTIGATE
        )
    )

    private var currentExperimentIndex = 0
    private var completedCount = 2
    private val completedExperimentIds = mutableSetOf<String>()

    private var signalsList = mutableListOf(
        SignalUiModel(BehaviorSignal.ANALYZE, SignalTrend.GROWING, count = 2),
        SignalUiModel(BehaviorSignal.CREATE, SignalTrend.NEUTRAL, count = 1),
        SignalUiModel(BehaviorSignal.COMPARE, SignalTrend.GROWING, count = 2)
    )

    private var currentHypothesis = "情報がどう整理されているかに気づき、より良くする方法を考えることが好きなようです。"
    private var currentObservation = "情報の比較や分析、構造の観察を伴うアクティビティにより長く取り組む傾向が見られます。"
    private var settingsData = MyDataSettings()
    private var fakeHypothesisConfidence = 0.5f
    private val fakeCriteria = mutableListOf<CriterionUiModel>()

    fun setScenario(scenario: FakeScenario) {
        this.scenario = scenario
        if (scenario == FakeScenario.FIRST_TIME_USER) {
            completedCount = 0
            signalsList.clear()
            currentExperimentIndex = 0
        } else if (scenario == FakeScenario.NORMAL) {
            completedCount = 2
            signalsList = mutableListOf(
                SignalUiModel(BehaviorSignal.ANALYZE, SignalTrend.GROWING, count = 2),
                SignalUiModel(BehaviorSignal.CREATE, SignalTrend.NEUTRAL, count = 1),
                SignalUiModel(BehaviorSignal.COMPARE, SignalTrend.GROWING, count = 2)
            )
        }
    }

    fun getCurrentScenario(): FakeScenario = scenario

    private suspend fun simulateLatency() {
        if (!enableArtificialDelay) return
        when (scenario) {
            FakeScenario.LOADING -> delay(1200)
            else -> delay(250)
        }
    }

    private fun checkErrorState() {
        if (scenario == FakeScenario.ERROR) {
            throw IllegalStateException("今日の実験を読み込めませんでした。通信状態を確認して再試行してください。")
        }
    }

    override suspend fun getHomeState(): HomeData {
        simulateLatency()
        checkErrorState()

        val featured = allExperiments.getOrNull(currentExperimentIndex) ?: allExperiments.first()
        // 今日の3つの実験候補
        val todayThree = listOf(
            allExperiments[currentExperimentIndex % allExperiments.size],
            allExperiments[(currentExperimentIndex + 1) % allExperiments.size],
            allExperiments[(currentExperimentIndex + 2) % allExperiments.size]
        )

        return when (scenario) {
            FakeScenario.FIRST_TIME_USER -> HomeData(
                greetingTitle = "こんにちは",
                greetingSubtitle = "今日、5分だけ試してみよう",
                todayCompleted = false,
                todayExperiments = todayThree,
                featuredExperiment = featured,
                alternativeExperimentsCount = allExperiments.size - 1,
                completedThisWeek = 0,
                signals = emptyList(),
                discoveryInsight = null
            )
            FakeScenario.EMPTY_DISCOVERY -> HomeData(
                greetingTitle = "こんにちは",
                greetingSubtitle = "今日、5分だけ試してみよう",
                todayCompleted = completedCount > 2,
                todayExperiments = todayThree,
                featuredExperiment = featured,
                alternativeExperimentsCount = allExperiments.size - 1,
                completedThisWeek = completedCount,
                signals = signalsList.toList(),
                discoveryInsight = "あなたの行動シグナルを少しずつ集めています。"
            )
            else -> HomeData(
                greetingTitle = "こんにちは",
                greetingSubtitle = "今日、5分だけ試してみよう",
                todayCompleted = completedCount > 2,
                todayExperiments = todayThree,
                featuredExperiment = featured,
                alternativeExperimentsCount = allExperiments.size - 1,
                completedThisWeek = completedCount,
                signals = signalsList.toList(),
                discoveryInsight = "情報がどう整理されているかに気づくのが得意なようです。"
            )
        }
    }

    override suspend fun getExperiment(experimentId: String): Experiment {
        simulateLatency()
        checkErrorState()
        return allExperiments.firstOrNull { it.id == experimentId }
            ?: allExperiments.first()
    }

    override suspend fun getSuggestedExperiments(): List<Experiment> {
        simulateLatency()
        checkErrorState()
        return allExperiments.filterIndexed { index, _ -> index != currentExperimentIndex }
    }

    override suspend fun selectExperiment(experimentId: String) {
        val index = allExperiments.indexOfFirst { it.id == experimentId }
        if (index >= 0) {
            currentExperimentIndex = index
        }
    }

    override suspend fun cycleNextExperiment(): Experiment {
        simulateLatency()
        checkErrorState()
        currentExperimentIndex = (currentExperimentIndex + 1) % allExperiments.size
        return allExperiments[currentExperimentIndex]
    }

    override suspend fun startExperiment(experimentId: String) {
        simulateLatency()
        checkErrorState()
    }

    override suspend fun completeExperiment(
        experimentId: String,
        enjoyment: Int,
        curiosity: Int,
        retryIntent: Int
    ) {
        simulateLatency()
        checkErrorState()

        completedCount++
        completedExperimentIds.add(experimentId)

        val exp = allExperiments.firstOrNull { it.id == experimentId }
        if (exp != null) {
            val existingSignal = signalsList.find { it.signal == exp.actionType }
            if (existingSignal != null) {
                val index = signalsList.indexOf(existingSignal)
                val newTrend = if (enjoyment >= 4 || curiosity >= 4) SignalTrend.GROWING else SignalTrend.NEUTRAL
                signalsList[index] = existingSignal.copy(
                    trend = newTrend,
                    count = existingSignal.count + 1
                )
            } else {
                val newTrend = if (enjoyment >= 4 || curiosity >= 4) SignalTrend.GROWING else SignalTrend.NEUTRAL
                signalsList.add(SignalUiModel(exp.actionType, newTrend, count = 1))
            }

            // 仮説と観察の更新
            currentObservation = "「${exp.actionType.japaneseLabel}」アクティビティにおいて、高い集中や意欲が見られました。"
            currentHypothesis = "「${exp.actionType.japaneseLabel}」ことや、構造を工夫することに自然と惹かれる傾向があります。"
        }
    }

    override suspend fun skipExperiment(experimentId: String) {
        simulateLatency()
        cycleNextExperiment()
    }

    override suspend fun getDiscovery(): DiscoveryData {
        simulateLatency()
        checkErrorState()

        val nextIndex = (currentExperimentIndex + 1) % allExperiments.size
        val nextExp = allExperiments[nextIndex]

        return if (scenario == FakeScenario.EMPTY_DISCOVERY) {
            DiscoveryData(
                observation = "まだ実験のデータが少ないため、傾向を分析中です。",
                hypothesis = "あと1〜2個の実験を試すと、行動の特徴が見えてきます。",
                testingFocus = "まずは幅広い分野の実験で興味の反応を観察中",
                recentChanges = "実験を開始したばかりです",
                evidenceReason = "まだ十分な回答データがありません",
                disclaimer = "※ これは現時点の行動から導き出した仮説です。",
                nextExperiment = nextExp
            )
        } else {
            DiscoveryData(
                observation = currentObservation,
                hypothesis = currentHypothesis,
                hypothesisId = 1,
                testingFocus = "実際に手を動かして作る（CREATE）ことにも興味が広がるか観察中",
                recentChanges = "先週と比べて「比べる」「分析する」シグナルが急上昇しています",
                evidenceReason = "直近3回の実験で高評価（4〜5点）を付け、予定時間より長く取り組んだため",
                disclaimer = "※ これは現時点の行動から導き出した仮説です。",
                nextExperiment = nextExp,
                criteria = fakeCriteria.toList()
            )
        }
    }

    override suspend fun sendHypothesisFeedback(
        hypothesisId: Int,
        reaction: HypothesisReaction
    ): HypothesisFeedbackOutcome {
        simulateLatency()
        val delta = HypothesisFeedbackPolicy.deltaFor(reaction)
        fakeHypothesisConfidence = (fakeHypothesisConfidence + delta).coerceIn(0f, 1f)
        var criterion: CriterionUiModel? = null
        if (reaction == HypothesisReaction.AGREE &&
            fakeHypothesisConfidence >= HypothesisFeedbackPolicy.CRITERION_PROMOTION_THRESHOLD
        ) {
            val label = currentHypothesis
            val existingIndex = fakeCriteria.indexOfFirst { it.label == label }
            val confidenceLabel = HypothesisFeedbackPolicy.confidenceLabelFor(fakeHypothesisConfidence)
            val criterionId = if (existingIndex >= 0) fakeCriteria[existingIndex].id else fakeCriteria.size + 1
            criterion = CriterionUiModel(criterionId, label, fakeHypothesisConfidence, confidenceLabel)
            if (existingIndex >= 0) fakeCriteria[existingIndex] = criterion else fakeCriteria.add(criterion)
        }
        return HypothesisFeedbackOutcome(
            hypothesisSummary = currentHypothesis,
            hypothesisConfidence = fakeHypothesisConfidence,
            criterion = criterion
        )
    }

    override suspend fun getNextExperiment(): Experiment {
        simulateLatency()
        checkErrorState()
        val nextIndex = (currentExperimentIndex + 1) % allExperiments.size
        return allExperiments[nextIndex]
    }

    override suspend fun getDomainFields(): List<DomainField> {
        simulateLatency()
        checkErrorState()
        return domainFields
    }

    override suspend fun getWeeklyNarrative(): WeeklyNarrative {
        simulateLatency()
        checkErrorState()
        return WeeklyNarrative(
            weeklyInsights = WEEKLY_INSIGHTS,
            changeFromPast = CHANGE_FROM_PAST
        )
    }

    override suspend fun getReportData(): ReportData {
        simulateLatency()
        checkErrorState()
        val narrative = getWeeklyNarrative()
        return ReportData(
            totalCompletedCount = completedCount,
            totalMinutesSpent = completedCount * 7,
            topSignal = BehaviorSignal.ANALYZE,
            weeklyInsights = narrative.weeklyInsights,
            changeFromPast = narrative.changeFromPast,
            signalDistribution = mapOf(
                "分析する" to 4,
                "比べる" to 3,
                "つくる" to 2,
                "整理する" to 1
            )
        )
    }

    override suspend fun getSettings(): MyDataSettings {
        simulateLatency()
        return settingsData
    }

    override suspend fun updateSettings(settings: MyDataSettings) {
        simulateLatency()
        this.settingsData = settings
    }

    override suspend fun resetAllData() {
        simulateLatency()
        completedCount = 0
        signalsList.clear()
        currentExperimentIndex = 0
        currentHypothesis = "まだ実験データがありません。"
        currentObservation = "最初の実験をやってみましょう。"
        lastCompletedOnboardingNickname = null
        lastCompletedOnboardingAgeRange = null
        lastCompletedOnboardingSchoolStage = null
        lastCompletedOnboardingOptionalInterests = null
        lastCompletedOnboardingScore = null
        onboardingCompletedCount = 0
    }

    var lastCompletedOnboardingNickname: String? = null
        private set
    var lastCompletedOnboardingAgeRange: String? = null
        private set
    var lastCompletedOnboardingSchoolStage: String? = null
        private set
    var lastCompletedOnboardingOptionalInterests: List<String>? = null
        private set
    var lastCompletedOnboardingScore: Float? = null
        private set
    var onboardingCompletedCount: Int = 0
        private set

    override suspend fun completeOnboarding(
        nickname: String?,
        ageRange: String?,
        schoolStage: String?,
        optionalInterests: List<String>,
        initialSelfUnderstandingScore: Float
    ) {
        simulateLatency()
        checkErrorState()
        lastCompletedOnboardingNickname = nickname
        lastCompletedOnboardingAgeRange = ageRange
        lastCompletedOnboardingSchoolStage = schoolStage
        lastCompletedOnboardingOptionalInterests = optionalInterests
        lastCompletedOnboardingScore = initialSelfUnderstandingScore
        onboardingCompletedCount++
    }
}
