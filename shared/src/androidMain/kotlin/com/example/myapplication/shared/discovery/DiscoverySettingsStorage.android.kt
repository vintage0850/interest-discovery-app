package com.example.myapplication.shared.discovery

import android.content.Context
import android.content.SharedPreferences
import kotlinx.datetime.Instant

private const val PREFS_NAME = "discovery_settings"
private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
private const val KEY_REMINDER_TIME = "reminder_time"
private const val KEY_DATA_SHARING_ENABLED = "data_sharing_enabled"
private const val KEY_LAST_NOTIFIED_AT = "last_notified_at"
private const val KEY_NOTIFIED_EXPERIMENT_DATES = "notified_experiment_dates"

/** [MyDataSettings] / [NotificationLog] を端末の[SharedPreferences]へ永続化する。 */
class AndroidDiscoverySettingsStorage(private val prefs: SharedPreferences) : DiscoverySettingsStorage {

    override fun load(): MyDataSettings {
        val defaults = MyDataSettings()
        return MyDataSettings(
            notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, defaults.notificationsEnabled),
            reminderTime = prefs.getString(KEY_REMINDER_TIME, defaults.reminderTime) ?: defaults.reminderTime,
            dataSharingEnabled = prefs.getBoolean(KEY_DATA_SHARING_ENABLED, defaults.dataSharingEnabled),
            savedSignalCount = defaults.savedSignalCount
        )
    }

    override fun save(settings: MyDataSettings) {
        prefs.edit()
            .putBoolean(KEY_NOTIFICATIONS_ENABLED, settings.notificationsEnabled)
            .putString(KEY_REMINDER_TIME, settings.reminderTime)
            .putBoolean(KEY_DATA_SHARING_ENABLED, settings.dataSharingEnabled)
            .apply()
    }

    override fun loadNotificationLog(): NotificationLog {
        val lastNotifiedAt = prefs.getString(KEY_LAST_NOTIFIED_AT, null)?.let { Instant.parse(it) }
        val datesRaw = prefs.getString(KEY_NOTIFIED_EXPERIMENT_DATES, "") ?: ""
        val dates = if (datesRaw.isBlank()) {
            emptyMap()
        } else {
            datesRaw.split(",")
                .mapNotNull { entry ->
                    val parts = entry.split(":", limit = 2)
                    if (parts.size == 2) parts[0] to parts[1] else null
                }
                .toMap()
        }
        return NotificationLog(lastNotifiedAt, dates)
    }

    override fun saveNotificationLog(log: NotificationLog) {
        prefs.edit()
            .putString(KEY_LAST_NOTIFIED_AT, log.lastNotifiedAt?.toString())
            .putString(
                KEY_NOTIFIED_EXPERIMENT_DATES,
                log.notifiedExperimentDates.entries.joinToString(",") { "${it.key}:${it.value}" }
            )
            .apply()
    }

    override fun getLastNotifiedWeekKey(sessionId: Int): String? =
        prefs.getString("last_notified_week_key_$sessionId", null)

    override fun saveLastNotifiedWeekKey(sessionId: Int, key: String) {
        prefs.edit()
            .putString("last_notified_week_key_$sessionId", key)
            .commit()
    }

    override fun getLastNotifiedMonthKey(sessionId: Int): String? =
        prefs.getString("last_notified_month_key_$sessionId", null)

    override fun saveLastNotifiedMonthKey(sessionId: Int, key: String) {
        prefs.edit()
            .putString("last_notified_month_key_$sessionId", key)
            .commit()
    }

    companion object {
        fun get(context: Context): AndroidDiscoverySettingsStorage =
            AndroidDiscoverySettingsStorage(
                context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            )
    }
}
