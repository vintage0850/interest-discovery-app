package com.example.myapplication.shared.reversefaq

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Reverse FAQ画面群の状態管理。
 * Compose Multiplatform共通で使い、Android/iOSのホストからcoroutineScopeを渡す。
 */
class ReverseFaqState(
    private val repository: ReverseFaqRepository,
    private val coroutineScope: CoroutineScope
) {
    /**
     * 子コルーチンの失敗が[stateIn]の内部コレクターを止めないよう、[SupervisorJob]で囲む。
     */
    private val supervisedScope = CoroutineScope(
        coroutineScope.coroutineContext + SupervisorJob(parent = coroutineScope.coroutineContext[Job])
    )

    val allCases: StateFlow<List<DocumentCase>> = repository.observeAllCases()
        .stateIn(coroutineScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val questionFlows = mutableMapOf<Long, StateFlow<List<Question>>>()
    private val progressFlows = mutableMapOf<Long, StateFlow<CaseProgress>>()

    fun questionsForCase(caseId: Long): StateFlow<List<Question>> =
        questionFlows.getOrPut(caseId) {
            repository.observeQuestionsForCase(caseId)
                .stateIn(coroutineScope, SharingStarted.WhileSubscribed(5000), emptyList())
        }

    fun progressForCase(caseId: Long): StateFlow<CaseProgress> =
        progressFlows.getOrPut(caseId) {
            questionsForCase(caseId)
                .map { questions ->
                    val confirmed = questions.count { it.status == QuestionStatus.CONFIRMED }
                    CaseProgress(confirmed, questions.size)
                }
                .stateIn(coroutineScope, SharingStarted.WhileSubscribed(5000), CaseProgress(0, 0))
        }

    fun createCase(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        supervisedScope.launch {
            try {
                repository.createCase(trimmed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit("保存に失敗しました")
            }
        }
    }

    /**
     * 案件を作成し、すぐにダミー質問を生成する。
     * Phase 1ではAI分析の代わりに固定のダミー質問を使う。
     */
    fun createCaseAndGenerateQuestions(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        supervisedScope.launch {
            try {
                val caseId = repository.createCase(trimmed)
                repository.generateDummyQuestions(caseId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit("保存に失敗しました")
            }
        }
    }

    fun confirmQuestion(questionId: Long) {
        supervisedScope.launch {
            try {
                repository.confirmQuestion(questionId)
                updateCaseStatusIfAllConfirmed(questionId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit("保存に失敗しました")
            }
        }
    }

    fun saveAnswer(questionId: Long, answerText: String, answeredBy: String?, answeredAt: Long) {
        val trimmed = answerText.trim()
        if (trimmed.isEmpty()) return
        supervisedScope.launch {
            try {
                repository.saveAnswer(questionId, trimmed, answeredBy, answeredAt)
                repository.confirmQuestion(questionId)
                updateCaseStatusIfAllConfirmed(questionId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit("保存に失敗しました")
            }
        }
    }

    suspend fun getQuestionById(id: Long): Question? =
        repository.getQuestionById(id)

    suspend fun getAnswerForQuestion(questionId: Long): QuestionAnswer? =
        repository.getAnswerForQuestion(questionId)

    private suspend fun updateCaseStatusIfAllConfirmed(questionId: Long) {
        val question = repository.getQuestionById(questionId) ?: return
        val questions = repository.observeQuestionsForCase(question.caseId).first()
        val status = if (questions.isNotEmpty() && questions.all { it.status == QuestionStatus.CONFIRMED }) {
            CaseStatus.COMPLETED
        } else {
            CaseStatus.IN_PROGRESS
        }
        repository.updateCaseStatus(question.caseId, status)
    }
}
