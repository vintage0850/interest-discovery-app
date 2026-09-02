package com.example.myapplication.shared.discovery

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
}
