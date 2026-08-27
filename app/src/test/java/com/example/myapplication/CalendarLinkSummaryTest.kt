package com.example.myapplication

import com.example.myapplication.data.calendar.CalendarAuthState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 設定ハブ画面のカレンダー連携項目表示を、認可状態に応じて正しく組み立てられるかの単体テスト。
 */
class CalendarLinkSummaryTest {

    @Test
    fun `未設定状態では未設定の文言と設定案内が表示される`() {
        val summary = calendarLinkSummary(CalendarAuthState.NotConfigured)

        assertEquals("Google カレンダー連携", summary.title)
        assertTrue(summary.subtitle?.startsWith("未設定") == true)
        assertTrue(
            summary.subtitle?.contains("SETUP.md の手順で OAuth クライアント ID を設定してください") == true
        )
        assertFalse(summary.isConnected)
        assertFalse(summary.canConnect)
    }

    @Test
    fun `未認可状態では未接続と表示され接続ボタンが有効になる`() {
        val summary = calendarLinkSummary(CalendarAuthState.NotAuthorized)

        assertEquals("Google カレンダー連携", summary.title)
        assertEquals("未接続", summary.subtitle)
        assertFalse(summary.isConnected)
        assertTrue(summary.canConnect)
    }

    @Test
    fun `認可済み状態ではアカウントと接続済みが表示される`() {
        val summary = calendarLinkSummary(CalendarAuthState.Authorized("user@example.com"))

        assertEquals("Google カレンダー連携", summary.title)
        assertTrue(summary.subtitle?.startsWith("接続済み") == true)
        assertTrue(summary.subtitle?.contains("user@example.com") == true)
        assertTrue(summary.isConnected)
        assertFalse(summary.canConnect)
    }

    @Test
    fun `認可済みでもメールがnullの場合は接続済みと表示される`() {
        val summary = calendarLinkSummary(CalendarAuthState.Authorized(null))

        assertEquals("Google カレンダー連携", summary.title)
        assertEquals("接続済み", summary.subtitle)
        assertTrue(summary.isConnected)
        assertFalse(summary.canConnect)
    }
}
