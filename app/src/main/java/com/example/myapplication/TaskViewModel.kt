package com.example.myapplication

import android.app.Application
import androidx.lifecycle.*
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.myapplication.data.*
import com.example.myapplication.data.NotificationWindow
import com.example.myapplication.data.NotificationWindowPreferences
import com.example.myapplication.data.calendar.CalendarAuthState
import com.example.myapplication.data.calendar.CalendarResult
import com.example.myapplication.data.calendar.GoogleAuthManager
import com.example.myapplication.data.calendar.GoogleCalendarSync
import com.example.myapplication.data.calendar.SignOutResult
import com.example.myapplication.work.FreeTimeCheckWorker
import com.example.myapplication.work.AndroidTaskNotificationScheduler
import com.example.myapplication.work.TaskNotificationScheduler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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
        WorkManagerFreeTimeCheckScheduler(application),
    private val notificationScheduler: TaskNotificationScheduler =
        AndroidTaskNotificationScheduler(application),
    private val notificationWindowPreferences: NotificationWindowPreferences =
        NotificationWindowPreferences.get(application)
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

    /** 通知有効時間帯。設定画面の初期値・保存に使う。 */
    private val _notificationWindow = MutableStateFlow(notificationWindowPreferences.get())
    val notificationWindow: StateFlow<NotificationWindow> = _notificationWindow.asStateFlow()

    fun saveNotificationWindow(window: NotificationWindow) {
        notificationWindowPreferences.set(window)
        _notificationWindow.value = window
    }

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
     * タスク編集時も同じ Mutex でカレンダー同期を直列化し、部分更新同士の競合を防ぐ。
     */
    private val calendarMutexes = mutableMapOf<Int, Mutex>()

    /** タスク ID に対応する Mutex を取得する（無ければ作成）。 */
    private fun mutexFor(taskId: Int): Mutex = synchronized(calendarMutexes) {
        calendarMutexes.getOrPut(taskId) { Mutex() }
    }

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
        addToCalendar: Boolean = false,
        eventHasTime: Boolean = false,
        notificationTime: Long? = null
    ) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            val task = Task(
                title = trimmed,
                deadline = deadline,
                importance = importance,
                urgency = urgency,
                categoryId = categoryId,
                eventHasTime = eventHasTime,
                notificationTime = notificationTime
            )
            val id = repository.insert(task)
            val saved = task.copy(id = id)

            val subTasks = subTaskTitles
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapIndexed { index, subTitle ->
                    SubTask(taskId = id, title = subTitle, sortOrder = index)
                }
            if (subTasks.isNotEmpty()) repository.insertSubTasks(subTasks)

            if (addToCalendar) {
                when (val result = calendarSync.insertEvent(saved, categoryNameOf(saved), subTasks)) {
                    is CalendarResult.Success ->
                        // 全列を上書きすると並行する別更新が失われる恐れがあるので、calendarEventId だけ更新
                        repository.updateCalendarEventId(saved.id, result.value)
                    else -> notifyCalendarFailure(result)
                }
            }

            if (notificationTime != null) {
                notificationScheduler.schedule(saved)
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
            mutexFor(task.id).withLock {
                // Mutex 取得後に最新のタスク状態を再取得。
                // 待ち行列で他の処理が既に calendarEventId を書き換えていた場合、二重登録を防ぐため。
                val current = repository.getTaskById(task.id) ?: return@withLock
                if (enabled) {
                    if (current.calendarEventId != null) return@withLock
                    val subTasks = repository.getSubTasksFor(current.id)
                    when (val result = calendarSync.insertEvent(current, categoryNameOf(current), subTasks)) {
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
     * タスク名を変更する。連携済み（calendarEventId != null）ならカレンダー側の予定タイトルも合わせて更新する。
     * タスク ID 単位の Mutex で他のカレンダー操作と直列化する。
     */
    fun renameTask(task: Task, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty() || trimmed == task.title) return
        viewModelScope.launch {
            mutexFor(task.id).withLock {
                // Mutex 取得後に最新状態を再取得。待ち行列で他の処理が変更を済ませていた場合に備える。
                val current = repository.getTaskById(task.id) ?: return@withLock
                if (trimmed.isEmpty() || trimmed == current.title) return@withLock
                repository.updateTitle(current.id, trimmed)
                doSyncToCalendar(current.copy(title = trimmed))
            }
        }
    }

    fun toggleSubTaskCompleted(subTask: SubTask) {
        viewModelScope.launch {
            repository.updateSubTask(subTask.copy(isCompleted = !subTask.isCompleted))
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            mutexFor(task.id).withLock {
                // Mutex 取得後に最新状態を再取得する。連携ON直後など、呼び出し側が
                // 渡した task の calendarEventId が既に古くなっている可能性があるため。
                val current = repository.getTaskById(task.id) ?: return@withLock

                // 取り消しに備えて、削除前にサブタスクも読み出しておく
                val subTasks = repository.getSubTasksFor(current.id)
                repository.delete(current)
                notificationScheduler.cancel(current.id)

                // カレンダー側を消せたかどうかは取り消し時の判断材料になるので必ず控える
                val eventDeleted = current.calendarEventId?.let { eventId ->
                    val result = calendarSync.deleteEvent(eventId)
                    notifyCalendarFailure(result)
                    result is CalendarResult.Success
                } ?: false

                lastDeleted = DeletedTask(current, subTasks, eventDeleted)
            }
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
            if (task.notificationTime != null) notificationScheduler.schedule(task)

            // 予定を消せていなかった場合はカレンダー側にまだ残っている。
            // 作り直すと二重になり、古い ID も上書きで失われて孤立するので、元の ID をそのまま戻す
            if (task.calendarEventId == null || !deleted.calendarEventDeleted) return@launch

            // 予定は確かに消えているので、連携を復活させるには作り直すしかない
            when (val result = calendarSync.insertEvent(task, categoryNameOf(task), deleted.subTasks)) {
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
     * 呼び出し側はタスク ID 単位の Mutex を取得済みであること。
     */
    private suspend fun doSyncToCalendar(task: Task) {
        val eventId = task.calendarEventId ?: return
        val name = categoryNameOf(task)
        val subTasks = repository.getSubTasksFor(task.id)
        when (val result = calendarSync.updateEvent(eventId, task, name, subTasks)) {
            is CalendarResult.Success -> Unit
            is CalendarResult.NotFound -> {
                when (val recreated = calendarSync.insertEvent(task, name, subTasks)) {
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

    /**
     * [doSyncToCalendar] をタスク ID 単位の Mutex で直列化して実行する。
     */
    private suspend fun syncToCalendar(task: Task) {
        mutexFor(task.id).withLock {
            doSyncToCalendar(task)
        }
    }

    /**
     * 予定の時刻指定を変更する。連携済み（calendarEventId != null）ならカレンダー側の
     * 予定も合わせて更新する（[doSyncToCalendar] を再利用）。
     * タスク ID 単位の Mutex で他のカレンダー操作と直列化する。
     */
    fun updateEventTime(task: Task, eventHasTime: Boolean, newDeadline: Long) {
        viewModelScope.launch {
            mutexFor(task.id).withLock {
                val current = repository.getTaskById(task.id) ?: return@withLock
                if (eventHasTime == current.eventHasTime && newDeadline == current.deadline) return@withLock
                repository.updateEventTime(current.id, newDeadline, eventHasTime)
                doSyncToCalendar(current.copy(deadline = newDeadline, eventHasTime = eventHasTime))
            }
        }
    }

    /**
     * 手動通知時刻を変更する。既存の予約を解除してから、新しい時刻があれば予約し直す。
     * null を渡すと手動通知を解除するだけになる。
     * タスク ID 単位の Mutex で他のカレンダー操作と直列化する。
     */
    fun updateNotificationTime(task: Task, newNotificationTime: Long?) {
        viewModelScope.launch {
            mutexFor(task.id).withLock {
                val current = repository.getTaskById(task.id) ?: return@withLock
                if (newNotificationTime != null && newNotificationTime == current.notificationTime) return@withLock
                repository.updateNotificationTime(current.id, newNotificationTime)
                notificationScheduler.cancel(current.id)
                if (newNotificationTime != null) {
                    notificationScheduler.schedule(current.copy(notificationTime = newNotificationTime))
                }
            }
        }
    }

    /**
     * [TaskEditDialog] の確定結果を、変更があった項目だけ適用する。
     * タイトル・予定時刻・通知時刻の複数変更があっても、単一 coroutine でまとめて処理し、
     * 全変更を合成した最新 Task でカレンダー同期を 1 回だけ行う。
     */
    fun applyTaskEdit(task: Task, result: TaskEditResult) {
        viewModelScope.launch {
            mutexFor(task.id).withLock {
                val current = repository.getTaskById(task.id) ?: return@withLock

                val trimmedTitle = result.title.trim()
                val titleChanged = trimmedTitle.isNotEmpty() && trimmedTitle != current.title
                val eventTimeChanged = result.eventHasTime != current.eventHasTime || result.deadline != current.deadline
                val notificationTimeChanged = result.notificationTime != current.notificationTime

                // 変更が無ければ何もしない
                if (!titleChanged && !eventTimeChanged && !notificationTimeChanged) return@withLock

                // 必要な列だけを部分更新する
                if (titleChanged) repository.updateTitle(current.id, trimmedTitle)
                if (eventTimeChanged) repository.updateEventTime(current.id, result.deadline, result.eventHasTime)
                if (notificationTimeChanged) repository.updateNotificationTime(current.id, result.notificationTime)

                // DB 更新後の最新 Task を合成。これを 1 回のカレンダー同期に使うことで、
                // タイトルと予定時刻のどちらかが巻き戻る競合を防ぐ。
                val merged = current.copy(
                    title = if (titleChanged) trimmedTitle else current.title,
                    deadline = if (eventTimeChanged) result.deadline else current.deadline,
                    eventHasTime = if (eventTimeChanged) result.eventHasTime else current.eventHasTime,
                    notificationTime = if (notificationTimeChanged) result.notificationTime else current.notificationTime
                )

                // カレンダー側に影響する変更（タイトル・予定時刻）があるときだけ同期する。
                // 通知時刻だけの変更でCalendar APIを呼ぶと無駄なリクエストになる。
                if (titleChanged || eventTimeChanged) {
                    doSyncToCalendar(merged)
                }

                // 通知時刻が変わったら、既存予約を解除して新しい時刻で予約し直す
                if (notificationTimeChanged) {
                    notificationScheduler.cancel(current.id)
                    if (result.notificationTime != null) {
                        notificationScheduler.schedule(merged)
                    }
                }
            }
        }
    }
}
