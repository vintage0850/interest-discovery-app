package com.example.myapplication.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * ユーザーが自由に作れるタスクの分類枠。
 *
 * 以前は TaskList という enum で「スキル / 提出 / やりたい」に固定されていたが、
 * 名前も個数もユーザーが決められるようテーブルに移した。
 * 同じ名前が並ぶと選び分けられないので name には一意制約を張っている。
 */
@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)]
)
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    /** タブや選択肢に並ぶ順。追加した順に 0, 1, 2... を振る。 */
    val sortOrder: Int = 0
) {
    companion object {
        /** カテゴリ名の上限。タブに収まる程度に抑える。 */
        const val MAX_NAME_LENGTH = 12

        /** 初回起動時に用意しておく枠。ユーザーは自由に消して構わない。 */
        val DEFAULTS = listOf("スキル", "提出", "やりたい")

        /** カテゴリが消されたタスク（categoryId が null）の表示名。 */
        const val UNCATEGORIZED_LABEL = "未分類"
    }
}
