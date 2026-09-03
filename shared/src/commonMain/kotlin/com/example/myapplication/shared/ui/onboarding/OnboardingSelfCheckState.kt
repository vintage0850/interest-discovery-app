package com.example.myapplication.shared.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

private const val QUESTION_COUNT = 5

/**
 * Step 3「初期自己理解チェック」の回答状態を保持する。
 * 5件法（1〜5）の5問すべてに回答するまで完了とはみなさない。
 */
class OnboardingSelfCheckState(
    private val onComplete: (Float) -> Unit
) {
    private val _ratings = mutableStateListOf<Int?>().apply {
        repeat(QUESTION_COUNT) { add(null) }
    }

    /** 各質問への回答。未回答は `null`。 */
    val ratings: List<Int?> get() = _ratings.toList()

    /** すべての質問に回答済みかどうか。 */
    val isComplete: Boolean get() = _ratings.all { it != null }

    /** 回答済みの場合、5問の平均点（1.0〜5.0）。未完了時は `null`。 */
    val averageScore: Float?
        get() = _ratings
            .filterNotNull()
            .takeIf { it.size == QUESTION_COUNT }
            ?.average()
            ?.toFloat()

    /**
     * 指定インデックスの質問に対する回答を設定する。
     *
     * @param index 質問インデックス（0〜4）
     * @param rating 5件法の値（1〜5）
     */
    fun setRating(index: Int, rating: Int) {
        require(index in _ratings.indices) { "index must be in 0..${_ratings.lastIndex}" }
        _ratings[index] = rating
    }

    /**
     * すべての質問に回答済みなら [onComplete] を平均点と共に呼び出す。
     * 未完了時は何もしない。
     */
    fun submit() {
        val score = averageScore ?: return
        onComplete(score)
    }
}
