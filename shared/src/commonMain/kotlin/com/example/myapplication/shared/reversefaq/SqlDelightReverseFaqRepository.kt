package com.example.myapplication.shared.reversefaq

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.example.myapplication.shared.db.DatabaseDriverFactory
import com.example.myapplication.shared.db.SharedDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * SQLDelightを使った[ReverseFaqRepository]の実装。
 */
class SqlDelightReverseFaqRepository(
    driverFactory: DatabaseDriverFactory,
    private val apiClient: ReverseFaqApiClient = ReverseFaqApiClient()
) : ReverseFaqRepository {

    private val database = SharedDatabase(driverFactory.createDriver())
    private val queries = database.reverseFaqQueries

    override suspend fun createCase(
        title: String,
        documentUri: String?,
        documentType: DocumentType,
        deadline: Long?
    ): Long = withContext(Dispatchers.Default) {
        queries.transactionWithResult {
            queries.insertCase(
                title = title,
                document_uri = documentUri,
                document_type = documentType.name,
                created_at = currentTimeMillis(),
                deadline = deadline,
                status = CaseStatus.DRAFT.name
            )
            queries.lastInsertCaseRowId().executeAsOne()
        }
    }

    override fun observeAllCases(): Flow<List<DocumentCase>> =
        queries.selectAllCases(::toDocumentCase).asFlow().mapToList(Dispatchers.Default)

    override suspend fun getCaseById(id: Long): DocumentCase? = withContext(Dispatchers.Default) {
        queries.selectCaseById(id, ::toDocumentCase).executeAsOneOrNull()
    }

    override suspend fun updateCaseStatus(id: Long, status: CaseStatus) {
        withContext(Dispatchers.Default) {
            queries.updateCaseStatus(status.name, id)
        }
    }

    override suspend fun deleteCase(id: Long) {
        withContext(Dispatchers.Default) {
            queries.deleteCase(id)
        }
    }

    // ---- UserContext ----

    override suspend fun saveUserContext(caseId: Long, attributesJson: String) = withContext(Dispatchers.Default) {
        queries.transaction {
            val existing = queries.selectContextByCaseId(caseId).executeAsOneOrNull()
            if (existing != null) {
                queries.updateContext(case_id = caseId, attributes_json = attributesJson)
            } else {
                queries.insertContext(case_id = caseId, attributes_json = attributesJson)
            }
        }
    }

    override suspend fun getUserContext(caseId: Long): UserContext? = withContext(Dispatchers.Default) {
        queries.selectContextByCaseId(caseId, ::toUserContext).executeAsOneOrNull()
    }

    // ---- Question ----

    override suspend fun analyzeQuestions(
        caseId: Long,
        documentText: String,
        userContextJson: String
    ): List<Question> = withContext(Dispatchers.Default) {
        // 本人条件を JSON 文字列から Map に戻す。解析できなければ空の Map で続行する。
        val userContext = parseUserContext(userContextJson)

        // 本人条件を保存してからバックエンドへ問い合わせる。
        saveUserContext(caseId, userContextJson)

        val dtos = apiClient.analyze(caseId, documentText, userContext)
        val questions = dtos.map { it.toQuestion(caseId) }

        queries.transaction {
            questions.forEach { q ->
                queries.insertQuestion(
                    case_id = q.caseId,
                    title = q.title,
                    reason = q.reason,
                    risk_level = q.riskLevel.name,
                    source_text = q.sourceText,
                    source_page = q.sourcePage?.toLong(),
                    status = q.status.name
                )
            }
        }

        queries.selectQuestionsForCase(caseId, ::toQuestion).executeAsList()
    }

    private fun parseUserContext(json: String): Map<String, kotlinx.serialization.json.JsonElement> {
        return try {
            Json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(json)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    override fun observeQuestionsForCase(caseId: Long): Flow<List<Question>> =
        queries.selectQuestionsForCase(caseId, ::toQuestion).asFlow().mapToList(Dispatchers.Default)

    override suspend fun getQuestionById(id: Long): Question? = withContext(Dispatchers.Default) {
        queries.selectQuestionById(id, ::toQuestion).executeAsOneOrNull()
    }

    override suspend fun confirmQuestion(id: Long) {
        withContext(Dispatchers.Default) {
            queries.updateQuestionStatus(QuestionStatus.CONFIRMED.name, id)
        }
    }

    override suspend fun unconfirmQuestion(id: Long) {
        withContext(Dispatchers.Default) {
            queries.updateQuestionStatus(QuestionStatus.UNCONFIRMED.name, id)
        }
    }

    // ---- QuestionAnswer ----

    override suspend fun saveAnswer(
        questionId: Long,
        answerText: String,
        answeredBy: String?,
        answeredAt: Long
    ): Long = withContext(Dispatchers.Default) {
        queries.transactionWithResult {
            val existing = queries.selectAnswerByQuestionId(questionId).executeAsOneOrNull()
            if (existing != null) {
                queries.updateAnswer(
                    id = existing.id,
                    question_id = questionId,
                    answer_text = answerText,
                    answered_by = answeredBy,
                    answered_at = answeredAt
                )
                existing.id
            } else {
                queries.insertAnswer(
                    question_id = questionId,
                    answer_text = answerText,
                    answered_by = answeredBy,
                    answered_at = answeredAt
                )
                queries.lastInsertAnswerRowId().executeAsOne()
            }
        }
    }

    override suspend fun getAnswerForQuestion(questionId: Long): QuestionAnswer? = withContext(Dispatchers.Default) {
        queries.selectAnswerByQuestionId(questionId, ::toQuestionAnswer).executeAsOneOrNull()
    }

    // ---- Mappers ----

    private fun toDocumentCase(
        id: Long,
        title: String,
        document_uri: String?,
        document_type: String,
        created_at: Long,
        deadline: Long?,
        status: String
    ) = DocumentCase(
        id = id,
        title = title,
        documentUri = document_uri,
        documentType = DocumentType.valueOf(document_type),
        createdAt = created_at,
        deadline = deadline,
        status = CaseStatus.valueOf(status)
    )

    private fun toUserContext(
        case_id: Long,
        attributes_json: String
    ) = UserContext(
        caseId = case_id,
        attributesJson = attributes_json
    )

    private fun toQuestion(
        id: Long,
        case_id: Long,
        title: String,
        reason: String,
        risk_level: String,
        source_text: String?,
        source_page: Long?,
        status: String
    ) = Question(
        id = id,
        caseId = case_id,
        title = title,
        reason = reason,
        riskLevel = RiskLevel.valueOf(risk_level),
        sourceText = source_text,
        sourcePage = source_page?.toInt(),
        status = QuestionStatus.valueOf(status)
    )

    private fun toQuestionAnswer(
        id: Long,
        question_id: Long,
        answer_text: String,
        answered_by: String?,
        answered_at: Long
    ) = QuestionAnswer(
        id = id,
        questionId = question_id,
        answerText = answer_text,
        answeredBy = answered_by,
        answeredAt = answered_at
    )

    @OptIn(ExperimentalTime::class)
    private fun currentTimeMillis(): Long =
        Clock.System.now().toEpochMilliseconds()
}
