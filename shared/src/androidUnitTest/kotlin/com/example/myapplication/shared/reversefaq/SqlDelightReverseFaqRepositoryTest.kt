package com.example.myapplication.shared.reversefaq

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.myapplication.shared.db.DatabaseDriverFactory
import com.example.myapplication.shared.db.SharedDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlDelightReverseFaqRepositoryTest {

    private lateinit var driver: SqlDriver
    private lateinit var repository: SqlDelightReverseFaqRepository

    private class InMemoryDriverFactory(private val driver: SqlDriver) : DatabaseDriverFactory {
        override fun createDriver(): SqlDriver = driver
    }

    @Before
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "PRAGMA foreign_keys = ON;", 0)
        SharedDatabase.Schema.create(driver)
        repository = SqlDelightReverseFaqRepository(
            InMemoryDriverFactory(driver),
            FakeReverseFaqApiClient()
        )
    }

    @After
    fun tearDown() {
        driver.close()
    }

    @Test
    fun `案件を作成するとIDが返る`() = runBlocking {
        val id = repository.createCase(
            title = "テスト案件",
            documentUri = null,
            documentType = DocumentType.NONE,
            deadline = null
        )
        assertTrue(id > 0)

        val case = repository.getCaseById(id)
        assertNotNull(case)
        assertEquals("テスト案件", case.title)
        assertEquals(CaseStatus.DRAFT, case.status)
    }

    @Test
    fun `質問を分析して生成すると案件に紐づく質問が保存される`() = runBlocking {
        val caseId = repository.createCase(
            title = "テスト案件",
            documentUri = null,
            documentType = DocumentType.NONE,
            deadline = null
        )

        val questions = repository.analyzeQuestions(caseId, "テスト契約書の本文です。", "{}")

        assertEquals(3, questions.size)
        assertTrue(questions.all { it.caseId == caseId })
        assertTrue(questions.any { it.riskLevel == RiskLevel.HIGH })

        val observed = repository.observeQuestionsForCase(caseId).first()
        assertEquals(3, observed.size)
    }

    @Test
    fun `質問を確認済みにできる`() = runBlocking {
        val caseId = repository.createCase("テスト案件", null, DocumentType.NONE, null)
        val questions = repository.analyzeQuestions(caseId, "テスト契約書の本文です。", "{}")
        val question = questions.first()

        repository.confirmQuestion(question.id)

        val updated = repository.getQuestionById(question.id)
        assertEquals(QuestionStatus.CONFIRMED, updated?.status)
    }

    @Test
    fun `回答を保存できる`() = runBlocking {
        val caseId = repository.createCase("テスト案件", null, DocumentType.NONE, null)
        val questions = repository.analyzeQuestions(caseId, "テスト契約書の本文です。", "{}")
        val question = questions.first()

        repository.saveAnswer(
            questionId = question.id,
            answerText = "テスト回答",
            answeredBy = "担当者",
            answeredAt = 1_700_000_000_000L
        )

        val answer = repository.getAnswerForQuestion(question.id)
        assertNotNull(answer)
        assertEquals("テスト回答", answer.answerText)
        assertEquals("担当者", answer.answeredBy)
    }

    @Test
    fun `本人条件を保存できる`() = runBlocking {
        val caseId = repository.createCase("テスト案件", null, DocumentType.NONE, null)
        repository.saveUserContext(caseId, """{"student":true}""")

        val context = repository.getUserContext(caseId)
        assertNotNull(context)
        assertEquals("""{"student":true}""", context.attributesJson)
    }

    @Test
    fun `案件削除時に紐づく質問と回答と本人条件も削除される`() = runBlocking {
        val caseId = repository.createCase("テスト案件", null, DocumentType.NONE, null)
        repository.saveUserContext(caseId, "{}")
        val questions = repository.analyzeQuestions(caseId, "テスト契約書の本文です。", "{}")
        repository.saveAnswer(questions.first().id, "回答", null, 1_700_000_000_000L)

        repository.deleteCase(caseId)

        assertNull(repository.getCaseById(caseId))
        assertNull(repository.getUserContext(caseId))
        assertEquals(emptyList(), repository.observeQuestionsForCase(caseId).first())
    }
}
