package com.mikke.discovery.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 「この空き時間帯は通知済み」を覚えておくためのレコード。
 * FreeTimeCheckWorker が同じ空き時間帯中に何度も通知しないための重複防止に使う。
 */
@Entity(tableName = "notified_slots")
data class NotifiedSlot(
    @PrimaryKey val startMillis: Long,
    val endMillis: Long
)
