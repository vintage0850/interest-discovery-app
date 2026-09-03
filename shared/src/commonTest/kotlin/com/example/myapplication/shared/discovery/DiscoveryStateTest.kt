package com.example.myapplication.shared.discovery

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoveryStateTest {

    private fun TestScope.createState(repository: DiscoveryRepository) = DiscoveryState(
        repository = repository,
        coroutineScope = this,
        useSupervisorJob = false,
        autoLoad = false
    )

    @Test
    fun initialState_loadsHomeDataSuccessfully() = runTest {
        val repo = FakeDiscoveryRepository(FakeScenario.NORMAL, enableArtificialDelay = false)
        val state = createState(repo)
        try {
            state.loadHomeData()
            advanceUntilIdle()

            val home = state.homeState.value
            assertFalse(home.isLoading)
            assertNotNull(home.homeData)
            assertEquals("こんにちは", home.homeData?.greetingTitle)
            assertEquals(3, home.homeData?.todayExperiments?.size)
        } finally {
            state.close()
        }
    }

    @Test
    fun selectTab_updatesCurrentTabAndLoadsData() = runTest {
        val repo = FakeDiscoveryRepository(FakeScenario.NORMAL, enableArtificialDelay = false)
        val state = createState(repo)
        try {
            assertEquals(AppTab.HOME, state.currentTab.value)

            state.selectTab(AppTab.DISCOVER)
            advanceUntilIdle()
            assertEquals(AppTab.DISCOVER, state.currentTab.value)
            assertNotNull(state.discoveryState.value.discoveryData)

            state.selectTab(AppTab.EXPLORE)
            advanceUntilIdle()
            assertEquals(AppTab.EXPLORE, state.currentTab.value)
            assertTrue(state.exploreState.value.fields.isNotEmpty())

            state.selectTab(AppTab.REPORT)
            advanceUntilIdle()
            assertEquals(AppTab.REPORT, state.currentTab.value)
            assertNotNull(state.reportState.value.reportData)

            state.selectTab(AppTab.SETTINGS)
            advanceUntilIdle()
            assertEquals(AppTab.SETTINGS, state.currentTab.value)
        } finally {
            state.close()
        }
    }

    @Test
    fun reflectionSubmission_updatesRatingsAndCompletes() = runTest {
        val repo = FakeDiscoveryRepository(FakeScenario.NORMAL, enableArtificialDelay = false)
        val state = createState(repo)
        try {
            state.loadHomeData()
            advanceUntilIdle()

            state.finishExperiment()
            state.setReflectionEnjoyment(5)
            state.setReflectionCuriosity(4)
            state.setReflectionRetryIntent(5)

            val reflection = state.reflectionState.value
            assertTrue(reflection.isValid)

            var completedCallbackFired = false
            state.submitReflection {
                completedCallbackFired = true
            }
            advanceUntilIdle()

            assertTrue(completedCallbackFired)
            val home = state.homeState.value
            assertEquals(3, home.homeData?.completedThisWeek) // 2 + 1
        } finally {
            state.close()
        }
    }

    @Test
    fun resetData_resetsAllProgress() = runTest {
        val repo = FakeDiscoveryRepository(FakeScenario.NORMAL, enableArtificialDelay = false)
        val state = createState(repo)
        try {
            state.loadHomeData()
            advanceUntilIdle()

            var resetCallbackFired = false
            state.resetAllData {
                resetCallbackFired = true
            }
            advanceUntilIdle()

            assertTrue(resetCallbackFired)
            val home = state.homeState.value
            assertEquals(0, home.homeData?.completedThisWeek)
        } finally {
            state.close()
        }
    }

    @Test
    fun selectExperiment_keepsSelectedExperimentForNonFirstCandidate() = runTest {
        val repo = FakeDiscoveryRepository(FakeScenario.NORMAL, enableArtificialDelay = false)
        val state = createState(repo)
        try {
            val candidates = repo.getSuggestedExperiments()
            val target = candidates.first { it.id == "exp-2" }
            var navigated = false

            state.selectExperiment(target) { navigated = true }
            advanceUntilIdle()

            assertEquals(target, state.selectedExperiment.value)
            assertEquals(target.id, state.selectedExperiment.value?.id)
            assertTrue(navigated)
        } finally {
            state.close()
        }
    }

    @Test
    fun selectExperiment_doesNotNavigateWhenSelectFails() = runTest {
        val repo = FakeDiscoveryRepository(FakeScenario.NORMAL, enableArtificialDelay = false)
        val state = createState(FailingOnSelectRepository(repo))
        try {
            var navigated = false
            var message: String? = null
            val collectJob = launch { state.messages.collect { message = it } }
            val target = Experiment(
                id = "exp-2",
                title = "t",
                description = "d",
                plannedMinutes = 5,
                actionType = BehaviorSignal.ANALYZE
            )

            state.selectExperiment(target) { navigated = true }
            advanceUntilIdle()

            assertFalse(navigated)
            assertEquals("select failed", message)
            collectJob.cancel()
        } finally {
            state.close()
        }
    }

    @Test
    fun startExperiment_startsSelectedExperimentAndNavigatesOnSuccess() = runTest {
        val repo = FakeDiscoveryRepository(FakeScenario.NORMAL, enableArtificialDelay = false)
        val state = createState(repo)
        try {
            val target = repo.getSuggestedExperiments().first { it.id == "exp-2" }
            var navigated = false

            state.selectExperiment(target) { }
            runCurrent()
            state.startExperiment { navigated = true }
            runCurrent()

            assertEquals(target.id, state.runningState.value.experiment?.id)
            assertTrue(state.runningState.value.isRunning)
            assertTrue(navigated)

            state.finishExperiment()
            runCurrent()
        } finally {
            state.close()
        }
    }

    @Test
    fun startExperiment_doesNotNavigateOrStartTimerWhenStartFails() = runTest {
        val repo = FakeDiscoveryRepository(FakeScenario.NORMAL, enableArtificialDelay = false)
        val state = createState(FailingOnStartRepository(repo))
        try {
            val target = repo.getSuggestedExperiments().first { it.id == "exp-2" }
            var navigated = false
            var message: String? = null
            val collectJob = launch { state.messages.collect { message = it } }

            state.selectExperiment(target) { }
            advanceUntilIdle()
            state.startExperiment { navigated = true }
            advanceUntilIdle()

            assertFalse(navigated)
            assertFalse(state.runningState.value.isRunning)
            assertEquals("start failed", message)
            collectJob.cancel()
        } finally {
            state.close()
        }
    }

    @Test
    fun startExperiment_doesNothingWhenNoExperimentSelected() = runTest {
        val repo = FakeDiscoveryRepository(FakeScenario.NORMAL, enableArtificialDelay = false)
        val state = createState(repo)
        try {
            var navigated = false

            state.startExperiment { navigated = true }
            advanceUntilIdle()

            assertFalse(navigated)
            assertFalse(state.runningState.value.isRunning)
            assertNull(state.runningState.value.experiment)
        } finally {
            state.close()
        }
    }

    @Test
    fun selectThenStart_callsRepositoryInOrderForSameExperiment() = runTest {
        val repo = FakeDiscoveryRepository(FakeScenario.NORMAL, enableArtificialDelay = false)
        val recording = RecordingRepository(repo)
        val state = createState(recording)
        try {
            val target = repo.getSuggestedExperiments().first { it.id == "exp-2" }

            state.selectExperiment(target) { }
            advanceUntilIdle()
            // Use runCurrent(), not advanceUntilIdle(): startExperiment's timer job
            // reschedules itself via delay(1000) forever, so advanceUntilIdle() would
            // never see the queue go empty.
            state.startExperiment { }
            runCurrent()

            assertEquals(listOf("select:exp-2", "start:exp-2"), recording.callLog)

            state.finishExperiment()
            runCurrent()
        } finally {
            state.close()
        }
    }

    @Test
    fun startExperiment_doesNotRunAheadOfAnInFlightSelect() = runTest {
        val repo = FakeDiscoveryRepository(FakeScenario.NORMAL, enableArtificialDelay = false)
        val recording = RecordingRepository(repo, selectDelayMillis = 1_000)
        val state = createState(recording)
        try {
            val target = repo.getSuggestedExperiments().first { it.id == "exp-2" }

            state.selectExperiment(target) { }
            // select's coroutine starts and suspends inside the artificial delay,
            // before repository.selectExperiment() has actually run.
            runCurrent()

            // Calling startExperiment while select is still in flight must not race ahead:
            // selectedExperiment isn't set yet, so this is a no-op.
            state.startExperiment { }
            runCurrent()

            assertTrue(recording.callLog.none { it.startsWith("start:") })
            assertFalse(state.runningState.value.isRunning)

            advanceUntilIdle() // let the delayed select finish

            // Now that selection genuinely succeeded, starting works.
            state.startExperiment { }
            runCurrent()

            assertEquals(listOf("select:exp-2", "start:exp-2"), recording.callLog)
            assertTrue(state.runningState.value.isRunning)

            state.finishExperiment()
            runCurrent()
        } finally {
            state.close()
        }
    }

    @Test
    fun sendHypothesisFeedback_agreePastThreshold_updatesHypothesisAndAddsCriterion() = runTest {
        val repo = FakeDiscoveryRepository(FakeScenario.NORMAL, enableArtificialDelay = false)
        val state = createState(repo)
        try {
            state.loadDiscovery()
            advanceUntilIdle()
            val before = state.discoveryState.value.discoveryData
            assertNotNull(before?.hypothesisId)
            assertEquals(0, before?.criteria?.size)

            var message: String? = null
            val collectJob = launch { state.messages.collect { message = it } }

            // FakeDiscoveryRepositoryのfakeHypothesisConfidenceは0.5始まり。
            // 同感+0.15で0.65 >= 0.6（昇格閾値）となりCriterionが追加される。
            state.sendHypothesisFeedback(HypothesisReaction.AGREE)
            advanceUntilIdle()

            val after = state.discoveryState.value.discoveryData
            assertEquals(1, after?.criteria?.size)
            assertEquals("あなたの基準として追加しました！", message)
            assertFalse(state.discoveryState.value.isSubmittingFeedback)
            collectJob.cancel()
        } finally {
            state.close()
        }
    }

    @Test
    fun sendHypothesisFeedback_unsure_doesNotAddCriterionOrEmitMessage() = runTest {
        val repo = FakeDiscoveryRepository(FakeScenario.NORMAL, enableArtificialDelay = false)
        val state = createState(repo)
        try {
            state.loadDiscovery()
            advanceUntilIdle()

            var message: String? = null
            val collectJob = launch { state.messages.collect { message = it } }

            state.sendHypothesisFeedback(HypothesisReaction.UNSURE)
            advanceUntilIdle()

            assertEquals(0, state.discoveryState.value.discoveryData?.criteria?.size)
            assertNull(message)
            collectJob.cancel()
        } finally {
            state.close()
        }
    }

    @Test
    fun sendHypothesisFeedback_doesNothingWhenNoHypothesisLoaded() = runTest {
        val repo = FakeDiscoveryRepository(FakeScenario.NORMAL, enableArtificialDelay = false)
        val recording = RecordingHypothesisFeedbackRepository(repo)
        val state = createState(recording)
        try {
            // loadDiscovery() を呼んでいないため discoveryData は null のまま。
            state.sendHypothesisFeedback(HypothesisReaction.AGREE)
            advanceUntilIdle()

            assertEquals(0, recording.callCount)
            assertFalse(state.discoveryState.value.isSubmittingFeedback)
        } finally {
            state.close()
        }
    }

    @Test
    fun sendHypothesisFeedback_whileSubmitting_ignoresDuplicateTaps() = runTest {
        val repo = FakeDiscoveryRepository(FakeScenario.NORMAL, enableArtificialDelay = false)
        val recording = RecordingHypothesisFeedbackRepository(repo, delayMillis = 1_000)
        val state = createState(recording)
        try {
            state.loadDiscovery()
            advanceUntilIdle()

            state.sendHypothesisFeedback(HypothesisReaction.AGREE)
            runCurrent() // 1回目が送信中（delay内）になる

            assertTrue(state.discoveryState.value.isSubmittingFeedback)

            // 送信中に連打しても2回目は無視される。
            state.sendHypothesisFeedback(HypothesisReaction.AGREE)
            state.sendHypothesisFeedback(HypothesisReaction.AGREE)
            advanceUntilIdle()

            assertEquals(1, recording.callCount)
            assertFalse(state.discoveryState.value.isSubmittingFeedback)
        } finally {
            state.close()
        }
    }

    @Test
    fun sendHypothesisFeedback_immediateBackToBackCalls_onlySubmitsOnce() = runTest {
        // runCurrent()を挟まず即座に連続呼び出しした場合の競合窓を検証する
        // （Gate4再指摘：最初のコルーチンが走り出す前はisSubmittingFeedbackがまだtrueに
        //   なっていない可能性があったため、その窓を突くケースを直接固定する）。
        val repo = FakeDiscoveryRepository(FakeScenario.NORMAL, enableArtificialDelay = false)
        val recording = RecordingHypothesisFeedbackRepository(repo, delayMillis = 1_000)
        val state = createState(recording)
        try {
            state.loadDiscovery()
            advanceUntilIdle()

            // 3回とも同一ディスパッチャtickの中で、間にrunCurrent()を挟まず連続呼び出しする。
            state.sendHypothesisFeedback(HypothesisReaction.AGREE)
            state.sendHypothesisFeedback(HypothesisReaction.AGREE)
            state.sendHypothesisFeedback(HypothesisReaction.AGREE)
            advanceUntilIdle()

            assertEquals(1, recording.callCount)
            assertFalse(state.discoveryState.value.isSubmittingFeedback)
        } finally {
            state.close()
        }
    }

    @Test
    fun sendHypothesisFeedback_onFailure_emitsMessageAndResetsSubmittingFlag() = runTest {
        val repo = FakeDiscoveryRepository(FakeScenario.NORMAL, enableArtificialDelay = false)
        val state = createState(FailingOnHypothesisFeedbackRepository(repo))
        try {
            state.loadDiscovery()
            advanceUntilIdle()

            var message: String? = null
            val collectJob = launch { state.messages.collect { message = it } }

            state.sendHypothesisFeedback(HypothesisReaction.AGREE)
            advanceUntilIdle()

            assertEquals("feedback failed", message)
            assertFalse(state.discoveryState.value.isSubmittingFeedback)
            collectJob.cancel()
        } finally {
            state.close()
        }
    }

    @Test
    fun completeOnboarding_callsRepositoryAndInvokesOnSuccess() = runTest {
        val repo = FakeDiscoveryRepository(FakeScenario.NORMAL, enableArtificialDelay = false)
        val state = createState(repo)
        try {
            var successCalled = false
            state.completeOnboarding(
                nickname = "Taro",
                ageRange = "16〜18歳",
                schoolStage = "高校",
                optionalInterests = listOf("tech"),
                initialSelfUnderstandingScore = 4.5f
            ) {
                successCalled = true
            }
            advanceUntilIdle()

            assertTrue(successCalled)
            assertEquals("Taro", repo.lastCompletedOnboardingNickname)
            assertEquals(1, repo.onboardingCompletedCount)
        } finally {
            state.close()
        }
    }

    @Test
    fun completeOnboarding_onFailure_emitsMessageAndDoesNotInvokeSuccess() = runTest {
        val repo = FakeDiscoveryRepository(FakeScenario.NORMAL, enableArtificialDelay = false)
        val state = createState(FailingOnCompleteOnboardingRepository(repo))
        try {
            var successCalled = false
            var message: String? = null
            val collectJob = launch { state.messages.collect { message = it } }

            state.completeOnboarding(
                nickname = "Taro",
                ageRange = null,
                schoolStage = null,
                optionalInterests = emptyList(),
                initialSelfUnderstandingScore = 3.0f
            ) {
                successCalled = true
            }
            advanceUntilIdle()

            assertFalse(successCalled)
            assertEquals("onboarding update failed", message)
            collectJob.cancel()
        } finally {
            state.close()
        }
    }
}

