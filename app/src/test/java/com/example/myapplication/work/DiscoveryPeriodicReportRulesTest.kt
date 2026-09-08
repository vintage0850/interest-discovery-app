package com.example.myapplication.work

import com.example.myapplication.shared.discovery.ReportType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * 案件27：週次／月次「気付きレポート」定期判定ルールの単体テスト。
 *
 * WorkManager の Android 依存部分は androidTest でカバーし、
 * ここでは UTC 期間キー算出・重複および巻戻り抑止・固定文言・通知 ID 衝突防止などの
 * 純粋ロジックを検証する。
 */
class DiscoveryPeriodicReportRulesTest {

    @Test
    fun computeUtcWeekKey_月曜0時UTCアンカーで正しく算出される() {
        // 2026-09-08 (火) 12:00:00 UTC -> 2026-09-07 (月)
        val tuesday = Instant.parse("2026-09-08T12:00:00Z")
        assertEquals("2026-09-07", computeUtcWeekKey(tuesday))

        // 2026-09-07 (月) 00:00:00 UTC -> 2026-09-07
        val mondayStart = Instant.parse("2026-09-07T00:00:00Z")
        assertEquals("2026-09-07", computeUtcWeekKey(mondayStart))

        // 2026-09-07 (月) 23:59:59 UTC -> 2026-09-07
        val mondayEnd = Instant.parse("2026-09-07T23:59:59Z")
        assertEquals("2026-09-07", computeUtcWeekKey(mondayEnd))

        // 2026-09-06 (日) 23:59:59 UTC -> 前週月曜 2026-08-31
        val sundayBefore = Instant.parse("2026-09-06T23:59:59Z")
        assertEquals("2026-08-31", computeUtcWeekKey(sundayBefore))

        // 2026-09-13 (日) 15:00:00 UTC -> 同週月曜 2026-09-07
        val sundaySameWeek = Instant.parse("2026-09-13T15:00:00Z")
        assertEquals("2026-09-07", computeUtcWeekKey(sundaySameWeek))

        // 2026-09-14 (月) 00:00:00 UTC -> 次週月曜 2026-09-14
        val nextMonday = Instant.parse("2026-09-14T00:00:00Z")
        assertEquals("2026-09-14", computeUtcWeekKey(nextMonday))
    }

    @Test
    fun computeUtcMonthKey_UTC月のYYYY_MMで正しく算出される() {
        // 2026-09-08 12:00:00 UTC -> 2026-09
        assertEquals("2026-09", computeUtcMonthKey(Instant.parse("2026-09-08T12:00:00Z")))

        // 2026-08-31 23:59:59 UTC -> 2026-08
        assertEquals("2026-08", computeUtcMonthKey(Instant.parse("2026-08-31T23:59:59Z")))

        // 2026-09-01 00:00:00 UTC -> 2026-09
        assertEquals("2026-09", computeUtcMonthKey(Instant.parse("2026-09-01T00:00:00Z")))

        // 年跨ぎ: 2026-12-31 -> 2026-12, 2027-01-01 -> 2027-01
        assertEquals("2026-12", computeUtcMonthKey(Instant.parse("2026-12-31T23:59:59Z")))
        assertEquals("2027-01", computeUtcMonthKey(Instant.parse("2027-01-01T00:00:00Z")))
    }

    @Test
    fun shouldNotifyPeriodReport_未保存の初回は配信対象() {
        // 初回（保存キーが null）は現在キーの1件だけ配信対象とする
        assertTrue(shouldNotifyPeriodReport(savedKey = null, currentKey = "2026-09-07"))
        assertTrue(shouldNotifyPeriodReport(savedKey = null, currentKey = "2026-09"))
    }

    @Test
    fun shouldNotifyPeriodReport_同一キーなら通知抑制() {
        // 既に今週／今月分を通知済み
        assertFalse(shouldNotifyPeriodReport(savedKey = "2026-09-07", currentKey = "2026-09-07"))
        assertFalse(shouldNotifyPeriodReport(savedKey = "2026-09", currentKey = "2026-09"))
    }

    @Test
    fun shouldNotifyPeriodReport_新しい期間に入ったら配信対象() {
        // 過去キーより新しい現在キーなら配信対象
        assertTrue(shouldNotifyPeriodReport(savedKey = "2026-08-31", currentKey = "2026-09-07"))
        assertTrue(shouldNotifyPeriodReport(savedKey = "2026-08", currentKey = "2026-09"))
    }

    @Test
    fun shouldNotifyPeriodReport_端末時刻巻戻り時は通知抑制() {
        // 保存済みキー以前の古いキーの場合は通知しない
        assertFalse(shouldNotifyPeriodReport(savedKey = "2026-09-07", currentKey = "2026-08-31"))
        assertFalse(shouldNotifyPeriodReport(savedKey = "2026-09", currentKey = "2026-08"))
    }

    @Test
    fun buildReportNotificationContent_週次固定文言の検証() {
        val (title, body) = buildReportNotificationContent(ReportType.WEEKLY)
        assertEquals("📊 1週間の気付きレポートができました", title)
        assertEquals("この1週間の変化と、新しく見えてきた傾向を確認できます", body)
    }

    @Test
    fun buildReportNotificationContent_月次固定文言の検証() {
        val (title, body) = buildReportNotificationContent(ReportType.MONTHLY)
        assertEquals("🌱 1か月の気付きレポートができました", title)
        assertEquals("この30日間の進み方と、続けられたペースを振り返れます", body)
    }

    @Test
    fun notificationIdAndRequestCode_週次と月次で衝突しない() {
        val sessionId = 1
        val weeklyId = reportNotificationIdFor(sessionId, ReportType.WEEKLY)
        val monthlyId = reportNotificationIdFor(sessionId, ReportType.MONTHLY)
        assertNotEquals(weeklyId, monthlyId)

        val weeklyRequestCode = reportRequestCodeFor(sessionId, ReportType.WEEKLY)
        val monthlyRequestCode = reportRequestCodeFor(sessionId, ReportType.MONTHLY)
        assertNotEquals(weeklyRequestCode, monthlyRequestCode)

        // 別セッションとも衝突しない
        val session2WeeklyId = reportNotificationIdFor(2, ReportType.WEEKLY)
        assertNotEquals(weeklyId, session2WeeklyId)
    }

    @Test
    fun notificationIdAndRequestCode_離れたセッション間でも種別違いで衝突しない() {
        assertNotEquals(
            reportNotificationIdFor(1, ReportType.MONTHLY),
            reportNotificationIdFor(10_001, ReportType.WEEKLY)
        )
        assertNotEquals(
            reportRequestCodeFor(1, ReportType.MONTHLY),
            reportRequestCodeFor(10_001, ReportType.WEEKLY)
        )
    }
}
