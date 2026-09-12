package com.mikke.discovery.shared.reversefaq

/**
 * 質問に対する回答（質問レシートの一部）。
 */
data class QuestionAnswer(
    val id: Long = 0,
    val questionId: Long,
    val answerText: String,
    val answeredBy: String? = null,
    val answeredAt: Long
)