private class FailingOnCompleteOnboardingRepository(
    delegate: DiscoveryRepository
) : DiscoveryRepository by delegate {
    override suspend fun completeOnboarding(
        nickname: String?,
        ageRange: String?,
        schoolStage: String?,
        optionalInterests: List<String>,
        initialSelfUnderstandingScore: Float
    ) {
        throw DiscoveryApiException("onboarding update failed")
    }
}

private class FailingOnSelectRepository(delegate: DiscoveryRepository) : DiscoveryRepository by delegate {
    override suspend fun selectExperiment(experimentId: String) {
        throw DiscoveryApiException("select failed")
    }
}

private class FailingOnStartRepository(delegate: DiscoveryRepository) : DiscoveryRepository by delegate {
    override suspend fun startExperiment(experimentId: String) {
        throw DiscoveryApiException("start failed")
    }
}

private class FailingOnHypothesisFeedbackRepository(
    delegate: DiscoveryRepository
) : DiscoveryRepository by delegate {
    override suspend fun sendHypothesisFeedback(
        hypothesisId: Int,
        reaction: HypothesisReaction
    ): HypothesisFeedbackOutcome {
        throw DiscoveryApiException("feedback failed")
    }
}

/** 送信中(isSubmittingFeedback)の多重送信防止を検証するための、呼び出し回数記録＋遅延注入デコレータ。 */
private class RecordingHypothesisFeedbackRepository(
    private val delegate: DiscoveryRepository,
    private val delayMillis: Long = 0L
) : DiscoveryRepository by delegate {
    var callCount = 0
        private set

    override suspend fun sendHypothesisFeedback(
        hypothesisId: Int,
        reaction: HypothesisReaction
    ): HypothesisFeedbackOutcome {
        callCount++
        if (delayMillis > 0) {
            delay(delayMillis)
        }
        return delegate.sendHypothesisFeedback(hypothesisId, reaction)
    }
}

/**
 * Records the order (and, for `select`, an optional artificial delay) of
 * repository calls so tests can assert select/start execute in sequence
 * for the same experiment id, rather than racing.
 */
private class RecordingRepository(
    private val delegate: DiscoveryRepository,
    private val selectDelayMillis: Long = 0L
) : DiscoveryRepository by delegate {
    val callLog = mutableListOf<String>()

    override suspend fun selectExperiment(experimentId: String) {
        if (selectDelayMillis > 0) {
            delay(selectDelayMillis)
        }
        callLog.add("select:$experimentId")
        delegate.selectExperiment(experimentId)
    }

    override suspend fun startExperiment(experimentId: String) {
        callLog.add("start:$experimentId")
        delegate.startExperiment(experimentId)
    }
}
