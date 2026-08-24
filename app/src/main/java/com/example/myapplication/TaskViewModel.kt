package com.example.myapplication

import android.app.Application
import androidx.lifecycle.*
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.myapplication.data.*
import com.example.myapplication.data.calendar.CalendarAuthState
import com.example.myapplication.data.calendar.CalendarResult
import com.example.myapplication.data.calendar.GoogleAuthManager
import com.example.myapplication.data.calendar.GoogleCalendarSync
import com.example.myapplication.data.calendar.SignOutResult
import com.example.myapplication.work.FreeTimeCheckWorker
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/**
 * 「空き時間です」通知チェックの定期実行登録を抽象化する。
 * WorkManager.getInstance() は初期化されていないと例外を投げるため、単体テストで
 * TaskViewModel を作るたびに実行されないよう差し替え可能にする。
 */
fun interface FreeTimeCheckScheduler {
    fun schedule()
}

private class WorkManagerFreeTimeCheckScheduler(
    private val application: Application
) : FreeTimeCheckScheduler {
    override fun schedule() {
        val request = PeriodicWorkRequestBuilder<FreeTimeCheckWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(application).enqueueUniquePeriodicWork(
            FreeTimeCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}

class TaskViewModel(
    application: Application,
    private val repository: TaskRepository = TaskRepository(
        AppDatabase.getDatabase(application).taskDao()
    ),
    private val authManager: GoogleAuthManager = GoogleAuthManager.get(application),
    private val calendarSync: GoogleCalendarSync = GoogleCalendarSync(authManager),
    private val freeTimeCheckScheduler: FreeTimeCheckScheduler =
        WorkManagerFreeTimeCheckScheduler(application)
) : AndroidViewModel(application) {

    val allTasks: StateFlow<List<TaskWithSubTasks>> = repository.allTasks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    /** ユーザーが作ったカテゴリの一覧。タブや登録画面の選択肢の元になる。 */
    val categories: StateFlow<List<Category>> = repository.categories.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    /** カレンダー連携（OAuth）の状態。UI はこれを見て表示と操作を切り替える。 */
    val authState: StateFlow<CalendarAuthState> = authManager.authState

    /** 画面にスナックバーで出す一言（重複名の警告、カレンダー連携の失敗など）。 */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /**
     * 取り消し（元に戻す）用に保持する削除済みタスク。
     *
     * カレンダーの予定を実際に消せたかどうかも一緒に覚えておく。
     * これが無いと、削除に失敗していた場合でも予定を作り直してしまい、
     * 元の予定が孤立したままカレンダーに残る。
     */
    private class DeletedTask(
        val task: Task,
        val subTasks: List<SubTask>,
        /** カレンダーの予定を削除できたか。未連携のタスクなら false（作り直す必要も無い）。 */
        val calendarEventDeleted: Boolean
    )

    /** 直前に削除したタスク。取り消し（元に戻す）用にサブタスクごと保持する。 */
    private var lastDeleted: DeletedTask? = null

    /**
     * タスク ID ごとのカレンダー操作排他用 Mutex。
     * 素早く連携トグルを 2 回操作しても、同じタスクの予定が 2 件作られないようにする。
     */
    private val calendarMutexes = mutableMapOf<Int, Mutex>()

    init {
        // 起動時に一度だけ、ユーザー操作なしで認可済みかを確認しておく
        viewModelScope.launch { authManager.refreshAuthState() }
        // 「空き時間です」通知の1時間おきチェックを登録する（既に登録済みなら重複登録しない）
        freeTimeCheckScheduler.schedule()
    }

    /** カレンダーの予定に書く分類名。カテゴリが消えたタスクは「未分類」とする。 */
    private fun categoryNameOf(task: Task): String =
        categories.value.firstOrNull { it.id == task.categoryId }?.name
            ?: Category.UNCATEGORIZED_LABEL

    /**
     * カレンダー操作の失敗をスナックバーで知らせる。
     * REST API はネットワーク断やトークン切れで日常的に失敗するため、黙って握り潰さない。
     */
    private fun notifyCalendarFailure(result: CalendarResult<*>) {
        val message = when (result) {
            is CalendarResult.Success -> return
            is CalendarResult.Unauthorized -> "Google カレンダーへの再ログインが必要です"
            is CalendarResult.NotFound -> "カレンダーに予定が見つかりませんでした"
            is CalendarResult.Failure -> result.message
        }
        _messages.tryEmit(message)
    }

    // ---- カテゴリ ----

    fun addCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            if (repository.addCategory(trimmed)) {
                _messages.tryEmit("「$trimmed」を追加しました")
            } else {
                _messages.tryEmit("「$trimmed」はすでにあります")
            }
        }
    }

    fun renameCategory(category: Category, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == category.name) return
        viewModelScope.launch {
            if (repository.renameCategory(category, trimmed)) {
                _messages.tryEmit("「$trimmed」に変更しました")
            } else {
                _messages.tryEmit("「$trimmed」はすでにあります")
            }
        }
    }

    /**
     * カテゴリを削除する。中のタスクは消えず「未分類」に移る
     * （tasks.categoryId の外部キーが ON DELETE SET NULL のため DB 側で処理される）。
     */
    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            val moved = repository.countTasksInCategory(category.id)
            repository.deleteCategory(category)
            _messages.tryEmit(
                if (moved > 0) {
                    "「${category.name}」を削除しました（${moved}件を未分類に移動）"
                } else {
                    "「${category.name}」を削除しました"
                }
            )
        }
    }

    /** 削除の確認ダイアログに件数を出すために使う。 */
    suspend fun countTasksInCategory(categoryId: Int): Int =
        repository.countTasksInCategory(categoryId)

    // ---- カレンダー連携 ----

    /** 連携中の Google アカウントからサインアウトする。 */
    fun signOut() {
        viewModelScope.launch {
            // Google 側の取り消しに失敗することがあるので、実態どおりに知らせる
            val message = when (authManager.signOut()) {
                SignOutResult.REVOKED -> "Google カレンダーとの連携を解除しました"
                SignOutResult.LOCAL_ONLY ->
                    "アプリ側の連携情報を削除しました（Google 側の許可の取り消しには失敗した可能性があります）"
            }
            _messages.tryEmit(message)
        }
    }

    // ---- タスク ----

    fun addTask(
        title: String,
        deadline: Long,
        importance: Int,
        urgency: Int,
        categoryId: Int?,
        subTaskTitles: List<String> = emptyList(),
        addToCalendar: Boolean = false
    ) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            val task = Task(
                title = trimmed,
                deadline = deadline,
                importance = importance,
                urgency = urgency,
                categoryId = categoryId
            )
            val id = repository.insert(task)

            val subTasks = subTaskTitles
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapIndexed { index, subTitle ->
                    SubTask(taskId = id, title = subTitle, sortOrder = index)
                }
            if (subTasks.isNotEmpty()) repository.insertSubTasks(subTasks)

            if (addToCalendar) {
                val saved = task.copy(id = id)
                when (val result = calendarSync.insertEvent(saved, categoryNameOf(saved))) {
                    is CalendarResult.Success ->
                        // 全列を上書きすると並行する別更新が失われる恐れがあるので、calendarEventId だけ更新
                        repository.updateCalendarEventId(saved.id, result.value)
                    else -> notifyCalendarFailure(result)
                }
            }
        }
    }

    /**
     * 作成済みのタスクをカレンダーに登録する / 登録を解除する。
     * 認可の確認は呼び出し側（UI）で済ませておくこと。
     */
    fun setCalendarLinked(task: Task, enabled: Boolean) {
        viewModelScope.launch {
            // タスク ID ごとに Mutex を用意。同じタスクに対するカレンダー操作は同時に 1 つだけ実行される。
            val mutex = synchronized(calendarMutexes) {
                calendarMutexes.getOrPut(task.id) { Mutex() }
            }
            mutex.withLock {
                // Mutex 取得後に最新のタスク状態を再取得。
                // 待ち行列で他の処理が既に calendarEventId を書き換えていた場合、二重登録を防ぐため。
                val current = repository.getTaskById(task.id) ?: return@withLock
                if (enabled) {
                    if (current.calendarEventId != null) return@withLock
                    when (val result = calendarSync.insertEvent(current, categoryNameOf(current))) {
                        is CalendarResult.Success -> {
                            // calendarEventId だけを更新。Task 全列を上書きすると他の変更が巻き戻る恐れがある。
                            repository.updateCalendarEventId(current.id, result.value)
                            _messages.tryEmit("「${current.title}」をカレンダーに登録しました")
                        }
                        else -> notifyCalendarFailure(result)
                    }
                } else {
                    val eventId = current.calendarEventId ?: return@withLock
                    when (val result = calendarSync.deleteEvent(eventId)) {
                        is CalendarResult.Success -> {
                            // 削除に成功したときだけ calendarEventId を外す。
                            // 消せていないのに解除すると予定が迷子になる（ADR-001）。
                            repository.updateCalendarEventId(current.id, null)
                            _messages.tryEmit("「${current.title}」の予定を削除しました")
                        }
                        else -> notifyCalendarFailure(result)
                    }
                }
            }
        }
    }

    fun toggleCompleted(task: Task) {
        viewModelScope.launch {
            val nowCompleted = !task.isCompleted
            // 「進行中のまま完了」という状態は持たせない。完了にする操作では status を TODO に戻す
            val updated = task.copy(
                isCompleted = nowCompleted,
                status = if (nowCompleted) TaskStatus.TODO else task.status
            )
            repository.update(updated)
            syncToCalendar(updated)
        }
    }

    /**
     * タスク名を変更する。連携済み（calendarEventId != null）なら syncToCalendar が
     * カレンダー側の予定タイトルも合わせて更新する。
     */
    fun renameTask(task: Task, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty() || trimmed == task.title) return
        viewModelScope.launch {
            repository.updateTitle(task.id, trimmed)
            syncToCalendar(task.copy(title = trimmed))
        }
    }

    fun toggleSubTaskCompleted(subTask: SubTask) {
        viewModelScope.launch {
            repository.updateSubTask(subTask.copy(isCompleted = !subTask.isCompleted))
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            // 取り消しに備えて、削除前にサブタスクも読み出しておく
            val subTasks = repository.getSubTasksFor(task.id)
            repository.delete(task)

            // カレンダー側を消せたかどうかは取り消し時の判断材料になるので必ず控える
            val eventDeleted = task.calendarEventId?.let { eventId ->
                val result = calendarSync.deleteEvent(eventId)
                notifyCalendarFailure(result)
                result is CalendarResult.Success
            } ?: false

            lastDeleted = DeletedTask(task, subTasks, eventDeleted)
        }
    }

    /** 直前の削除を取り消す。id を保持したまま insert し直す。 */
    fun undoDelete() {
        val deleted = lastDeleted ?: return
        lastDeleted = null
        viewModelScope.launch {
            val task = deleted.task
            repository.insert(task)
            if (deleted.subTasks.isNotEmpty()) repository.insertSubTasks(deleted.subTasks)

            // 予定を消せていなかった場合はカレンダー側にまだ残っている。
            // 作り直すと二重になり、古い ID も上書きで失われて孤立するので、元の ID をそのまま戻す
            if (task.calendarEventId == null || !deleted.calendarEventDeleted) return@launch

            // 予定は確かに消えているので、連携を復活させるには作り直すしかない
            when (val result = calendarSync.insertEvent(task, categoryNameOf(task))) {
                is CalendarResult.Success ->
                    // 作り直した予定の ID だけを更新。他の列は巻き戻さない。
                    repository.updateCalendarEventId(task.id, result.value)
                else -> {
                    // 作り直せなかったら未連携に戻す（古い ID は既に無効なため）
                    repository.updateCalendarEventId(task.id, null)
                    notifyCalendarFailure(result)
                }
            }
        }
    }

    /**
     * 連携済みのタスクの変更をカレンダーへ反映する。
     * ユーザーがカレンダー側で予定を消していた場合は作り直し、ID を貼り替える。
     */
    private suspend fun syncToCalendar(task: Task) {
        val eventId = task.calendarEventId ?: return
        val name = categoryNameOf(task)
        when (val result = calendarSync.updateEvent(eventId, task, name)) {
            is CalendarResult.Success -> Unit
            is CalendarResult.NotFound -> {
                when (val recreated = calendarSync.insertEvent(task, name)) {
                    is CalendarResult.Success ->
                        // 予定を作り直したので、calendarEventId だけを新しい値に差し替える
                        repository.updateCalendarEventId(task.id, recreated.value)
                    else -> {
                        // 作り直せなかったら未連携に戻す（古い ID は既に無効）
                        repository.updateCalendarEventId(task.id, null)
                        notifyCalendarFailure(recreated)
                    }
                }
            }
            else -> notifyCalendarFailure(result)
        }
    }
}
