package com.example.myapplication.shared.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Step 2「基本ユーザー情報」で収集する入力値。
 * すべて省略可能（設計書§7「Avoid excessive personal information」に従う）。
 */
data class OnboardingBasicInfo(
    val nickname: String = "",
    val ageRange: String = "",
    val schoolStage: String = "",
    val optionalInterests: List<String> = emptyList()
)

/**
 * Step 2の入力状態を保持し、画面からの更新を中継する。
 * コールバックは「次へ」ボタン押下時に1回だけ呼ばれる。
 */
class OnboardingBasicInfoState(
    initial: OnboardingBasicInfo = OnboardingBasicInfo(),
    private val onNext: (OnboardingBasicInfo) -> Unit
) {
    var info by mutableStateOf(initial)
        private set

    fun updateNickname(nickname: String) {
        info = info.copy(nickname = nickname)
    }

    fun updateAgeRange(ageRange: String) {
        info = info.copy(ageRange = ageRange)
    }

    fun updateSchoolStage(schoolStage: String) {
        info = info.copy(schoolStage = schoolStage)
    }

    fun toggleInterest(interest: String) {
        val current = info.optionalInterests
        info = info.copy(
            optionalInterests = if (interest in current) {
                current - interest
            } else {
                current + interest
            }
        )
    }

    fun submit() {
        onNext(info)
    }
}
