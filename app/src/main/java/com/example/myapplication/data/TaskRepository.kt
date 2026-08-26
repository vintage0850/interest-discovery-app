package com.example.myapplication.data

import kotlinx.coroutines.flow.Flow

open class TaskRepository(private val taskDao: TaskDao) {
    val allTasks: Flow<List<TaskWithSubTasks>> = taskDao.getAllTasks()
    val activeTasks: Flow<List<Task>> = taskDao.getActiveTasks()
    val categories: Flow<List<Category>> = taskDao.getCategories()

    /** 挿入された行の id を返す。 */
    open suspend fun insert(task: Task): Int = taskDao.insertTask(task).toInt()

    open suspend fun update(task: Task) = taskDao.updateTask(task)

    /**
     * calendarEventId だけを更新する。
     * カレンダー連携の結果保存時に、Task 全列を上書きして他の変更を巻き戻さないため。
     */
    open suspend fun updateCalendarEventId(taskId: Int, calendarEventId: String?) =
        taskDao.updateCalendarEventId(taskId, calendarEventId)

    /**
     * title だけを更新する。全列上書きだと完了状態やカレンダー連携など
     * 他の変更を巻き戻す恐れがあるため。
     */
    open suspend fun updateTitle(taskId: Int, title: String) =
        taskDao.updateTaskTitle(taskId, title)

    /** status だけを更新する。通知の「始める」アクションから使う部分更新。 */
    open suspend fun updateStatus(taskId: Int, status: TaskStatus) =
        taskDao.updateTaskStatus(taskId, status)

    /** 予定の日時と時刻指定の有無だけを更新する部分更新。 */
    open suspend fun updateEventTime(taskId: Int, deadline: Long, eventHasTime: Boolean) =
        taskDao.updateEventTime(taskId, deadline, eventHasTime)

    /** 通知時刻だけを更新する部分更新。null で手動通知を解除する。 */
    open suspend fun updateNotificationTime(taskId: Int, notificationTime: Long?) =
        taskDao.updateNotificationTime(taskId, notificationTime)

    /** 端末再起動後にアラームを再登録する対象（未完了かつ通知時刻が未来）を返す。 */
    open suspend fun getTasksWithFutureNotification(now: Long): List<Task> =
        taskDao.getTasksWithFutureNotification(now)

    /** 「始めさせる」通知の対象候補を1件選ぶ。未完了かつ未着手で優先度最大のタスク。 */
    open suspend fun getTopEligibleTaskForNotification(): Task? =
        taskDao.getTopEligibleTaskForNotification()

    open suspend fun isSlotNotified(startMillis: Long): Boolean =
        taskDao.isSlotNotified(startMillis)

    open suspend fun insertNotifiedSlot(slot: NotifiedSlot) =
        taskDao.insertNotifiedSlot(slot)

    open suspend fun deleteNotifiedSlotsOlderThan(cutoffMillis: Long) =
        taskDao.deleteNotifiedSlotsOlderThan(cutoffMillis)

    open suspend fun delete(task: Task) = taskDao.deleteTask(task)

    open suspend fun getTaskById(id: Int) = taskDao.getTaskById(id)

    open suspend fun insertSubTasks(subTasks: List<SubTask>) = taskDao.insertSubTasks(subTasks)

    open suspend fun updateSubTask(subTask: SubTask) = taskDao.updateSubTask(subTask)

    open suspend fun deleteSubTask(subTask: SubTask) = taskDao.deleteSubTask(subTask)

    open suspend fun getSubTasksFor(taskId: Int) = taskDao.getSubTasksFor(taskId)

    /**
     * 末尾に新しいカテゴリを追加する。
     * 同名のカテゴリがすでにある場合は追加せず false を返す。
     */
    suspend fun addCategory(name: String): Boolean {
        val nextOrder = (taskDao.getMaxCategorySortOrder() ?: -1) + 1
        return taskDao.insertCategory(Category(name = name, sortOrder = nextOrder)) != -1L
    }

    /** リネーム。同名のカテゴリが既にある場合は変更せず false を返す。 */
    suspend fun renameCategory(category: Category, newName: String): Boolean =
        taskDao.updateCategory(category.copy(name = newName)) > 0

    /** 削除。所属していたタスクは消えず「未分類」になる。 */
    suspend fun deleteCategory(category: Category) = taskDao.deleteCategory(category)

    suspend fun countTasksInCategory(categoryId: Int) = taskDao.countTasksInCategory(categoryId)
}
