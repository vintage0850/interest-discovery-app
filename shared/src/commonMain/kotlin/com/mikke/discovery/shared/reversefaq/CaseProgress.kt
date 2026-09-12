package com.mikke.discovery.shared.reversefaq

/**
 * 案件内の確認済み質問の進捗。
 */
data class CaseProgress(
    val confirmedCount: Int,
    val totalCount: Int
) {
    /** 0〜100の整数パーセント。総数が0なら0。 */
    val percentage: Int
        get() = if (totalCount == 0) 0 else confirmedCount * 100 / totalCount
}
