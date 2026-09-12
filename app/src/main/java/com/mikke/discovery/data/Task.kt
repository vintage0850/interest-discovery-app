package com.mikke.discovery.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** タスクの進行状態。「完了したまま進行中」という状態は持たせない。 */
enum class TaskStatus { TODO, IN_PROGRESS }

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            // カテゴリを消してもタスクは残し、「未分類」（categoryId = null）に落とす
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("categoryId")]
)
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val deadline: Long,
    val importance: Int, // 1-3
    val urgency: Int,    // 1-3 (calculated or manual)
    /** 所属カテゴリ。null は「未分類」（カテゴリが削除されたタスク）。 */
    val categoryId: Int? = null,
    val isCompleted: Boolean = false,
    val progress: Int = 0, // 0-100
    val notificationTime: Long? = null,
    /**
     * true なら [deadline] の時刻部分をカレンダー予定の開始時刻として使う（時刻指定予定）。
     * false なら従来通り [deadline] の暦日で終日予定を作る。
     */
    val eventHasTime: Boolean = false,
    /**
     * 書き出し済みの Google カレンダー予定の ID。未連携なら null。
     * Calendar REST API のイベント ID は文字列（例: "abc123def456"）なので String で持つ。
     */
    val calendarEventId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /**
     * 「始めさせる」通知の始めるボタンで IN_PROGRESS になる。
     * 完了操作（isCompleted を true にする）が行われたら強制的に TODO に戻す。
     */
    val status: TaskStatus = TaskStatus.TODO
) {
    /** 並び順・色分けに使う優先度スコア。4（低）〜12（高）。 */
    val priorityScore: Int
        get() = importance * 3 + urgency

    /** 未完了かつ締切を過ぎているか。 */
    fun isOverdue(now: Long = System.currentTimeMillis()): Boolean =
        !isCompleted && deadline < now
}
