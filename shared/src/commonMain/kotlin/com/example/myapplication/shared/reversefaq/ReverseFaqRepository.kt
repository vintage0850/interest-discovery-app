package com.example.myapplication.shared.reversefaq

import kotlinx.coroutines.flow.Flow

/**
 * Reverse FAQのデータ操作を抽象化するRepository。
 * Phase 1ではダミー質問を使い、Phase 2以降でAI分析結果に置き換える。
 */
interface ReverseFaqRepository {
    // DocumentCase
    suspend fun createCase(
        title: String,
        documentUri: String? = null,
        documentType: DocumentType = DocumentType.NONE,
        deadline: Long? = null
    ): Long

    fun observeAllCases(): Flow<List<DocumentCase>>
    suspend fun getCaseById(id: Long): DocumentCase?
    suspend fun updateCaseStatus(id: Long, status: CaseStatus)
    suspend fun deleteCase(id: Long)

    // UserContext
    suspend fun saveUserContext(caseId: Long, attributesJson: String)
    suspend fun getUserContext(caseId: Long): UserContext?

    // Question
    suspend fun analyzeQuestions(
        caseId: Long,
        documentText: String,
        userContextJson: String
    ): List<Question>
    fun observeQuestionsForCase(caseId: Long): Flow<List<Question>>
    suspend fun getQuestionById(id: Long): Question?
    suspend fun confirmQuestion(id: Long)
    suspend fun unconfirmQuestion(id: Long)

    // QuestionAnswer
    suspend fun saveAnswer(
        questionId: Long,
        answerText: String,
        answeredBy: String? = null,
        answeredAt: Long
    ): Long

    suspend fun getAnswerForQuestion(questionId: Long): QuestionAnswer?
}
