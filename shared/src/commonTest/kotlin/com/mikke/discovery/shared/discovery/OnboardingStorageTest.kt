package com.mikke.discovery.shared.discovery

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingStorageTest {

    @Test
    fun freshStorage_hasNotCompletedOnboarding() {
        val storage = InMemoryOnboardingStorage()

        assertFalse(storage.hasCompletedOnboarding())
    }

    @Test
    fun markCompleted_onboardingIsCompleted() {
        val storage = InMemoryOnboardingStorage()

        storage.markCompleted()

        assertTrue(storage.hasCompletedOnboarding())
    }
}
