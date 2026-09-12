package com.mikke.discovery.shared.ui.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OnboardingBasicInfoStateTest {

    @Test
    fun initialState_isEmpty() {
        val state = OnboardingBasicInfoState(onNext = {})

        assertEquals("", state.info.nickname)
        assertEquals("", state.info.ageRange)
        assertEquals("", state.info.schoolStage)
        assertEquals(emptyList<String>(), state.info.optionalInterests)
    }

    @Test
    fun updateNickname_changesNickname() {
        val state = OnboardingBasicInfoState(onNext = {})

        state.updateNickname("タロウ")

        assertEquals("タロウ", state.info.nickname)
    }

    @Test
    fun updateAgeRange_changesAgeRange() {
        val state = OnboardingBasicInfoState(onNext = {})

        state.updateAgeRange("13-15")

        assertEquals("13-15", state.info.ageRange)
    }

    @Test
    fun updateSchoolStage_changesSchoolStage() {
        val state = OnboardingBasicInfoState(onNext = {})

        state.updateSchoolStage("中学3年")

        assertEquals("中学3年", state.info.schoolStage)
    }

    @Test
    fun toggleInterest_addsAndRemovesInterest() {
        val state = OnboardingBasicInfoState(onNext = {})

        state.toggleInterest("音楽")
        assertEquals(listOf("音楽"), state.info.optionalInterests)

        state.toggleInterest("スポーツ")
        assertEquals(listOf("音楽", "スポーツ"), state.info.optionalInterests)

        state.toggleInterest("音楽")
        assertEquals(listOf("スポーツ"), state.info.optionalInterests)
    }

    @Test
    fun submit_callsOnNextWithCurrentInfo() {
        var submitted: OnboardingBasicInfo? = null
        val state = OnboardingBasicInfoState(
            initial = OnboardingBasicInfo(
                nickname = "ハナコ",
                ageRange = "16-18",
                schoolStage = "高校2年",
                optionalInterests = listOf("アート")
            ),
            onNext = { submitted = it }
        )

        state.submit()

        assertEquals("ハナコ", submitted?.nickname)
        assertEquals("16-18", submitted?.ageRange)
        assertEquals("高校2年", submitted?.schoolStage)
        assertEquals(listOf("アート"), submitted?.optionalInterests)
    }

    @Test
    fun submit_withEmptyInfo_callsOnNext() {
        var called = false
        val state = OnboardingBasicInfoState(onNext = { called = true })

        state.submit()

        assertTrue(called)
    }
}
