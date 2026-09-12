package com.mikke.discovery.shared

/**
 * Room版（`app/src/main/java/com/example/myapplication/data/Task.kt`）からDB依存を除いた
 * プレーンな値クラス。DBとの変換は Task 6 の Repository が担う。
 */
data class Task(
    val id: Int = 0,
    val title: String,
    val deadline: Long,
    val importance: Int, // 1-3
    val urgency: Int,    // 1-3
    /** 所属カテゴリ。null は「未分類」（カテゴリが削除されたタスク）。 */
    val categoryId: Int? = null,
    val isCompleted: Boolean = false,
    val progress: Int = 0, // 0-100
    val createdAt: Long = 0L,
    val status: TaskStatus = TaskStatus.TODO
) {
    /** 並び順・色分けに使う優先度スコア。4（低）〜12（高）。 */
    val priorityScore: Int
        get() = importance * 3 + urgency

    /** 未完了かつ締切を過ぎているか。 */
    fun isOverdue(now: Long): Boolean = !isCompleted && deadline < now
}
