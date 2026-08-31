package com.example.myapplication.shared.reversefaq

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class ReverseFaqStateTest {

    @Test
    fun `案件を作成すると一覧に追加される`() = runTest {
        val repository = FakeReverseFaqRepository()
        val state = ReverseFaqState(repository, backgroundScope)

        state.createCase("テスト案件")

        val cases = state.allCases.first { it.isNotEmpty() }
        assertEquals(1, cases.size)
        assertEquals("テスト案件", cases.first().title)
    }

    @Test
    fun `案件作成後にダミー質問が生成される`() = runTest {
        val repository = FakeReverseFaqRepository()
        val state = ReverseFaqState(repository, backgroundScope)

        state.createCaseAndGenerateQuestions("テスト案件")

        val caseId = state.allCases.first { it.isNotEmpty() }.first().id
        val questions = state.questionsForCase(caseId).first { it.isNotEmpty() }
        assertEquals(3, questions.size)
    }

    @Test
    fun `質問を確認済みにすると進捗が更新される`() = runTest {
        val repository = FakeReverseFaqRepository()
        val state = ReverseFaqState(repository, backgroundScope)

        state.createCaseAndGenerateQuestions("テスト案件")
        val caseId = state.allCases.first { it.isNotEmpty() }.first().id
        val question = state.questionsForCase(caseId).first { it.isNotEmpty() }.first()

        state.confirmQuestion(question.id)

        val progress = state.progressForCase(caseId).first { it.totalCount == 3 }
        assertEquals(1, progress.confirmedCount)
        assertEquals(3, progress.totalCount)
    }

    @Test
    fun `回答を保存すると確認済み扱いになる`() = runTest {
        val repository = FakeReverseFaqRepository()
        val state = ReverseFaqState(repository, backgroundScope)

        state.createCaseAndGenerateQuestions("テスト案件")
        val caseId = state.allCases.first { it.isNotEmpty() }.first().id
        val question = state.questionsForCase(caseId).first { it.isNotEmpty() }.first()

        state.saveAnswer(question.id, "テスト回答", "担当者", 1_700_000_000_000L)

        val progress = state.progressForCase(caseId).first { it.totalCount == 3 }
        assertEquals(1, progress.confirmedCount)
        assertEquals(3, progress.totalCount)
        assertNotNull(state.getAnswerForQuestion(question.id))
    }

    @Test
    fun `全質問が確認済みになると案件ステータスが完了になる`() = runTest {
        val repository = FakeReverseFaqRepository()
        val state = ReverseFaqState(repository, backgroundScope)

        state.createCaseAndGenerateQuestions("テスト案件")
        val caseId = state.allCases.first { it.isNotEmpty() }.first().id
        val questions = state.questionsForCase(caseId).first { it.isNotEmpty() }

        questions.forEach { state.saveAnswer(it.id, "OK", null, 1_700_000_000_000L) }

        val case = state.allCases.first { it.first().status == CaseStatus.COMPLETED }.first()
        assertEquals(CaseStatus.COMPLETED, case.status)
    }

    @Test
    fun `進捗率は確認済み数を総数で割ったパーセント`() = runTest {
        val repository = FakeReverseFaqRepository()
        val state = ReverseFaqState(repository, backgroundScope)

        state.createCaseAndGenerateQuestions("テスト案件")
        val caseId = state.allCases.first { it.isNotEmpty() }.first().id
        val questions = state.questionsForCase(caseId).first { it.isNotEmpty() }

        state.confirmQuestion(questions[0].id)

        val progress = state.progressForCase(caseId).first { it.confirmedCount == 1 }
        assertEquals(33, progress.percentage)
    }
}
