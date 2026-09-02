package com.example.myapplication.shared.discovery

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FakeDiscoveryRepositoryTest {

    @Test
    fun getHomeState_normalScenario_returnsExpectedInitialData() = runTest {
        val repo = FakeDiscoveryRepository(FakeScenario.NORMAL, enableArtificialDelay = false)
        val home = repo.getHomeState()

        assertEquals("こんにちは", home.greetingTitle)
        assertNotNull(home.featuredExperiment)
        assertEquals("好きなゲームのUIを観察する", home.featuredExperiment?.title)
        assertEquals(3, home.todayExperiments.size)
        assertEquals(2, home.completedThisWeek)
        assertTrue(home.signals.isNotEmpty())
        assertNotNull(home.discoveryInsight)
    }

    @Test
    fun getDomainFields_returnsNonEmptyList() = runTest {
        val repo = FakeDiscoveryRepository(FakeScenario.NORMAL, enableArtificialDelay = false)
        val fields = repo.getDomainFields()

        assertTrue(fields.size >= 5)
        val design = fields.find { it.id == "design_ui" }
        assertNotNull(design)
        assertEquals(ExploreStatus.TRIED, design.status)
    }

    @Test
    fun getReportData_returnsValidStats() = runTest {
        val repo = FakeDiscoveryRepository(FakeScenario.NORMAL, enableArtificialDelay = false)
        val report = repo.getReportData()

        assertEquals(2, report.totalCompletedCount)
        assertEquals(14, report.totalMinutesSpent)
        assertTrue(report.weeklyInsights.isNotEmpty())
    }

    @Test
    fun resetAllData_clearsCountAndSignals() = runTest {
        val repo = FakeDiscoveryRepository(FakeScenario.NORMAL, enableArtificialDelay = false)
        repo.resetAllData()

        val home = repo.getHomeState()
        assertEquals(0, home.completedThisWeek)
        assertTrue(home.signals.isEmpty())
    }
}
