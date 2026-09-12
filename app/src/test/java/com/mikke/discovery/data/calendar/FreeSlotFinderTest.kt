package com.mikke.discovery.data.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * [findNextFreeSlot] の空きギャップ計算ロジックの単体テスト。
 * 副作用の無い純粋関数なので、Android や DB に依存せずテストできる。
 */
class FreeSlotFinderTest {

    private val now = Instant.parse("2026-08-23T10:00:00Z")
    private val windowEnd = Instant.parse("2026-08-23T22:00:00Z")

    @Test
    fun `予定が1件も無ければ現在時刻からwindowEndまでが空き時間になる`() {
        val slot = findNextFreeSlot(emptyList(), now, windowEnd)

        assertEquals(now, slot?.start)
        assertEquals(windowEnd, slot?.end)
    }

    @Test
    fun `現在時刻から次の予定まで30分ちょうどなら空き時間として採用する`() {
        val nextEventStart = now.plusSeconds(30 * 60)
        val events = listOf(
            CalendarEventSlot(start = nextEventStart, end = nextEventStart.plusSeconds(3600))
        )

        val slot = findNextFreeSlot(events, now, windowEnd, minDurationMinutes = 30)

        assertEquals(now, slot?.start)
        assertEquals(nextEventStart, slot?.end)
    }

    @Test
    fun `現在時刻から次の予定まで29分なら対象外で予定終了後の空きを探す`() {
        val nextEventStart = now.plusSeconds(29 * 60)
        val nextEventEnd = nextEventStart.plusSeconds(3600)
        val events = listOf(
            CalendarEventSlot(start = nextEventStart, end = nextEventEnd)
        )

        val slot = findNextFreeSlot(events, now, windowEnd, minDurationMinutes = 30)

        // 29分の隙間（now〜nextEventStart）は採用されず、予定終了後の空きが返る
        assertEquals(nextEventEnd, slot?.start)
        assertEquals(windowEnd, slot?.end)
    }

    @Test
    fun `直前の空きが短くその後も空きが無ければ全体として空き無し`() {
        val nextEventStart = now.plusSeconds(29 * 60)
        val events = listOf(
            // 29分の隙間の後、windowEndまでびっしり埋まっている
            CalendarEventSlot(start = nextEventStart, end = windowEnd)
        )

        val slot = findNextFreeSlot(events, now, windowEnd, minDurationMinutes = 30)

        assertNull(slot)
    }

    @Test
    fun `複数の予定が連続していて隙間が無ければ次の空きまで見に行く`() {
        // 10:00-10:40, 10:40-11:40 と連続していて、11:40 以降にようやく空く
        val events = listOf(
            CalendarEventSlot(start = now, end = now.plusSeconds(40 * 60)),
            CalendarEventSlot(
                start = now.plusSeconds(40 * 60),
                end = now.plusSeconds(100 * 60)
            )
        )

        val slot = findNextFreeSlot(events, now, windowEnd, minDurationMinutes = 30)

        assertEquals(now.plusSeconds(100 * 60), slot?.start)
        assertEquals(windowEnd, slot?.end)
    }

    @Test
    fun `予定がすべて過去なら現在時刻からwindowEndまでが空き時間になる`() {
        val events = listOf(
            CalendarEventSlot(
                start = now.minusSeconds(3600),
                end = now.minusSeconds(1800)
            )
        )

        val slot = findNextFreeSlot(events, now, windowEnd, minDurationMinutes = 30)

        assertEquals(now, slot?.start)
        assertEquals(windowEnd, slot?.end)
    }

    @Test
    fun `windowEndを超える空き区間はwindowEndで切り詰める`() {
        // 最後の予定が windowEnd の直前で終わり、その後は何も無い
        val lastEventEnd = windowEnd.minusSeconds(3600)
        val events = listOf(
            CalendarEventSlot(start = now, end = lastEventEnd)
        )

        val slot = findNextFreeSlot(events, now, windowEnd, minDurationMinutes = 30)

        assertEquals(lastEventEnd, slot?.start)
        assertEquals(windowEnd, slot?.end)
    }

    @Test
    fun `windowEndを過ぎてから予定がある場合は空き無しとして扱う`() {
        val events = listOf(
            CalendarEventSlot(start = now, end = windowEnd)
        )

        val slot = findNextFreeSlot(events, now, windowEnd, minDurationMinutes = 30)

        assertNull(slot)
    }
}
