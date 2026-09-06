package com.example.myapplication.shared.discovery

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Lane B：FakeDiscoveryRepository の心理軸アンケート・ユーザー主導 Reflection
 * ダミー実装に対する単体テスト。
 */
class FakeDiscoveryRepositorySurveyReflectionTest {

    @Test
    fun submitPsychAxisSurvey_withAllAxisScores_returnsUiModels() = runTest {
        val repo = FakeDiscoveryRepository(FakeScenario.NORMAL, enableArtificialDelay = false)
        val scores = mapOf(
            PsychAxis.INVESTIGATE to 4.5f,
            PsychAxis.CREATE to 3.0f,
            PsychAxis.EXECUTE to 4.0f,
            PsychAxis.COMMUNICATE to 2.5f
        )

        val results = repo.submitPsychAxisSurvey(scores)

        assertEquals(4, results.size)
        val resultMap = results.associate { it.axis to it.score }
        assertEquals(scores, resultMap)
    }

    @Test
    fun submitPsychAxisSurvey_withMissingAxis_throws() = runTest {
        val repo = FakeDiscoveryRepository(FakeScenario.NORMAL, enableArtificialDelay = false)
        val scores = mapOf(
            PsychAxis.INVESTIGATE to 4.0f,
            PsychAxis.CREATE to 3.0f,
            PsychAxis.EXECUTE to 4.0f
        )

        assertFailsWith<IllegalArgumentException> {
            repo.submitPsychAxisSurvey(scores)
        }
    }

    @Test
    fun submitPsychAxisSurvey_withOutOfRangeScore_throws() = runTest {
        val repo = FakeDiscoveryRepository(FakeScenario.NORMAL, enableArtificialDelay = false)

        assertFailsWith<IllegalArgumentException> {
            repo.submitPsychAxisSurvey(
                mapOf(
                    PsychAxis.INVESTIGATE to 0.5f,
                    PsychAxis.CREATE to 3.0f,
                    PsychAxis.EXECUTE to 4.0f,
                    PsychAxis.COMMUNICATE to 3.0f
                )
            )
        }

        assertFailsWith<IllegalArgumentException> {
            repo.submitPsychAxisSurvey(
                mapOf(
                    PsychAxis.INVESTIGATE to 5.5f,
                    PsychAxis.CREATE to 3.0f,
                    PsychAxis.EXECUTE to 4.0f,
                    PsychAxis.COMMUNICATE to 3.0f
                )
            )
        }
    }

    @Test
    fun addReflection_andGetReflections_returnsNewestFirst() = runTest {
        val repo = FakeDiscoveryRepository(FakeScenario.NORMAL, enableArtificialDelay = false)

        repo.addReflection("今日は観察が楽しかった", mood = 4)
        repo.addReflection("もっと調べてみたい", mood = 5)

        val reflections = repo.getReflections()

        assertEquals(2, reflections.size)
        assertEquals("もっと調べてみたい", reflections[0].content)
        assertEquals(5, reflections[0].mood)
        assertEquals("今日は観察が楽しかった", reflections[1].content)
        assertEquals(4, reflections[1].mood)
    }

    @Test
    fun addReflection_withoutMood_allowsNull() = runTest {
        val repo = FakeDiscoveryRepository(FakeScenario.NORMAL, enableArtificialDelay = false)

        repo.addReflection("特に気分はなかった", mood = null)

        val reflections = repo.getReflections()
        assertEquals(1, reflections.size)
        assertEquals("特に気分はなかった", reflections[0].content)
        assertEquals(null, reflections[0].mood)
    }

    @Test
    fun getReflections_initially_returnsEmptyList() = runTest {
        val repo = FakeDiscoveryRepository(FakeScenario.NORMAL, enableArtificialDelay = false)

        assertTrue(repo.getReflections().isEmpty())
    }
}
