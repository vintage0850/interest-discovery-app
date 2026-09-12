package com.mikke.discovery.shared.ui.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnboardingSelfCheckStateTest {

    @Test
    fun initialState_hasNoRatingsAndIsIncomplete() {
        val state = OnboardingSelfCheckState(onComplete = {})

        assertEquals(List(5) { null }, state.ratings)
        assertFalse(state.isComplete)
        assertNull(state.averageScore)
    }

    @Test
    fun setRating_updatesRatingAtIndex() {
        val state = OnboardingSelfCheckState(onComplete = {})

        state.setRating(0, 3)

        assertEquals(3, state.ratings[0])
    }

    @Test
    fun isComplete_trueOnlyWhenAllRatingsSet() {
        val state = OnboardingSelfCheckState(onComplete = {})

        repeat(4) { index ->
            state.setRating(index, 1)
            assertFalse(state.isComplete)
        }

        state.setRating(4, 5)
        assertTrue(state.isComplete)
    }

    @Test
    fun averageScore_computesMeanOfAllRatings() {
        val state = OnboardingSelfCheckState(onComplete = {})

        state.setRating(0, 1)
        state.setRating(1, 2)
        state.setRating(2, 3)
        state.setRating(3, 4)
        state.setRating(4, 5)

        assertEquals(3.0f, state.averageScore)
    }

    @Test
    fun submit_whenComplete_callsOnCompleteWithAverage() {
        var completedScore: Float? = null
        val state = OnboardingSelfCheckState(onComplete = { completedScore = it })

        state.setRating(0, 2)
        state.setRating(1, 3)
        state.setRating(2, 4)
        state.setRating(3, 3)
        state.setRating(4, 2)
        state.submit()

        assertEquals(2.8f, completedScore)
    }

    @Test
    fun submit_whenIncomplete_doesNotCallOnComplete() {
        var called = false
        val state = OnboardingSelfCheckState(onComplete = { called = true })

        state.setRating(0, 5)
        state.submit()

        assertFalse(called)
    }
}
