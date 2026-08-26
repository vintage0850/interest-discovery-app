package com.example.myapplication.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    // 未完了を先頭に、次に優先度（重要度×3＋緊急度）の高い順、同点なら締切が近い順
    @Transaction
    @Query(
        "SELECT * FROM tasks " +
            "ORDER BY isCompleted ASC, (importance * 3 + urgency) DESC, deadline ASC"
    )
    fun getAllTasks(): Flow<List<TaskWithSubTasks>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Int): Task?

    /** 採番された（または REPLACE で置き換えた）行の id を返す。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long

    @Update
    suspend fun updateTask(task: Task)

    /**
     * calendarEventId だけを更新する。
     * カレンダー連携結果を保存するとき、Task 全列を上書きして他の変更を巻き戻さないため。
     */
    @Query("UPDATE tasks SET calendarEventId = :calendarEventId WHERE id = :taskId")
    suspend fun updateCalendarEventId(taskId: Int, calendarEventId: String?)

    /**
     * title だけを更新する。calendarEventId 更新と同じ理由で、Task 全列を上書きしないため。
     */
    @Query("UPDATE tasks SET title = :title WHERE id = :taskId")
    suspend fun updateTaskTitle(taskId: Int, title: String)

    /**
     * status だけを更新する。「始める」通知アクションからの更新は、通知受信の
     * BroadcastReceiver からバックグラウンドで走るため、他列を巻き戻さない部分更新にする。
     */
    @Query("UPDATE tasks SET status = :status WHERE id = :taskId")
    suspend fun updateTaskStatus(taskId: Int, status: TaskStatus)

    /**
     * 「始めさせる」通知の対象候補を1件選ぶ。未完了かつ未着手（TODO）のタスクのうち、
     * 優先度（重要度×3＋緊急度）が最も高いものを返す。無ければ null。
     */
    @Query(
        "SELECT * FROM tasks WHERE isCompleted = 0 AND status = 'TODO' " +
            "ORDER BY (importance * 3 + urgency) DESC, deadline ASC LIMIT 1"
    )
    suspend fun getTopEligibleTaskForNotification(): Task?

    /**
     * 予定の日時（deadline）と時刻指定の有無だけを更新する。
     * カレンダーへの反映は呼び出し側（TaskViewModel）の責務。
     */
    @Query("UPDATE tasks SET deadline = :deadline, eventHasTime = :eventHasTime WHERE id = :taskId")
    suspend fun updateEventTime(taskId: Int, deadline: Long, eventHasTime: Boolean)

    /** 通知時刻だけを更新する。null にすると手動通知を解除する。 */
    @Query("UPDATE tasks SET notificationTime = :notificationTime WHERE id = :taskId")
    suspend fun updateNotificationTime(taskId: Int, notificationTime: Long?)

    /**
     * 端末再起動後、まだ発火していない手動通知の予約を復元するために使う。
     * 未完了かつ、指定時刻がまだ先のタスクだけを返す。
     */
    @Query(
        "SELECT * FROM tasks WHERE isCompleted = 0 AND notificationTime IS NOT NULL " +
            "AND notificationTime > :now"
    )
    suspend fun getTasksWithFutureNotification(now: Long): List<Task>

    // ---- 通知済みスロット ----

    @Query("SELECT EXISTS(SELECT 1 FROM notified_slots WHERE startMillis = :startMillis)")
    suspend fun isSlotNotified(startMillis: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotifiedSlot(slot: NotifiedSlot)

    /** 肥大化を避けるため、指定時刻より前に始まった古いレコードを消す。 */
    @Query("DELETE FROM notified_slots WHERE startMillis < :cutoffMillis")
    suspend fun deleteNotifiedSlotsOlderThan(cutoffMillis: Long)

    @Delete
    suspend fun deleteTask(task: Task)

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 ORDER BY deadline ASC")
    fun getActiveTasks(): Flow<List<Task>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubTasks(subTasks: List<SubTask>)

    @Update
    suspend fun updateSubTask(subTask: SubTask)

    @Delete
    suspend fun deleteSubTask(subTask: SubTask)

    @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY sortOrder ASC")
    suspend fun getSubTasksFor(taskId: Int): List<SubTask>

    // ---- カテゴリ ----

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
    fun getCategories(): Flow<List<Category>>

    /** 同名のカテゴリがあれば何もせず -1 を返す（name に一意制約があるため）。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(category: Category): Long

    /** 同名にリネームしようとした場合は 0 件更新になる。 */
    @Update(onConflict = OnConflictStrategy.IGNORE)
    suspend fun updateCategory(category: Category): Int

    /** タスクは消えず、FK の ON DELETE SET NULL で「未分類」に移る。 */
    @Delete
    suspend fun deleteCategory(category: Category)

    @Query("SELECT MAX(sortOrder) FROM categories")
    suspend fun getMaxCategorySortOrder(): Int?

    @Query("SELECT COUNT(*) FROM tasks WHERE categoryId = :categoryId")
    suspend fun countTasksInCategory(categoryId: Int): Int
}
