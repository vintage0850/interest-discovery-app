package com.example.myapplication.shared.discovery

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * ホーム画面のUIステート
 */
data class HomeUiState(
    val isLoading: Boolean = false,
    val isCycling: Boolean = false,
    val homeData: HomeData? = null,
    val errorMessage: String? = null,
    val activeScenario: FakeScenario = FakeScenario.NORMAL
)

/**
 * 実験実行中（タイマー）のUIステート
 */
data class RunningTimerUiState(
    val experiment: Experiment? = null,
    val elapsedSeconds: Int = 0,
    val isRunning: Boolean = false
)

/**
 * 振り返り（Reflection）のUIステート
 */
data class ReflectionUiState(
    val experimentId: String = "",
    val enjoymentRating: Int = 0,
    val curiosityRating: Int = 0,
    val retryIntentRating: Int = 0,
    val isSubmitting: Boolean = false,
    val isCompleted: Boolean = false
) {
    val isValid: Boolean
        get() = enjoymentRating in 1..5 && curiosityRating in 1..5 && retryIntentRating in 1..5
}

/**
 * 発見（Discover）画面のUIステート
 */
data class DiscoveryUiState(
    val isLoading: Boolean = false,
    val discoveryData: DiscoveryData? = null,
    val errorMessage: String? = null,
    val isEvidenceExpanded: Boolean = false,
    val isSubmittingFeedback: Boolean = false
)

/**
 * 探索（Explore）画面のUIステート
 */
data class ExploreUiState(
    val isLoading: Boolean = false,
    val fields: List<DomainField> = emptyList(),
    val selectedFilter: ExploreStatus? = null,
    val errorMessage: String? = null
)

/**
 * レポート（Report）画面のUIステート
 */
data class ReportUiState(
    val isLoading: Boolean = false,
    val reportData: ReportData? = null,
    val errorMessage: String? = null
)

/**
 * 設定（Settings）画面のUIステート
 */
data class SettingsUiState(
    val isLoading: Boolean = false,
    val settings: MyDataSettings = MyDataSettings(),
    val isResetting: Boolean = false
)

/** Reflection 一覧画面の UI ステート。 */
data class ReflectionListUiState(
    val isLoading: Boolean = false,
    val reflections: List<ReflectionUiModel> = emptyList(),
    val errorMessage: String? = null
)

/** セッション一覧画面の UI ステート。 */
data class SessionListUiState(
    val isLoading: Boolean = false,
    val sessions: List<SessionSummaryItem> = emptyList(),
    val errorMessage: String? = null
)

/** 心理4軸アンケート画面の UI ステート。 */
data class PsychAxisSurveyUiState(
    val isSubmitting: Boolean = false,
    val submitted: Boolean = false,
    val errorMessage: String? = null
)

/** Evidence 一覧画面の UI ステート。 */
data class EvidenceListUiState(
    val isLoading: Boolean = false,
    val evidences: List<EvidenceUiModel> = emptyList(),
    val errorMessage: String? = null
)


/**
 * Discovery 機能全体の ViewModel / State Holder。
 */
