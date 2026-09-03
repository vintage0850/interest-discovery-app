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
    fun completeOnboarding_recordsArgumentsAndIncrementsCount() = runTest {
        val repo = FakeDiscoveryRepository(FakeScenario.NORMAL, enableArtificialDelay = false)
        repo.completeOnboarding(
            nickname = "Hanako",
            ageRange = "13〜15歳",
            schoolStage = "中学",
            optionalInterests = listOf("art", "music"),
            initialSelfUnderstandingScore = 4.0f
        )

        assertEquals("Hanako", repo.lastCompletedOnboardingNickname)
        assertEquals("13〜15歳", repo.lastCompletedOnboardingAgeRange)
        assertEquals("中学", repo.lastCompletedOnboardingSchoolStage)
        assertEquals(listOf("art", "music"), repo.lastCompletedOnboardingOptionalInterests)
        assertEquals(4.0f, repo.lastCompletedOnboardingScore)
        assertEquals(1, repo.onboardingCompletedCount)
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
