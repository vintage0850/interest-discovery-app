package com.mikke.discovery.shared.reversefaq

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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Reverse FAQ画面群の状態管理。
 * Compose Multiplatform共通で使い、Android/iOSのホストからcoroutineScopeを渡す。
 */
class ReverseFaqState(
    private val repository: ReverseFaqRepository,
    private val coroutineScope: CoroutineScope,
    useSupervisorJob: Boolean = true,
    private val sharingStopTimeoutMs: Long = 5000L
) {
    /**
     * [stateIn] で作成した [StateFlow] をまとめてキャンセルできるよう、
     * 専用のスコープを用意する。テストでは [close] を呼ぶことで
     * [runTest] 終了時のデッドロックを防ぐ。
     */
    private val stateJob = SupervisorJob(parent = coroutineScope.coroutineContext[Job])
    private val stateScope = CoroutineScope(coroutineScope.coroutineContext + stateJob)

    /**
     * 子コルーチンの失敗が[stateIn]の内部コレクターを止めないよう、[SupervisorJob]で囲む。
     * テストでは[TestScope.advanceUntilIdle]でコルーチンを完了させるため、
     * スーパーバイザを無効化できる。
     */
    private val supervisedScope = if (useSupervisorJob) {
        CoroutineScope(
            coroutineScope.coroutineContext + SupervisorJob(parent = coroutineScope.coroutineContext[Job])
        )
    } else {
        coroutineScope
    }

    val allCases: StateFlow<List<DocumentCase>> = repository.observeAllCases()
        .stateIn(stateScope, SharingStarted.WhileSubscribed(sharingStopTimeoutMs), emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val questionFlows = mutableMapOf<Long, StateFlow<List<Question>>>()
    private val progressFlows = mutableMapOf<Long, StateFlow<CaseProgress>>()
    private val mutationMutex = Mutex()

    fun questionsForCase(caseId: Long): StateFlow<List<Question>> =
        questionFlows.getOrPut(caseId) {
            repository.observeQuestionsForCase(caseId)
                .stateIn(stateScope, SharingStarted.WhileSubscribed(sharingStopTimeoutMs), emptyList())
        }

    fun progressForCase(caseId: Long): StateFlow<CaseProgress> =
        progressFlows.getOrPut(caseId) {
            questionsForCase(caseId)
                .map { questions ->
                    val confirmed = questions.count { it.status == QuestionStatus.CONFIRMED }
                    CaseProgress(confirmed, questions.size)
                }
                .stateIn(stateScope, SharingStarted.WhileSubscribed(sharingStopTimeoutMs), CaseProgress(0, 0))
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
     * 案件を作成し、その ID を返す。
     * 画面遷移後に本人条件入力へ進むために使う。
     */
    fun createCaseAndReturnId(title: String, onCreated: (Long) -> Unit) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        supervisedScope.launch {
            try {
                val caseId = repository.createCase(trimmed)
                onCreated(caseId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit("保存に失敗しました")
            }
        }
    }

    /**
     * 契約書本文と本人条件をバックエンドに送り、AI 生成の質問を取得して保存する。
     * ネットワークエラー時はダミー質問で補わず、エラーメッセージを表示する。
     */
    fun analyzeCase(
        caseId: Long,
        documentText: String,
        userContextJson: String,
        onFinished: (success: Boolean) -> Unit = {}
    ) {
        val trimmed = documentText.trim()
        if (trimmed.isEmpty()) {
            // 呼び出し側が先にローディング表示へ切り替えていても、必ず解除できるようにする。
            onFinished(false)
            return
        }
        supervisedScope.launch {
            var success = false
            try {
                repository.analyzeQuestions(caseId, trimmed, userContextJson)
                repository.updateCaseStatus(caseId, CaseStatus.IN_PROGRESS)
                success = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: ReverseFaqApiException) {
                _messages.tryEmit(e.message ?: "質問の生成に失敗しました。バックエンドが起動しているか確認してください。")
            } catch (e: Exception) {
                _messages.tryEmit("質問の生成に失敗しました。バックエンドが起動しているか確認してください。")
            } finally {
                // 成功・失敗どちらでも呼び出し元のローディング状態を必ず解除する。
                // ここを onComplete（成功時のみ）のままにすると、エラー時に
                // ローディング表示が解除されず「固まって見える」不具合になる。
                onFinished(success)
            }
        }
    }

    fun confirmQuestion(questionId: Long) {
        supervisedScope.launch {
            try {
                mutationMutex.withLock {
                    repository.confirmQuestion(questionId)
                    updateCaseStatusIfAllConfirmed(questionId)
                }
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
                mutationMutex.withLock {
                    repository.saveAnswer(questionId, trimmed, answeredBy, answeredAt)
                    repository.confirmQuestion(questionId)
                    updateCaseStatusIfAllConfirmed(questionId)
                }
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

    /**
     * [StateFlow] 用のスコープをキャンセルする。
     * ホスト側（ViewModel/Composable）で適切に破棄される想定だが、
     * テストでは明示的に呼ぶことで [runTest] が終了できるようになる。
     */
    fun close() {
        stateJob.cancel()
    }

    private suspend fun updateCaseStatusForQuestions(caseId: Long, questions: List<Question>) {
        if (questions.isEmpty()) return
        val status = if (questions.all { it.status == QuestionStatus.CONFIRMED }) {
            CaseStatus.COMPLETED
        } else {
            CaseStatus.IN_PROGRESS
        }
        repository.updateCaseStatus(caseId, status)
    }

    private suspend fun updateCaseStatusIfAllConfirmed(questionId: Long) {
        val question = repository.getQuestionById(questionId) ?: return
        val questions = repository.observeQuestionsForCase(question.caseId).first()
        updateCaseStatusForQuestions(question.caseId, questions)
    }
}
