package com.example.myapplication.shared.discovery

import android.content.Context
import android.content.SharedPreferences

private const val PREFS_NAME = "discovery_session"
private const val KEY_LAST_SESSION_ID = "last_session_id"

/**
 * 最後に使用した Discovery セッション ID を端末の [SharedPreferences] へ永続化する。
 */
class AndroidSessionStorage(private val prefs: SharedPreferences) : SessionStorage {

    override fun getLastSessionId(): Int? {
        return if (prefs.contains(KEY_LAST_SESSION_ID)) {
            prefs.getInt(KEY_LAST_SESSION_ID, -1)
        } else {
            null
        }
    }

    override fun saveLastSessionId(id: Int) {
        prefs.edit().putInt(KEY_LAST_SESSION_ID, id).apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY_LAST_SESSION_ID).apply()
    }

    companion object {
        fun get(context: Context): AndroidSessionStorage =
            AndroidSessionStorage(
                context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            )
    }
}
