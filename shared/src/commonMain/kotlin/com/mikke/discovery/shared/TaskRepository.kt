package com.mikke.discovery.shared

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.mikke.discovery.shared.db.DatabaseDriverFactory
import com.mikke.discovery.shared.db.SharedDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

/**
 * Room版（`app/.../data/TaskRepository.kt`）のうち、カレンダー連携・通知関連を除いた
 * CRUD部分をSQLDelightで書き直したもの。Phase 1のUI（後続プラン）が使う。
 */
class TaskRepository(driverFactory: DatabaseDriverFactory) {
    private val database = SharedDatabase(driverFactory.createDriver())
    private val taskQueries = database.taskQueries
    private val subTaskQueries = database.subTaskQueries
    private val categoryQueries = database.categoryQueries

    // Task/SubTaskどちらの変化にも反応する必要があるため、両テーブルをそれぞれ
    // observe queryで流し、combineでメモリ上だけで突き合わせる（DB I/Oなしの純粋処理）。
    // 以前は末尾の.mapでサブタスクを命令的に引いていたが、それはSQLDelightの
    // クエリ無効化の監視対象外のため、subTaskへの変更がallTasksに反映されなかった。
    val allTasks: Flow<List<TaskWithSubTasks>> =
        combine(
            taskQueries.selectAll(::toTask).asFlow().mapToList(Dispatchers.Default),
            subTaskQueries.selectAllOrdered(::toSubTask).asFlow().mapToList(Dispatchers.Default)
        ) { tasks, subTasks ->
            val byTaskId = subTasks.groupBy { it.taskId }
            tasks.map { task ->
                TaskWithSubTasks(
                    task = task,
                    subTasks = byTaskId[task.id].orEmpty()
                )
            }
        }

    val categories: Flow<List<Category>> =
        categoryQueries.selectAll(::toCategory).asFlow().mapToList(Dispatchers.Default)

    suspend fun insert(task: Task): Int = withContext(Dispatchers.Default) {
        taskQueries.transactionWithResult {
            taskQueries.insert(
                title = task.title,
                deadline = task.deadline,
                importance = task.importance.toLong(),
                urgency = task.urgency.toLong(),
                categoryId = task.categoryId?.toLong(),
                isCompleted = task.isCompleted,
                progress = task.progress.toLong(),
                notificationTime = null,
                calendarEventId = null,
                createdAt = task.createdAt,
                status = task.status.name
            )
            taskQueries.lastInsertRowId().executeAsOne().toInt()
        }
    }

    suspend fun update(task: Task) = withContext(Dispatchers.Default) {
        taskQueries.update(
            title = task.title,
            deadline = task.deadline,
            importance = task.importance.toLong(),
            urgency = task.urgency.toLong(),
            categoryId = task.categoryId?.toLong(),
            isCompleted = task.isCompleted,
            progress = task.progress.toLong(),
            id = task.id.toLong()
        )
    }

    suspend fun delete(task: Task) = withContext(Dispatchers.Default) {
        taskQueries.delete(task.id.toLong())
    }

    suspend fun getTaskById(id: Int): Task? = withContext(Dispatchers.Default) {
        taskQueries.selectById(id.toLong(), ::toTask).executeAsOneOrNull()
    }

    suspend fun insertSubTasks(subTasks: List<SubTask>) = withContext(Dispatchers.Default) {
        subTaskQueries.transaction {
            subTasks.forEach { subTask ->
                subTaskQueries.insert(
                    taskId = subTask.taskId.toLong(),
                    title = subTask.title,
                    isCompleted = subTask.isCompleted,
                    sortOrder = subTask.sortOrder.toLong()
                )
            }
        }
    }

    suspend fun updateSubTask(subTask: SubTask) = withContext(Dispatchers.Default) {
        subTaskQueries.update(
            title = subTask.title,
            isCompleted = subTask.isCompleted,
            sortOrder = subTask.sortOrder.toLong(),
            id = subTask.id.toLong()
        )
    }

    suspend fun deleteSubTask(subTask: SubTask) = withContext(Dispatchers.Default) {
        subTaskQueries.delete(subTask.id.toLong())
    }

    suspend fun getSubTasksFor(taskId: Int): List<SubTask> = withContext(Dispatchers.Default) {
        subTaskQueries.selectForTask(taskId.toLong(), ::toSubTask).executeAsList()
    }

    /**
     * 同名のカテゴリがすでにある場合は追加せず false を返す。
     * `selectMaxSortOrder()`（引数なし）はSQLDelightが自動生成する0引数オーバーロードで、
     * 単一カラムでも `Long?` には収まらず `SelectMaxSortOrder(val MAX: Long?)` という
     * データクラスでラップされて返る（`MAX(sortOrder)` が集約でnull許容のため、
     * `countByCategoryId` のような非null単一カラムの直接返却とは異なる）。
     * そのため `.MAX` で中身を取り出す必要がある。
     */
    suspend fun addCategory(name: String): Boolean = withContext(Dispatchers.Default) {
        categoryQueries.transactionWithResult {
            if (categoryQueries.selectByName(name).executeAsOneOrNull() != null) {
                false
            } else {
                val nextOrder = (categoryQueries.selectMaxSortOrder().executeAsOne().MAX ?: -1L) + 1
                categoryQueries.insert(name, nextOrder)
                true
            }
        }
    }

    /** リネーム。同名のカテゴリが既にある場合は変更せず false を返す。 */
    suspend fun renameCategory(category: Category, newName: String): Boolean =
        withContext(Dispatchers.Default) {
            categoryQueries.transactionWithResult {
                val conflict = categoryQueries
                    .selectConflictingName(name = newName, excludingId = category.id.toLong())
                    .executeAsOneOrNull()
                if (conflict != null) {
                    false
                } else {
                    categoryQueries.updateName(newName, category.id.toLong())
                    true
                }
            }
        }

    /** 削除。所属していたタスクは消えず「未分類」になる（DB外部キーの ON DELETE SET NULL）。 */
    suspend fun deleteCategory(category: Category) = withContext(Dispatchers.Default) {
        categoryQueries.delete(category.id.toLong())
    }

    suspend fun countTasksInCategory(categoryId: Int): Int = withContext(Dispatchers.Default) {
        taskQueries.countByCategoryId(categoryId.toLong()).executeAsOne().toInt()
    }

    private fun toTask(
        id: Long,
        title: String,
        deadline: Long,
        importance: Long,
        urgency: Long,
        categoryId: Long?,
        isCompleted: Boolean,
        progress: Long,
        notificationTime: Long?,
        calendarEventId: String?,
        createdAt: Long,
        status: String
    ) = Task(
        id = id.toInt(),
        title = title,
        deadline = deadline,
        importance = importance.toInt(),
        urgency = urgency.toInt(),
        categoryId = categoryId?.toInt(),
        isCompleted = isCompleted,
        progress = progress.toInt(),
        createdAt = createdAt,
        status = TaskStatus.valueOf(status)
    )

    private fun toSubTask(
        id: Long,
        taskId: Long,
        title: String,
        isCompleted: Boolean,
        sortOrder: Long
    ) = SubTask(
        id = id.toInt(),
        taskId = taskId.toInt(),
        title = title,
        isCompleted = isCompleted,
        sortOrder = sortOrder.toInt()
    )

    private fun toCategory(id: Long, name: String, sortOrder: Long) =
        Category(id = id.toInt(), name = name, sortOrder = sortOrder.toInt())
}
