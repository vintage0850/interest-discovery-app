package com.example.myapplication.data

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class NotificationWindowPreferencesTest {

    @Test
    fun `何も保存されていなければデフォルトの8時から22時を返す`() {
        val prefs = NotificationWindowPreferences(FakeSharedPreferences())

        val window = prefs.get()

        assertEquals(LocalTime.of(8, 0), window.start)
        assertEquals(LocalTime.of(22, 0), window.end)
    }

    @Test
    fun `保存した時間帯を読み出せる`() {
        val fake = FakeSharedPreferences()
        val prefs = NotificationWindowPreferences(fake)

        prefs.set(NotificationWindow(LocalTime.of(7, 30), LocalTime.of(21, 15)))
        val window = prefs.get()

        assertEquals(LocalTime.of(7, 30), window.start)
        assertEquals(LocalTime.of(21, 15), window.end)
    }

    /**
     * Android フレームワークを使わない最小限の [SharedPreferences] フェイク。
     * このプロジェクトは Robolectric を導入していないため、Context を必要としない
     * テストにするためにインメモリの Map で代用する。
     */
    class FakeSharedPreferences : SharedPreferences {
        private val values = mutableMapOf<String, Any?>()

        override fun getInt(key: String, defValue: Int): Int =
            values[key] as? Int ?: defValue

        override fun contains(key: String): Boolean = values.containsKey(key)
        override fun getAll(): MutableMap<String, *> = values
        override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST") (values[key] as? MutableSet<String>) ?: defValues
        override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener
        ) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener
        ) = Unit

        override fun edit(): SharedPreferences.Editor = FakeEditor()

        private inner class FakeEditor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            override fun putString(key: String, value: String?) = apply { pending[key] = value }
            override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values }
            override fun putInt(key: String, value: Int) = apply { pending[key] = value }
            override fun putLong(key: String, value: Long) = apply { pending[key] = value }
            override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
            override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
            override fun remove(key: String) = apply { pending[key] = null }
            override fun clear() = apply { values.clear() }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                pending.forEach { (key, value) ->
                    if (value == null) values.remove(key) else values[key] = value
                }
                pending.clear()
            }
        }
    }
}
