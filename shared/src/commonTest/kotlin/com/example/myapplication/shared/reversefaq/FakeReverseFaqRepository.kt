package com.example.myapplication.shared.reversefaq

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * [ReverseFaqRepository]のインメモリFake。UI/Stateの単体テスト用。
 */
class FakeReverseFaqRepository : ReverseFaqRepository {

    private val cases = MutableStateFlow<List<DocumentCase>>(emptyList())
    private val questions = MutableStateFlow<List<Question>>(emptyList())
    private val contexts = mutableMapOf<Long, UserContext>()
    private val answers = mutableMapOf<Long, QuestionAnswer>()

    private var nextCaseId = 1L
    private var nextQuestionId = 1L
    private var nextAnswerId = 1L

    override suspend fun createCase(
        title: String,
        documentUri: String?,
        documentType: DocumentType,
        deadline: Long?
    ): Long {
        val id = nextCaseId++
        cases.value += DocumentCase(
            id = id,
            title = title,
            documentUri = documentUri,
            documentType = documentType,
            createdAt = 0L,
            deadline = deadline
        )
        return id
    }

    override fun observeAllCases(): Flow<List<DocumentCase>> = cases

    override suspend fun getCaseById(id: Long): DocumentCase? = cases.value.find { it.id == id }

    override suspend fun updateCaseStatus(id: Long, status: CaseStatus) {
        cases.value = cases.value.map { if (it.id == id) it.copy(status = status) else it }
    }

    override suspend fun deleteCase(id: Long) {
        cases.value = cases.value.filter { it.id != id }
        questions.value = questions.value.filter { it.caseId != id }
        contexts.remove(id)
    }

    override suspend fun saveUserContext(caseId: Long, attributesJson: String) {
        contexts[caseId] = UserContext(caseId, attributesJson)
    }

    override suspend fun getUserContext(caseId: Long): UserContext? = contexts[caseId]

    override suspend fun analyzeQuestions(
        caseId: Long,
        documentText: String,
        userContextJson: String
    ): List<Question> {
        val newQuestions = listOf(
            Question(
                id = nextQuestionId++,
                caseId = caseId,
                title = "退去時のクリーニング費用は必ず発生しますか？",
                reason = "退去時の費用負担条件が不明確です。",
                riskLevel = RiskLevel.HIGH
            ),
            Question(
                id = nextQuestionId++,
                caseId = caseId,
                title = "更新料はいくらですか？",
                reason = "契約更新時の料金が記載されているか確認が必要です。",
                riskLevel = RiskLevel.MEDIUM
            ),
            Question(
                id = nextQuestionId++,
                caseId = caseId,
                title = "鍵交換費用の負担はどうなりますか？",
                reason = "鍵の紛失・交換時の費用負担について確認してください。",
                riskLevel = RiskLevel.LOW
            )
        )
        questions.value += newQuestions
        return newQuestions
    }

    override fun observeQuestionsForCase(caseId: Long): Flow<List<Question>> =
        questions.map { list -> list.filter { it.caseId == caseId } }

    override suspend fun getQuestionById(id: Long): Question? = questions.value.find { it.id == id }

    override suspend fun confirmQuestion(id: Long) {
        questions.value = questions.value.map { if (it.id == id) it.copy(status = QuestionStatus.CONFIRMED) else it }
    }

    override suspend fun unconfirmQuestion(id: Long) {
        questions.value = questions.value.map { if (it.id == id) it.copy(status = QuestionStatus.UNCONFIRMED) else it }
    }

    override suspend fun saveAnswer(
        questionId: Long,
        answerText: String,
        answeredBy: String?,
        answeredAt: Long
    ): Long {
        val id = nextAnswerId++
        answers[questionId] = QuestionAnswer(
            id = id,
            questionId = questionId,
            answerText = answerText,
            answeredBy = answeredBy,
            answeredAt = answeredAt
        )
        return id
    }

    override suspend fun getAnswerForQuestion(questionId: Long): QuestionAnswer? = answers[questionId]
}
