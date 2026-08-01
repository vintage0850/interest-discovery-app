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
