package com.example.myapplication.shared.discovery

/**
 * 初回オンボーディングの完了状態を永続化する。
 * [DiscoverySettingsStorage] と同じ層に置き、プラットフォームごとに差し替え可能にする。
 */
interface OnboardingStorage {
    fun hasCompletedOnboarding(): Boolean
    fun markCompleted()
}

/** 永続化しない既定実装。テストや未対応プラットフォームで使用する。 */
class InMemoryOnboardingStorage : OnboardingStorage {
    private var completed = false

    override fun hasCompletedOnboarding(): Boolean = completed

    override fun markCompleted() {
        completed = true
    }
}
