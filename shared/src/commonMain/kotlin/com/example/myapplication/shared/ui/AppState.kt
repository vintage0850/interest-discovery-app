package com.example.myapplication.shared.ui

import com.example.myapplication.shared.Category
import com.example.myapplication.shared.SubTask
import com.example.myapplication.shared.Task
import com.example.myapplication.shared.TaskRepository
import com.example.myapplication.shared.TaskWithSubTasks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * `androidx.lifecycle.ViewModel`（Android専用）を使わない、Compose Multiplatform共通の状態管理。
 * Android版 `TaskViewModel` からカレンダー連携・通知関連を除いたCRUD部分の移植。
 * ライフサイクル管理（`coroutineScope`のキャンセル）は呼び出し側（Android/iOSそれぞれのホスト）の責務。
 */
class AppState(
    private val repository: TaskRepository,
    private val coroutineScope: CoroutineScope
) {
    /**
     * `coroutineScope` から派生した、`SupervisorJob` 付きの内部スコープ。
     * これを使わずに `coroutineScope.launch` で起動した子コルーチンが例外を投げると、
     * 親の `Job` ごとキャンセルされ、`stateIn` の内部コレクターも停止し、
     * それ以降のすべての `launch` が静かに no-op になってしまう。
     * `SupervisorJob` を挟むことで、1つの操作の失敗が他に波及しないようにする。
     */
    private val supervisedScope = CoroutineScope(
        coroutineScope.coroutineContext + SupervisorJob(parent = coroutineScope.coroutineContext[Job])
    )

    val allTasks: StateFlow<List<TaskWithSubTasks>> = repository.allTasks.stateIn(
        scope = coroutineScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val categories: StateFlow<List<Category>> = repository.categories.stateIn(
        scope = coroutineScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** 直前に削除したタスク。取り消し（[undoDelete]）のために保持する。 */
    private var lastDeleted: DeletedTask? = null

    private data class DeletedTask(val task: Task, val subTasks: List<SubTask>)

    // ---- カテゴリ ----

    fun addCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        supervisedScope.launch {
            try {
                if (repository.addCategory(trimmed)) {
                    _messages.tryEmit("「$trimmed」を追加しました")
                } else {
                    _messages.tryEmit("「$trimmed」はすでにあります")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit("操作に失敗しました")
            }
        }
    }

    fun renameCategory(category: Category, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == category.name) return
        supervisedScope.launch {
            try {
                if (repository.renameCategory(category, trimmed)) {
                    _messages.tryEmit("「$trimmed」に変更しました")
                } else {
                    _messages.tryEmit("「$trimmed」はすでにあります")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit("操作に失敗しました")
            }
        }
    }

    /**
     * カテゴリを削除する。中のタスクは消えず「未分類」に移る
     * （tasks.categoryId の外部キーが ON DELETE SET NULL のため DB 側で処理される）。
     */
    fun deleteCategory(category: Category) {
        supervisedScope.launch {
            try {
                val moved = repository.countTasksInCategory(category.id)
                repository.deleteCategory(category)
                _messages.tryEmit(
                    if (moved > 0) {
                        "「${category.name}」を削除しました（${moved}件を未分類に移動）"
                    } else {
                        "「${category.name}」を削除しました"
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit("操作に失敗しました")
            }
        }
    }

    /** 削除の確認ダイアログに件数を出すために使う。 */
    suspend fun countTasksInCategory(categoryId: Int): Int =
        repository.countTasksInCategory(categoryId)

    // ---- タスク ----

    @OptIn(kotlin.time.ExperimentalTime::class)
    fun addTask(
        title: String,
        deadline: Long,
        importance: Int,
        urgency: Int,
        categoryId: Int?,
        subTaskTitles: List<String> = emptyList()
    ) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return

        supervisedScope.launch {
            try {
                val task = Task(
                    title = trimmed,
                    deadline = deadline,
                    importance = importance,
                    urgency = urgency,
                    categoryId = categoryId,
                    createdAt = kotlin.time.Clock.System.now().toEpochMilliseconds()
                )
                val id = repository.insert(task)

                val subTasks = subTaskTitles
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .mapIndexed { index, subTitle ->
                        SubTask(taskId = id, title = subTitle, sortOrder = index)
                    }
                if (subTasks.isNotEmpty()) repository.insertSubTasks(subTasks)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit("保存に失敗しました")
            }
        }
    }

    fun toggleCompleted(task: Task) {
        supervisedScope.launch {
            try {
                repository.update(task.copy(isCompleted = !task.isCompleted))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit("保存に失敗しました")
            }
        }
    }

    fun renameTask(task: Task, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty() || trimmed == task.title) return
        supervisedScope.launch {
            try {
                repository.update(task.copy(title = trimmed))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit("保存に失敗しました")
            }
        }
    }

    fun toggleSubTaskCompleted(subTask: SubTask) {
        supervisedScope.launch {
            try {
                repository.updateSubTask(subTask.copy(isCompleted = !subTask.isCompleted))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit("保存に失敗しました")
            }
        }
    }

    fun deleteTask(task: Task) {
        supervisedScope.launch {
            try {
                // 取り消しに備えて、削除前にサブタスクも読み出しておく
                val subTasks = repository.getSubTasksFor(task.id)
                repository.delete(task)
                lastDeleted = DeletedTask(task, subTasks)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit("保存に失敗しました")
            }
        }
    }

    /**
     * 直前の削除を取り消す。`:shared` の insert は常に新しい id を採番するため、
     * 元と同じ id では復元されない（内容は同じ）。
     */
    fun undoDelete() {
        val deleted = lastDeleted ?: return
        lastDeleted = null
        supervisedScope.launch {
            try {
                val id = repository.insert(deleted.task)
                if (deleted.subTasks.isNotEmpty()) {
                    repository.insertSubTasks(deleted.subTasks.map { it.copy(id = 0, taskId = id) })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit("保存に失敗しました")
            }
        }
    }
}
