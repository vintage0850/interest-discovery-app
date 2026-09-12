package com.mikke.discovery.shared.ui.survey

import com.mikke.discovery.shared.discovery.PsychAxis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PsychAxisQuestionTest {

    @Test
    fun questionsCount_isEight() {
        assertEquals(8, PSYCH_AXIS_QUESTIONS.size)
    }

    @Test
    fun eachAxis_hasExactlyTwoQuestions() {
        val countPerAxis = PSYCH_AXIS_QUESTIONS.groupBy { it.axis }.mapValues { it.value.size }

        assertEquals(4, countPerAxis.size)
        PsychAxis.entries.forEach { axis ->
            assertEquals(2, countPerAxis[axis], "${axis.name} should have exactly 2 questions")
        }
    }

    @Test
    fun questionIds_areOneToEightUnique() {
        val ids = PSYCH_AXIS_QUESTIONS.map { it.id }
        assertEquals(8, ids.toSet().size)
        assertEquals((1..8).toList(), ids.sorted())
    }

    @Test
    fun calculatePsychAxisScores_allFive_returnsFiveForEachAxis() {
        val ratings = (1..8).associateWith { 5 }
        val scores = calculatePsychAxisScores(ratings)

        assertEquals(4, scores.size)
        PsychAxis.entries.forEach { axis ->
            assertEquals(5.0f, scores[axis] ?: 0f, 0.001f)
        }
    }

    @Test
    fun calculatePsychAxisScores_mixedRatings_calculatesAveragePerAxis() {
        // Q1: INVESTIGATE = 4, Q5: INVESTIGATE = 2 -> 平均 3.0
        // Q2: CREATE = 5, Q6: CREATE = 3 -> 平均 4.0
        // Q3: EXECUTE = 1, Q7: EXECUTE = 3 -> 平均 2.0
        // Q4: COMMUNICATE = 4, Q8: COMMUNICATE = 5 -> 平均 4.5
        val ratings = mapOf(
            1 to 4,
            2 to 5,
            3 to 1,
            4 to 4,
            5 to 2,
            6 to 3,
            7 to 3,
            8 to 5
        )
        val scores = calculatePsychAxisScores(ratings)

        assertEquals(3.0f, scores[PsychAxis.INVESTIGATE] ?: 0f, 0.001f)
        assertEquals(4.0f, scores[PsychAxis.CREATE] ?: 0f, 0.001f)
        assertEquals(2.0f, scores[PsychAxis.EXECUTE] ?: 0f, 0.001f)
        assertEquals(4.5f, scores[PsychAxis.COMMUNICATE] ?: 0f, 0.001f)
    }

    @Test
    fun formatScore_formatsCorrectly() {
        assertEquals("4.5", formatScore(4.5f))
        assertEquals("5.0", formatScore(5.0f))
        assertEquals("1.0", formatScore(1.0f))
        assertEquals("3.3", formatScore(3.333f))
        assertEquals("0.0", formatScore(0.0f))
    }
}
