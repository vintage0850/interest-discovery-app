package com.example.myapplication.shared.discovery

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PeriodicReportNotificationTest {

    private fun TestScope.createState(repository: DiscoveryRepository) = DiscoveryState(
        repository = repository,
        coroutineScope = this,
        useSupervisorJob = false,
        autoLoad = false
    )

    @Test
    fun reportType_fromValue_parsesCorrectly() {
        assertEquals(ReportType.WEEKLY, ReportType.fromValue("weekly"))
        assertEquals(ReportType.WEEKLY, ReportType.fromValue("WEEKLY"))
        assertEquals(ReportType.MONTHLY, ReportType.fromValue("monthly"))
        assertEquals(ReportType.MONTHLY, ReportType.fromValue("MONTHLY"))
        assertEquals(ReportType.MILESTONE, ReportType.fromValue("milestone"))
        assertEquals(ReportType.MILESTONE, ReportType.fromValue("MILESTONE"))
        assertNull(ReportType.fromValue("daily"))
        assertNull(ReportType.fromValue("unknown"))
        assertNull(ReportType.fromValue(null))
    }

    @Test
    fun onReportNotificationTapped_weekly_transitionsToReportTabAndFocusesWeekly() = runTest {
        val repo = FakeDiscoveryRepository(FakeScenario.NORMAL, enableArtificialDelay = false)
        val state = createState(repo)
        try {
            var navigatedHome = false
            state.onReportNotificationTapped("weekly", 1) {
                navigatedHome = true
            }
            advanceUntilIdle()

            assertTrue(navigatedHome)
            assertEquals(AppTab.REPORT, state.currentTab.value)
            assertEquals(ReportType.WEEKLY, state.focusedReportType.value)
            assertEquals(2, state.reportState.value.reportData?.totalCompletedCount)
        } finally {
            state.close()
        }
    }

    @Test
    fun onReportNotificationTapped_monthly_transitionsToReportTabAndFocusesMonthly() = runTest {
        val repo = FakeDiscoveryRepository(FakeScenario.NORMAL, enableArtificialDelay = false)
        val state = createState(repo)
        try {
            var navigatedHome = false
            state.onReportNotificationTapped("monthly", 1) {
                navigatedHome = true
            }
            advanceUntilIdle()

            assertTrue(navigatedHome)
            assertEquals(AppTab.REPORT, state.currentTab.value)
            assertEquals(ReportType.MONTHLY, state.focusedReportType.value)
            assertEquals("2026-08-02", state.reportState.value.reportData?.monthlyNarrative?.periodStart)
        } finally {
            state.close()
        }
    }

    @Test
    fun onReportNotificationTapped_milestone_transitionsToReportTabAndFocusesMilestone() = runTest {
        val repo = FakeDiscoveryRepository(FakeScenario.NORMAL, enableArtificialDelay = false)
        val state = createState(repo)
        try {
            var navigatedHome = false
            state.onReportNotificationTapped("milestone", 1) {
                navigatedHome = true
            }
            advanceUntilIdle()

            assertTrue(navigatedHome)
            assertEquals(AppTab.REPORT, state.currentTab.value)
            assertEquals(ReportType.MILESTONE, state.focusedReportType.value)
            assertEquals(2, state.reportState.value.reportData?.milestoneNarrative?.milestone)
        } finally {
            state.close()
        }
    }

    @Test
    fun onReportNotificationTapped_invalidReportType_fallsBackToHomeWithErrorMessage() = runTest {
        val repo = FakeDiscoveryRepository(FakeScenario.NORMAL, enableArtificialDelay = false)
        val state = createState(repo)
        try {
            var navigatedHome = false
            var messageReceived: String? = null
            val messageJob = launch {
                messageReceived = state.messages.first()
            }

            state.onReportNotificationTapped("invalid_type", 1) {
                navigatedHome = true
            }
            advanceUntilIdle()

            assertTrue(navigatedHome)
            assertEquals(AppTab.HOME, state.currentTab.value)
            assertNull(state.focusedReportType.value)
            assertEquals("このレポートは現在表示できません", messageReceived)
            messageJob.cancel()
        } finally {
            state.close()
        }
    }

    @Test
    fun onReportNotificationTapped_sessionMismatch_fallsBackToHomeWithErrorMessage() = runTest {
        val repo = FakeDiscoveryRepository(FakeScenario.NORMAL, enableArtificialDelay = false)
        val state = createState(repo)
        try {
            var navigatedHome = false
            var messageReceived: String? = null
            val messageJob = launch {
                messageReceived = state.messages.first()
            }

            // Notification belongs to session 999, but active is 1
            state.onReportNotificationTapped("monthly", 999) {
                navigatedHome = true
            }
            advanceUntilIdle()

            assertTrue(navigatedHome)
            assertEquals(AppTab.HOME, state.currentTab.value)
            assertNull(state.focusedReportType.value)
            assertEquals("このレポートは現在表示できません", messageReceived)
            messageJob.cancel()
        } finally {
            state.close()
        }
    }

    @Test
    fun onReportNotificationTapped_nullSessionId_fallsBackToHomeWithErrorMessage() = runTest {
        val repo = FakeDiscoveryRepository(FakeScenario.NORMAL, enableArtificialDelay = false)
        val state = createState(repo)
        try {
            var navigatedHome = false
            var messageReceived: String? = null
            val messageJob = launch {
                messageReceived = state.messages.first()
            }

            state.onReportNotificationTapped("weekly", null) {
                navigatedHome = true
            }
            advanceUntilIdle()

            assertTrue(navigatedHome)
            assertEquals(AppTab.HOME, state.currentTab.value)
            assertNull(state.focusedReportType.value)
            assertEquals("このレポートは現在表示できません", messageReceived)
            messageJob.cancel()
        } finally {
            state.close()
        }
    }

    @Test
    fun onReportNotificationTapped_sessionRestoreFailure_fallsBackToHomeWithErrorMessage() = runTest {
        val repo = object : DiscoveryRepository by FakeDiscoveryRepository(enableArtificialDelay = false) {
            override suspend fun getActiveSessionId(): Int? {
                throw IllegalStateException("session storage unavailable")
            }
        }
        val state = createState(repo)
        try {
            var navigatedHome = false
            var messageReceived: String? = null
            val messageJob = launch {
                messageReceived = state.messages.first()
            }

            state.onReportNotificationTapped("weekly", 1) {
                navigatedHome = true
            }
            advanceUntilIdle()

            assertTrue(navigatedHome)
            assertEquals(AppTab.HOME, state.currentTab.value)
            assertNull(state.focusedReportType.value)
            assertEquals("このレポートは現在表示できません", messageReceived)
            messageJob.cancel()
        } finally {
            state.close()
        }
    }
}
