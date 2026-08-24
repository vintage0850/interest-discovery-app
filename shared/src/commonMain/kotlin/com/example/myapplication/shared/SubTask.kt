package com.example.myapplication.shared

data class SubTask(
    val id: Int = 0,
    val taskId: Int,
    val title: String,
    val isCompleted: Boolean = false,
    /** 表示順。追加した順に 0, 1, 2... を振る。 */
    val sortOrder: Int = 0
)

/** タスクと、それにぶら下がるサブタスクをまとめて扱うための組。 */
data class TaskWithSubTasks(
    val task: Task,
    val subTasks: List<SubTask> = emptyList()
) {
    /** サブタスクがあればその完了率、なければタスク自身の進捗を返す。 */
    val progress: Int
        get() = when {
            subTasks.isNotEmpty() -> subTasks.count { it.isCompleted } * 100 / subTasks.size
            task.isCompleted -> 100
            else -> task.progress
        }
}
