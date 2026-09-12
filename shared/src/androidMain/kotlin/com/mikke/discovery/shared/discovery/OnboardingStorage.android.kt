package com.mikke.discovery.shared.discovery

import android.content.Context
import android.content.SharedPreferences

private const val PREFS_NAME = "onboarding"
private const val KEY_COMPLETED = "completed"

/** [OnboardingStorage] を端末の[SharedPreferences]へ永続化する。 */
class AndroidOnboardingStorage(private val prefs: SharedPreferences) : OnboardingStorage {

    override fun hasCompletedOnboarding(): Boolean {
        return prefs.getBoolean(KEY_COMPLETED, false)
    }

    override fun markCompleted() {
        prefs.edit()
            .putBoolean(KEY_COMPLETED, true)
            .apply()
    }

    companion object {
        fun get(context: Context): AndroidOnboardingStorage =
            AndroidOnboardingStorage(
                context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            )
    }
}
