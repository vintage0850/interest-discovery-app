package com.example.myapplication.data.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Calendar API のイベント一覧レスポンス1件を、統一的な区間 [CalendarEventSlot] に変換する。
 *
 * 終日予定（[CalendarEventDateTime.date]）と時刻指定予定（[CalendarEventDateTime.dateTime]）の
 * どちらも扱う。終日予定は [zoneId]（既定は端末のデフォルトタイムゾーン）で
 * 日付境界（0:00〜24:00）として区間化する。
 * start / end のどちらかが欠けている行は空き時間計算に使えないので null を返す。
 */
fun CalendarEventListItem.toEventSlotOrNull(
    zoneId: ZoneId = ZoneId.systemDefault()
): CalendarEventSlot? {
    val startInstant = start?.toInstantOrNull(zoneId) ?: return null
    val endInstant = end?.toInstantOrNull(zoneId) ?: return null
    return CalendarEventSlot(startInstant, endInstant)
}

/**
 * end.date は Google の仕様で排他的（当日ではなく翌日の日付が入っている）ため、
 * start / end のどちらも「date が示す日の 0:00」に変換するだけでよい。
 */
private fun CalendarEventDateTime.toInstantOrNull(zoneId: ZoneId): Instant? {
    dateTime?.let { return OffsetDateTime.parse(it).toInstant() }
    date?.let {
        val localDate = LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE)
        return localDate.atStartOfDay(zoneId).toInstant()
    }
    return null
}
