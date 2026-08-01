package com.example.myapplication.data

import kotlinx.coroutines.flow.Flow

class TaskRepository(private val taskDao: TaskDao) {
    val allTasks: Flow<List<TaskWithSubTasks>> = taskDao.getAllTasks()
    val activeTasks: Flow<List<Task>> = taskDao.getActiveTasks()
    val categories: Flow<List<Category>> = taskDao.getCategories()

    /** 挿入された行の id を返す。 */
    suspend fun insert(task: Task): Int = taskDao.insertTask(task).toInt()

    suspend fun update(task: Task) = taskDao.updateTask(task)

    suspend fun delete(task: Task) = taskDao.deleteTask(task)

    suspend fun getTaskById(id: Int) = taskDao.getTaskById(id)

    suspend fun insertSubTasks(subTasks: List<SubTask>) = taskDao.insertSubTasks(subTasks)

    suspend fun updateSubTask(subTask: SubTask) = taskDao.updateSubTask(subTask)

    suspend fun deleteSubTask(subTask: SubTask) = taskDao.deleteSubTask(subTask)

    suspend fun getSubTasksFor(taskId: Int) = taskDao.getSubTasksFor(taskId)

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
