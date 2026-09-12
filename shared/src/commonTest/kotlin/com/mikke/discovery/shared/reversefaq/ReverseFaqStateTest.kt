package com.mikke.discovery.shared.reversefaq

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class ReverseFaqStateTest {

    private fun TestScope.createState(repository: ReverseFaqRepository) = ReverseFaqState(
        repository = repository,
        coroutineScope = this,
        useSupervisorJob = false,
        sharingStopTimeoutMs = 0L
    )

    private suspend fun TestScope.createCaseAndAnalyze(state: ReverseFaqState, title: String): Long {
        val deferred = CompletableDeferred<Long>()
        state.createCaseAndReturnId(title) { deferred.complete(it) }
        advanceUntilIdle()
        val caseId = deferred.await()
        state.analyzeCase(caseId, "テスト用契約書本文です。", "{}") { }
        advanceUntilIdle()
        return caseId
    }

    @Test
    fun `空の契約書本文でも分析終了を通知する`(): Unit = runTest {
        val repository = FakeReverseFaqRepository()
        val state = createState(repository)
        var success = true
        var finished = false
        try {
            state.analyzeCase(1L, "   ", "{}") {
                success = it
                finished = true
            }

            assertFalse(success)
            assertEquals(true, finished)
        } finally {
            state.close()
        }
    }

    @Test
    fun `案件を作成すると一覧に追加される`(): Unit = runTest {
        val repository = FakeReverseFaqRepository()
        val state = createState(repository)
        try {
            println("DEBUG: before createCase")
            state.createCase("テスト案件")
            advanceUntilIdle()
            println("DEBUG: after createCase")

            val cases = repository.observeAllCases().first { it.isNotEmpty() }
            println("DEBUG: cases=$cases")
            assertEquals(1, cases.size)
            assertEquals("テスト案件", cases.first().title)
        } finally {
            state.close()
        }
    }

    @Test
    fun `案件作成後に質問が分析されて生成される`(): Unit = runTest {
        val repository = FakeReverseFaqRepository()
        val state = createState(repository)
        try {
            println("DEBUG: before createCaseAndAnalyze")
            val caseId = createCaseAndAnalyze(state, "テスト案件")
            println("DEBUG: after createCaseAndAnalyze caseId=$caseId")

            val questions = repository.observeQuestionsForCase(caseId).first { it.isNotEmpty() }
            println("DEBUG: questions=$questions")
            assertEquals(3, questions.size)
        } finally {
            state.close()
        }
    }

    @Test
    fun `質問を確認済みにすると進捗が更新される`(): Unit = runTest {
        val repository = FakeReverseFaqRepository()
        val state = createState(repository)
        try {
            val caseId = createCaseAndAnalyze(state, "テスト案件")
            val question = repository.observeQuestionsForCase(caseId).first { it.isNotEmpty() }.first()

            println("DEBUG: before confirmQuestion id=${question.id}")
            state.confirmQuestion(question.id)
            advanceUntilIdle()
            println("DEBUG: after confirmQuestion")

            val questions = repository.observeQuestionsForCase(caseId)
                .first { list -> list.any { it.status == QuestionStatus.CONFIRMED } }
            println("DEBUG: questions=$questions")
            assertEquals(1, questions.count { it.status == QuestionStatus.CONFIRMED })
        } finally {
            state.close()
        }
    }

    @Test
    fun `回答を保存すると確認済み扱いになる`(): Unit = runTest {
        val repository = FakeReverseFaqRepository()
        val state = createState(repository)
        try {
            val caseId = createCaseAndAnalyze(state, "テスト案件")
            val question = repository.observeQuestionsForCase(caseId).first { it.isNotEmpty() }.first()

            println("DEBUG: before saveAnswer id=${question.id}")
            state.saveAnswer(question.id, "テスト回答", "担当者", 1_700_000_000_000L)
            advanceUntilIdle()
            println("DEBUG: after saveAnswer")

            assertNotNull(repository.getAnswerForQuestion(question.id))
            val questions = repository.observeQuestionsForCase(caseId)
                .first { list -> list.any { it.id == question.id && it.status == QuestionStatus.CONFIRMED } }
            assertEquals(1, questions.count { it.status == QuestionStatus.CONFIRMED })
        } finally {
            state.close()
        }
    }

    @Test
    fun `全質問が確認済みになると案件ステータスが完了になる`(): Unit = runTest {
        val repository = FakeReverseFaqRepository()
        val state = createState(repository)
        try {
            val caseId = createCaseAndAnalyze(state, "テスト案件")
            val questions = repository.observeQuestionsForCase(caseId).first { it.isNotEmpty() }

            println("DEBUG: before saveAllAnswers")
            questions.forEach { state.saveAnswer(it.id, "OK", null, 1_700_000_000_000L) }
            advanceUntilIdle()
            println("DEBUG: after saveAllAnswers")

            val questionsAfter = repository.observeQuestionsForCase(caseId)
                .first { list -> list.all { it.status == QuestionStatus.CONFIRMED } }
            assertEquals(3, questionsAfter.size)

            val cases = repository.observeAllCases()
                .first { cases -> cases.any { it.id == caseId && it.status == CaseStatus.COMPLETED } }
            val case = cases.first { it.id == caseId }
            assertEquals(CaseStatus.COMPLETED, case.status)
        } finally {
            state.close()
        }
    }

    @Test
    fun `進捗率は確認済み数を総数で割ったパーセント`(): Unit = runTest {
        val repository = FakeReverseFaqRepository()
        val state = createState(repository)
        try {
            val caseId = createCaseAndAnalyze(state, "テスト案件")
            val questions = repository.observeQuestionsForCase(caseId).first { it.isNotEmpty() }

            state.confirmQuestion(questions[0].id)
            advanceUntilIdle()

            val updated = repository.observeQuestionsForCase(caseId)
                .first { list -> list.count { it.status == QuestionStatus.CONFIRMED } == 1 }
            val progress = CaseProgress(updated.count { it.status == QuestionStatus.CONFIRMED }, updated.size)
            assertEquals(33, progress.percentage)
        } finally {
            state.close()
        }
    }
}
