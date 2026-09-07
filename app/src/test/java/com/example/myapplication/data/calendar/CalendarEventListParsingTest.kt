package com.example.myapplication.data.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * Calendar API のイベント一覧レスポンス1件を [CalendarEventSlot] へ変換する処理の単体テスト。
 * 終日予定（date）と時刻指定予定（dateTime）の両方を扱えることを確認する。
 * また、空き時間を塞がない予定（取消・透明・辞退）は null になることを確認する。
 */
class CalendarEventListParsingTest {

    private val tokyo = ZoneId.of("Asia/Tokyo")

    @Test
    fun `時刻指定予定はdateTimeをそのままInstantに変換する`() {
        val item = CalendarEventListItem(
            start = CalendarEventDateTime(dateTime = "2026-08-23T10:00:00+09:00"),
            end = CalendarEventDateTime(dateTime = "2026-08-23T11:00:00+09:00")
        )

        val slot = item.toBlockingEventSlotOrNull(tokyo)

        assertEquals(Instant.parse("2026-08-23T01:00:00Z"), slot?.start)
        assertEquals(Instant.parse("2026-08-23T02:00:00Z"), slot?.end)
    }

    @Test
    fun `終日予定は端末タイムゾーンの日付境界0時から24時までの区間になる`() {
        val item = CalendarEventListItem(
            start = CalendarEventDateTime(date = "2026-08-23"),
            // Google の仕様どおり end.date は排他的（翌日）
            end = CalendarEventDateTime(date = "2026-08-24")
        )

        val slot = item.toBlockingEventSlotOrNull(tokyo)

        // 2026-08-23 00:00 Asia/Tokyo == 2026-08-22 15:00 UTC
        assertEquals(Instant.parse("2026-08-22T15:00:00Z"), slot?.start)
        // 2026-08-24 00:00 Asia/Tokyo == 2026-08-23 15:00 UTC
        assertEquals(Instant.parse("2026-08-23T15:00:00Z"), slot?.end)
    }

    @Test
    fun `startとendのどちらかが欠けていればnullを返す`() {
        val missingEnd = CalendarEventListItem(
            start = CalendarEventDateTime(dateTime = "2026-08-23T10:00:00+09:00"),
            end = null
        )
        val missingStart = CalendarEventListItem(
            start = null,
            end = CalendarEventDateTime(dateTime = "2026-08-23T11:00:00+09:00")
        )

        assertNull(missingEnd.toBlockingEventSlotOrNull(tokyo))
        assertNull(missingStart.toBlockingEventSlotOrNull(tokyo))
    }

    @Test
    fun `cancelledステータスは空きを塞がない`() {
        val item = CalendarEventListItem(
            start = CalendarEventDateTime(dateTime = "2026-08-23T10:00:00+09:00"),
            end = CalendarEventDateTime(dateTime = "2026-08-23T11:00:00+09:00"),
            status = "cancelled"
        )

        assertNull(item.toBlockingEventSlotOrNull(tokyo))
    }

    @Test
    fun `transparentな予定は空きを塞がない`() {
        val item = CalendarEventListItem(
            start = CalendarEventDateTime(dateTime = "2026-08-23T10:00:00+09:00"),
            end = CalendarEventDateTime(dateTime = "2026-08-23T11:00:00+00Z"),
            transparency = "transparent"
        )

        assertNull(item.toBlockingEventSlotOrNull(tokyo))
    }

    @Test
    fun `辞退済みのattendeeは空きを塞がない`() {
        val item = CalendarEventListItem(
            start = CalendarEventDateTime(dateTime = "2026-08-23T10:00:00+09:00"),
            end = CalendarEventDateTime(dateTime = "2026-08-23T11:00:00+09:00"),
            attendees = listOf(
                CalendarEventAttendee(self = true, responseStatus = "declined"),
                CalendarEventAttendee(self = false, responseStatus = "accepted")
            )
        )

        assertNull(item.toBlockingEventSlotOrNull(tokyo))
    }

    @Test
    fun `辞退していないattendeeは空きを塞ぐ`() {
        val item = CalendarEventListItem(
            start = CalendarEventDateTime(dateTime = "2026-08-23T10:00:00+09:00"),
            end = CalendarEventDateTime(dateTime = "2026-08-23T11:00:00+09:00"),
            attendees = listOf(
                CalendarEventAttendee(self = true, responseStatus = "accepted")
            )
        )

        val slot = item.toBlockingEventSlotOrNull(tokyo)
        assertEquals(Instant.parse("2026-08-23T01:00:00Z"), slot?.start)
        assertEquals(Instant.parse("2026-08-23T02:00:00Z"), slot?.end)
    }
}
