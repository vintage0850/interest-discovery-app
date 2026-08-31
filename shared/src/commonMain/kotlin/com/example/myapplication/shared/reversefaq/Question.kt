package com.example.myapplication.shared.reversefaq

/**
 * AIが提示する「契約前に確認すべき質問」。
 */
data class Question(
    val id: Long = 0,
    val caseId: Long,
    val title: String,
    val reason: String,
    val riskLevel: RiskLevel,
    val sourceText: String? = null,
    val sourcePage: Int? = null,
    val status: QuestionStatus = QuestionStatus.UNCONFIRMED
)