class DiscoveryState(
    val repository: DiscoveryRepository,
    private val coroutineScope: CoroutineScope,
    useSupervisorJob: Boolean = true,
    autoLoad: Boolean = true
) {
    private val stateJob = SupervisorJob(parent = coroutineScope.coroutineContext[Job])
    private val scope = if (useSupervisorJob) {
        CoroutineScope(coroutineScope.coroutineContext + stateJob)
    } else {
        coroutineScope
    }

    // タブ管理
    private val _currentTab = MutableStateFlow(AppTab.HOME)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    private val _homeState = MutableStateFlow(HomeUiState(isLoading = autoLoad))
    val homeState: StateFlow<HomeUiState> = _homeState.asStateFlow()

    private val _runningState = MutableStateFlow(RunningTimerUiState())
    val runningState: StateFlow<RunningTimerUiState> = _runningState.asStateFlow()

    private val _reflectionState = MutableStateFlow(ReflectionUiState())
    val reflectionState: StateFlow<ReflectionUiState> = _reflectionState.asStateFlow()

    private val _discoveryState = MutableStateFlow(DiscoveryUiState())
    val discoveryState: StateFlow<DiscoveryUiState> = _discoveryState.asStateFlow()

    private val _exploreState = MutableStateFlow(ExploreUiState())
    val exploreState: StateFlow<ExploreUiState> = _exploreState.asStateFlow()

    private val _reportState = MutableStateFlow(ReportUiState())
    val reportState: StateFlow<ReportUiState> = _reportState.asStateFlow()

    private val _settingsState = MutableStateFlow(SettingsUiState())
    val settingsState: StateFlow<SettingsUiState> = _settingsState.asStateFlow()

    private val _reflectionListState = MutableStateFlow(ReflectionListUiState())
    val reflectionListState: StateFlow<ReflectionListUiState> = _reflectionListState.asStateFlow()

    private val _sessionListState = MutableStateFlow(SessionListUiState())
    val sessionListState: StateFlow<SessionListUiState> = _sessionListState.asStateFlow()

    private val _psychAxisSurveyState = MutableStateFlow(PsychAxisSurveyUiState())
    val psychAxisSurveyState: StateFlow<PsychAxisSurveyUiState> = _psychAxisSurveyState.asStateFlow()

    private val _evidenceListState = MutableStateFlow(EvidenceListUiState())
    val evidenceListState: StateFlow<EvidenceListUiState> = _evidenceListState.asStateFlow()


    private val _selectedExperiment = MutableStateFlow<Experiment?>(null)
    val selectedExperiment: StateFlow<Experiment?> = _selectedExperiment.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val actionMutex = Mutex()
    private var timerJob: Job? = null

    init {
        if (autoLoad) {
            loadHomeData()
            loadDiscovery()
            loadExploreData()
            loadReportData()
            loadSettings()
        }
    }

    fun selectTab(tab: AppTab) {
        _currentTab.value = tab
        when (tab) {
            AppTab.HOME -> loadHomeData()
            AppTab.DISCOVER -> loadDiscovery()
            AppTab.EXPLORE -> loadExploreData()
            AppTab.REPORT -> loadReportData()
            AppTab.SETTINGS -> loadSettings()
        }
    }

    fun loadHomeData() {
        scope.launch {
            _homeState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val data = repository.getHomeState()
                _homeState.update {
                    it.copy(
                        isLoading = false,
                        homeData = data,
                        errorMessage = null,
                        activeScenario = (repository as? FakeDiscoveryRepository)
                            ?.getCurrentScenario()
                            ?: it.activeScenario
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _homeState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "今日の実験を読み込めませんでした。"
                    )
                }
            }
        }
    }

    fun cycleNextExperiment() {
        if (_homeState.value.isCycling) return
        scope.launch {
            actionMutex.withLock {
                _homeState.update { it.copy(isCycling = true) }
                try {
                    val nextExp = repository.cycleNextExperiment()
                    _homeState.update { current ->
                        val updatedData = current.homeData?.copy(featuredExperiment = nextExp)
                        current.copy(isCycling = false, homeData = updatedData)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _homeState.update { it.copy(isCycling = false) }
                    _messages.tryEmit(e.message ?: "実験の切り替えに失敗しました。")
                }
            }
        }
    }

    fun selectExperiment(experiment: Experiment, onSuccess: () -> Unit) {
        scope.launch {
            actionMutex.withLock {
                try {
                    repository.selectExperiment(experiment.id)
                    _selectedExperiment.value = experiment
                    onSuccess()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _messages.tryEmit(e.message ?: "実験の選択に失敗しました。もう一度お試しください。")
                }
            }
        }
    }

    fun completeOnboarding(
        nickname: String?,
        ageRange: String?,
        schoolStage: String?,
        optionalInterests: List<String>,
        initialSelfUnderstandingScore: Float,
        onSuccess: () -> Unit
    ) {
        scope.launch {
            actionMutex.withLock {
                try {
                    repository.completeOnboarding(
                        nickname = nickname,
                        ageRange = ageRange,
                        schoolStage = schoolStage,
                        optionalInterests = optionalInterests,
                        initialSelfUnderstandingScore = initialSelfUnderstandingScore
                    )
                    onSuccess()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _messages.tryEmit(e.message ?: "オンボーディングの完了に失敗しました。もう一度お試しください。")
                }
            }
        }
    }

    fun startExperiment(onSuccess: () -> Unit) {
        val experiment = _selectedExperiment.value ?: return
        timerJob?.cancel()
        scope.launch {
            actionMutex.withLock {
                try {
                    repository.startExperiment(experiment.id)
                    _runningState.value = RunningTimerUiState(
                        experiment = experiment,
                        elapsedSeconds = 0,
                        isRunning = true
                    )
                    timerJob = scope.launch {
                        while (isActive) {
                            delay(1000)
                            _runningState.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
                        }
                    }
                    onSuccess()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _messages.tryEmit(e.message ?: "実験の開始に失敗しました。もう一度お試しください。")
                }
            }
        }
    }

    fun finishExperiment() {
        timerJob?.cancel()
        _runningState.update { it.copy(isRunning = false) }
        val expId = _runningState.value.experiment?.id ?: ""
        _reflectionState.value = ReflectionUiState(
            experimentId = expId,
            enjoymentRating = 0,
            curiosityRating = 0,
            retryIntentRating = 0,
            isSubmitting = false,
            isCompleted = false
        )
    }

    fun setReflectionEnjoyment(rating: Int) {
        _reflectionState.update { it.copy(enjoymentRating = rating) }
    }

    fun setReflectionCuriosity(rating: Int) {
        _reflectionState.update { it.copy(curiosityRating = rating) }
    }

    fun setReflectionRetryIntent(rating: Int) {
        _reflectionState.update { it.copy(retryIntentRating = rating) }
    }

    fun submitReflection(onComplete: () -> Unit) {
        val current = _reflectionState.value
        if (!current.isValid || current.isSubmitting) return

        scope.launch {
            _reflectionState.update { it.copy(isSubmitting = true) }
            try {
                repository.completeExperiment(
                    experimentId = current.experimentId,
                    enjoyment = current.enjoymentRating,
                    curiosity = current.curiosityRating,
                    retryIntent = current.retryIntentRating
                )
                try {
                    repository.updateHypothesis()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _messages.tryEmit("実験は完了しましたが、気づきの更新に失敗しました。")
                }
                _reflectionState.update { it.copy(isSubmitting = false, isCompleted = true) }
                loadHomeData()
                loadDiscovery()
                loadReportData()
                onComplete()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _reflectionState.update { it.copy(isSubmitting = false) }
                _messages.tryEmit(e.message ?: "送信に失敗しました。もう一度お試しください。")
            }
        }
    }

    fun loadDiscovery() {
        scope.launch {
            _discoveryState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val data = repository.getDiscovery()
                _discoveryState.update {
                    it.copy(isLoading = false, discoveryData = data, errorMessage = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _discoveryState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "発見インサイトを読み込めませんでした。"
                    )
                }
            }
        }
    }

    fun toggleEvidenceExpanded() {
        _discoveryState.update { it.copy(isEvidenceExpanded = !it.isEvidenceExpanded) }
    }

    /**
     * 仮説への反応（同感/わからない/違う）を送信する。§15/§16 のフィードバックループ。
     *
     * POSTレスポンス（[HypothesisFeedbackOutcome]）だけでローカル状態を更新し、追加のGETは行わない
     * （GET失敗時にサーバー側だけ更新済みになり、再送でconfidenceが二重加算される事故を避けるため）。
     * 送信中は多重送信を防ぐため [isSubmittingFeedback] でボタンを無効化する。
     */
    fun sendHypothesisFeedback(reaction: HypothesisReaction) {
        // isSubmittingFeedback はここで同期的に立てる（scope.launch/actionMutexの中で立てると、
        // 最初のコルーチンが実際に走り出すまでの間に連続呼び出しされた場合、
        // 全呼び出しがfalseを観測してenqueueされ、confidenceが二重加算され得る。Gate4再指摘）。
        if (_discoveryState.value.isSubmittingFeedback) return
        val current = _discoveryState.value.discoveryData ?: return
        val hypothesisId = current.hypothesisId ?: return
        _discoveryState.update { it.copy(isSubmittingFeedback = true) }
        scope.launch {
            actionMutex.withLock {
                try {
                    val outcome = repository.sendHypothesisFeedback(hypothesisId, reaction)
                    val newCriterion = outcome.criterion
                    val isNewCriterion = newCriterion != null &&
                        current.criteria.none { it.id == newCriterion.id }
                    val updatedCriteria = if (newCriterion != null) {
                        (current.criteria.filterNot { it.id == newCriterion.id } + newCriterion)
                            .sortedByDescending { it.confidence }
                    } else {
                        current.criteria
                    }
                    _discoveryState.update {
                        it.copy(
                            isSubmittingFeedback = false,
                            discoveryData = current.copy(
                                hypothesis = outcome.hypothesisSummary,
                                criteria = updatedCriteria
                            )
                        )
                    }
                    if (isNewCriterion) {
                        _messages.tryEmit("あなたの基準として追加しました！")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _discoveryState.update { it.copy(isSubmittingFeedback = false) }
                    _messages.tryEmit(e.message ?: "反応の送信に失敗しました。もう一度お試しください。")
                }
            }
        }
    }

    fun loadExploreData() {
        scope.launch {
            _exploreState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val fields = repository.getDomainFields()
                _exploreState.update {
                    it.copy(isLoading = false, fields = fields, errorMessage = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _exploreState.update {
                    it.copy(isLoading = false, errorMessage = "探索分野の読み込みに失敗しました。")
                }
            }
        }
    }

    fun setExploreFilter(status: ExploreStatus?) {
        _exploreState.update { it.copy(selectedFilter = status) }
    }

    fun loadReportData() {
        scope.launch {
            _reportState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val report = repository.getReportData()
                _reportState.update {
                    it.copy(isLoading = false, reportData = report, errorMessage = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _reportState.update {
                    it.copy(isLoading = false, errorMessage = "レポートの読み込みに失敗しました。")
                }
            }
        }
    }

    fun loadSettings() {
        scope.launch {
            _settingsState.update { it.copy(isLoading = true) }
            try {
                val s = repository.getSettings()
                _settingsState.update { it.copy(isLoading = false, settings = s) }
            } catch (e: Exception) {
                _settingsState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        val updated = _settingsState.value.settings.copy(notificationsEnabled = enabled)
        _settingsState.update { it.copy(settings = updated) }
        scope.launch { repository.updateSettings(updated) }
    }

    fun resetAllData(onComplete: () -> Unit) {
        scope.launch {
            _settingsState.update { it.copy(isResetting = true) }
            repository.resetAllData()
            loadHomeData()
            loadDiscovery()
            loadReportData()
            _settingsState.update { it.copy(isResetting = false) }
            _messages.tryEmit("データを初期化しました。")
            onComplete()
        }
    }

    fun changeScenario(scenario: FakeScenario) {
        (repository as? FakeDiscoveryRepository)?.setScenario(scenario)
        loadHomeData()
        loadDiscovery()
        loadReportData()
    }

    fun loadReflections() {
        scope.launch {
            _reflectionListState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val list = repository.getReflections()
                _reflectionListState.update {
                    it.copy(isLoading = false, reflections = list, errorMessage = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _reflectionListState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "振り返りの読み込みに失敗しました。"
                    )
                }
            }
        }
    }

    fun loadEvidenceList(sessionId: Int? = null) {
        scope.launch {
            _evidenceListState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val targetSessionId = sessionId ?: (repository.getSessionList().firstOrNull()?.id ?: 1)
                val list = repository.getEvidenceList(targetSessionId)
                _evidenceListState.update {
                    it.copy(isLoading = false, evidences = list, errorMessage = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _evidenceListState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "エビデンスの読み込みに失敗しました。"
                    )
                }
            }
        }
    }


    fun addReflection(content: String, mood: Int?) {
        scope.launch {
            try {
                repository.addReflection(content, mood)
                loadReflections()
                _messages.tryEmit("振り返りを記録しました。")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit(e.message ?: "振り返りの保存に失敗しました。")
            }
        }
    }

    fun loadSessionList() {
        scope.launch {
            _sessionListState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val list = repository.getSessionList()
                _sessionListState.update {
                    it.copy(isLoading = false, sessions = list, errorMessage = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _sessionListState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "セッション一覧の読み込みに失敗しました。"
                    )
                }
            }
        }
    }

    fun switchSession(id: Int, onComplete: () -> Unit) {
        scope.launch {
            try {
                repository.switchToSession(id)
                // セッション切り替え後は各種キャッシュを再読み込みする。
                loadHomeData()
                loadDiscovery()
                loadReportData()
                loadSessionList()
                _messages.tryEmit("セッションを切り替えました。")
                onComplete()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit(e.message ?: "セッションの切り替えに失敗しました。")
            }
        }
    }

    fun submitPsychAxisSurvey(scores: Map<PsychAxis, Float>, onComplete: () -> Unit) {
        scope.launch {
            _psychAxisSurveyState.update { it.copy(isSubmitting = true, errorMessage = null) }
            try {
                repository.submitPsychAxisSurvey(scores)
                _psychAxisSurveyState.update {
                    it.copy(isSubmitting = false, submitted = true, errorMessage = null)
                }
                _messages.tryEmit("心理4軸アンケートを保存しました。")
                onComplete()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _psychAxisSurveyState.update {
                    it.copy(isSubmitting = false, errorMessage = e.message ?: "送信に失敗しました。")
                }
            }
        }
    }

    fun close() {
        timerJob?.cancel()
        stateJob.cancel()
    }
}
