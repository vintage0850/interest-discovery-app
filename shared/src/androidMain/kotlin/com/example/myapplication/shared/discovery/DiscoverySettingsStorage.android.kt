package com.example.myapplication.shared.discovery

import android.content.Context
import android.content.SharedPreferences

private const val PREFS_NAME = "discovery_settings"
private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
private const val KEY_REMINDER_TIME = "reminder_time"
private const val KEY_DATA_SHARING_ENABLED = "data_sharing_enabled"

/** [MyDataSettings] を端末の[SharedPreferences]へ永続化する。 */
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

    companion object {
        fun get(context: Context): AndroidDiscoverySettingsStorage =
            AndroidDiscoverySettingsStorage(
                context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            )
    }
}
