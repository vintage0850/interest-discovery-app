package com.example.myapplication.data

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalTime

/** 「空き時間です」通知を出してよい時間帯。 */
data class NotificationWindow(val start: LocalTime, val end: LocalTime)

/** 従来ハードコードされていた既定値（8:00〜22:00）。 */
val DEFAULT_NOTIFICATION_WINDOW = NotificationWindow(LocalTime.of(8, 0), LocalTime.of(22, 0))

private const val PREFS_NAME = "notification_window"
private const val KEY_START_MINUTES = "start_minutes"
private const val KEY_END_MINUTES = "end_minutes"

/**
 * 通知有効時間帯の永続化。項目が2つだけの単純な設定なので、DataStore は導入せず
 * [SharedPreferences] をそのまま使う。
 */
class NotificationWindowPreferences(private val prefs: SharedPreferences) {

    fun get(): NotificationWindow {
        val startMinutes = prefs.getInt(KEY_START_MINUTES, DEFAULT_NOTIFICATION_WINDOW.start.toMinutesOfDay())
        val endMinutes = prefs.getInt(KEY_END_MINUTES, DEFAULT_NOTIFICATION_WINDOW.end.toMinutesOfDay())
        return NotificationWindow(
            start = LocalTime.ofSecondOfDay(startMinutes * 60L),
            end = LocalTime.ofSecondOfDay(endMinutes * 60L)
        )
    }

    fun set(window: NotificationWindow) {
        prefs.edit()
            .putInt(KEY_START_MINUTES, window.start.toMinutesOfDay())
            .putInt(KEY_END_MINUTES, window.end.toMinutesOfDay())
            .apply()
    }

    private fun LocalTime.toMinutesOfDay(): Int = hour * 60 + minute

    companion object {
        fun get(context: Context): NotificationWindowPreferences =
            NotificationWindowPreferences(
                context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            )
    }
}
