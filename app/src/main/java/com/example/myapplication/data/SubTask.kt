package com.example.myapplication.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "subtasks",
    foreignKeys = [
        ForeignKey(
            entity = Task::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("taskId")]
)
data class SubTask(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val taskId: Int,
    val title: String,
    val isCompleted: Boolean = false,
    /** 表示順。追加した順に 0, 1, 2... を振る。 */
    val sortOrder: Int = 0
)

/** タスクと、それにぶら下がるサブタスクをまとめて扱うための組。 */
data class TaskWithSubTasks(
    @Embedded val task: Task,
    @Relation(parentColumn = "id", entityColumn = "taskId")
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
