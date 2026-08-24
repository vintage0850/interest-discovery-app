package com.example.myapplication.data.calendar

import java.time.Instant

/**
 * カレンダー上の1件の予定区間。終日予定・時刻指定予定の両方を統一的に扱うための軽量データクラス。
 * 終日予定は端末のデフォルトタイムゾーンで日付境界（0:00〜24:00）に変換してから渡すこと。
 */
data class CalendarEventSlot(val start: Instant, val end: Instant)

/** [findNextFreeSlot] が見つけた空き区間。 */
data class FreeSlot(val start: Instant, val end: Instant)

/**
 * 予定の合間から、通知に使える直近の空き時間を1つ探す。
 *
 * [now] 以降の予定を開始時刻順に見て、[now]（または直前の予定終了時刻）から
 * 次の予定開始までの間隔が [minDurationMinutes] 分以上ならその区間を採用する。
 * 最初に見つかった区間＝直近の空き時間を優先する。
 * 予定が尽きた場合は [windowEnd] までを1区間とみなす。
 *
 * 副作用の無い純粋関数。[events] は開始時刻順である必要はない（内部でソートする）。
 */
fun findNextFreeSlot(
    events: List<CalendarEventSlot>,
    now: Instant,
    windowEnd: Instant,
    minDurationMinutes: Int = 30
): FreeSlot? {
    val minDurationSeconds = minDurationMinutes * 60L

    // now より後に終わる予定だけを開始時刻順に見ていく（過去の予定は空き時間の計算に無関係）
    val upcoming = events
        .filter { it.end.isAfter(now) }
        .sortedBy { it.start }

    var cursor = now
    for (event in upcoming) {
        // 予定の開始が windowEnd 以降なら、その手前までが最後の空き区間になる
        if (!event.start.isBefore(windowEnd)) break

        // cursor から次の予定開始までの間隔を候補にする（重複予定は開始が cursor より前になりうる）
        val candidateEnd = if (event.start.isAfter(cursor)) event.start else cursor
        if (candidateEnd.epochSecond - cursor.epochSecond >= minDurationSeconds) {
            return FreeSlot(cursor, candidateEnd)
        }

        if (event.end.isAfter(cursor)) cursor = event.end
    }

    if (cursor.isAfter(windowEnd) || cursor == windowEnd) return null

    return if (windowEnd.epochSecond - cursor.epochSecond >= minDurationSeconds) {
        FreeSlot(cursor, windowEnd)
    } else {
        null
    }
}
