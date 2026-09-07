package com.example.myapplication.shared.discovery

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 案件22：Discovery 向け Google Calendar 連携通知の KMP 共通契約テスト。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DiscoveryNotificationTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @Test
    fun notificationCandidate_preservesDomainStatusPriorityAndReason() = runTest(testDispatcher) {
        val repo = FakeDiscoveryRepository(initialScenario = FakeScenario.NORMAL, enableArtificialDelay = false)

        val candidates = repo.getNotificationCandidates()

        // Fake リポジトリは business_structure を DIVE_CANDIDATE、design_ui を TRIED、
        // tech_code を EXPLORED として返す想定。
        assertTrue(candidates.isNotEmpty(), "通知候補が空であってはならない")
        val dive = candidates.firstOrNull { it.domainStatus == NotificationCandidateDomainStatus.DIVE_CANDIDATE }
        assertTrue(dive != null, "DIVE_CANDIDATE が候補に含まれる")
        assertTrue(dive!!.reason.isNotBlank(), "DIVE_CANDIDATE には理由が必要")
        // DIVE_CANDIDATE は TRIED/EXPLORED より先頭に来る。
        assertEquals(NotificationCandidateDomainStatus.DIVE_CANDIDATE, candidates.first().domainStatus)
    }

    @Test
    fun inMemorySettingsStorage_persistsNotificationLog() = runTest(testDispatcher) {
        val storage = InMemoryDiscoverySettingsStorage()
        val log = NotificationLog(
            lastNotifiedAt = Instant.fromEpochSeconds(1_000_000),
            notifiedExperimentDates = mapOf("exp-1" to "2026-09-07")
        )

        storage.saveNotificationLog(log)
        val loaded = storage.loadNotificationLog()

        assertEquals(log, loaded)
    }

    @Test
    fun discoveryState_setGoogleCalendarLinked_updatesSettingsUiState() = runTest(testDispatcher) {
        val repo = FakeDiscoveryRepository(enableArtificialDelay = false)
        val state = DiscoveryState(repo, this, autoLoad = false)
        try {
            state.setGoogleCalendarLinked(true)
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(state.settingsState.first().googleCalendarLinked)
        } finally {
            state.close()
        }
    }

    @Test
    fun discoveryState_onNotificationTapped_validExperiment_navigatesAndSelects() = runTest(testDispatcher) {
        val repo = FakeDiscoveryRepository(enableArtificialDelay = false)
        val state = DiscoveryState(repo, this, autoLoad = false)
        var navigated = false
        val messages = mutableListOf<String>()
        val collectJob = launch { state.messages.collect { messages.add(it) } }
        try {
            state.onNotificationTapped("exp-1", onNavigate = { navigated = true })
            advanceUntilIdle()

            assertTrue(navigated)
            assertEquals("exp-1", state.selectedExperiment.first()?.id)
            assertTrue(messages.isEmpty())
        } finally {
            collectJob.cancel()
            state.close()
        }
    }

    @Test
    fun discoveryState_onNotificationTapped_unknownExperiment_doesNotNavigateAndEmitsMessage() = runTest(testDispatcher) {
        val repo = FakeWithMissingExperiment()
        val state = DiscoveryState(repo, this, autoLoad = false)
        var navigated = false
        val messages = mutableListOf<String>()
        val collectJob = launch { state.messages.collect { messages.add(it) } }
        try {
            state.onNotificationTapped("unknown-id", onNavigate = { navigated = true })
            advanceUntilIdle()

            assertFalse(navigated)
            assertEquals(null, state.selectedExperiment.first())
            assertEquals("この実験は現在開始できません。", messages.singleOrNull())
        } finally {
            collectJob.cancel()
            state.close()
        }
    }
}

/**
 * 無効な experimentId で [getExperiment] を失敗させる Fake ラッパー。
 */
private class FakeWithMissingExperiment(
    private val delegate: FakeDiscoveryRepository = FakeDiscoveryRepository(enableArtificialDelay = false)
) : DiscoveryRepository by delegate {
    override suspend fun getExperiment(experimentId: String): Experiment {
        if (experimentId == "unknown-id") throw DiscoveryApiException("not found")
        return delegate.getExperiment(experimentId)
    }
}
