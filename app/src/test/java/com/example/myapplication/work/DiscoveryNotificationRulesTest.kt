package com.example.myapplication.work

import com.example.myapplication.data.calendar.CalendarEventSlot
import com.example.myapplication.shared.discovery.Experiment
import com.example.myapplication.shared.discovery.NotificationCandidate
import com.example.myapplication.shared.discovery.NotificationCandidateDomainStatus
import com.example.myapplication.shared.discovery.NotificationLog
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Discovery 空き時間通知の純粋判定ロジックの単体テスト。
 *
 * Worker 全体の Android フレームワーク依存部分は androidTest でカバーし、
 * ここでは文言・抑止・候補選択の計算だけを検証する。
 */
class DiscoveryNotificationRulesTest {

    @Test
    fun `buildContent_DIVE_CANDIDATEは深掘り文言を返す`() {
        val candidate = createCandidate(
            id = "exp-build",
            title = "UIの法則を分解する",
            plannedMinutes = 15,
            status = NotificationCandidateDomainStatus.DIVE_CANDIDATE
        )

        val (title, body) = buildDiscoveryNotificationContent(candidate)

        assertEquals("🔥 深掘りする時間ができました", title)
        assertEquals("「UIの法則を分解する」なら今から15分で試せそうです", body)
    }

    @Test
    fun `buildContent_TRIEDは試す文言を返す`() {
        val candidate = createCandidate(
            id = "exp-build",
            title = "配色パターンを3つ集める",
            plannedMinutes = 10,
            status = NotificationCandidateDomainStatus.TRIED
        )

        val (title, body) = buildDiscoveryNotificationContent(candidate)

        assertEquals("✨ ちょっと試せる時間です", title)
        assertEquals("選んだ「配色パターンを3つ集める」を10分だけ試してみませんか？", body)
    }

    @Test
    fun `buildContent_EXPLOREDは試す文言を返す`() {
        val candidate = createCandidate(
            id = "exp-build",
            title = "人の視線を追う",
            plannedMinutes = 20,
            status = NotificationCandidateDomainStatus.EXPLORED
        )

        val (title, body) = buildDiscoveryNotificationContent(candidate)

        assertEquals("✨ ちょっと試せる時間です", title)
        assertEquals("選んだ「人の視線を追う」を20分だけ試してみませんか？", body)
    }

    @Test
    fun `shouldSuppressNotification_6時間未満の前回通知は抑制する`() {
        val log = NotificationLog(
            lastNotifiedAt = Instant.parse("2026-09-07T08:00:00+09:00"),
            notifiedExperimentDates = emptyMap()
        )

        assertTrue(
            shouldSuppressNotification(
                log,
                now = Instant.parse("2026-09-07T10:00:00+09:00")
            )
        )
    }

    @Test
    fun `shouldSuppressNotification_6時間以上経過した通知は抑制しない`() {
        val log = NotificationLog(
            lastNotifiedAt = Instant.parse("2026-09-07T03:00:00+09:00"),
            notifiedExperimentDates = emptyMap()
        )

        assertFalse(
            shouldSuppressNotification(
                log,
                now = Instant.parse("2026-09-07T10:00:00+09:00")
            )
        )
    }

    @Test
    fun `sameExperimentToday_同日同実験は抑制する`() {
        val log = NotificationLog(
            notifiedExperimentDates = mapOf("exp-1" to "2026-09-07")
        )

        assertTrue(log.notifiedExperimentDates["exp-1"] == "2026-09-07")
        assertFalse(log.notifiedExperimentDates["exp-2"] == "2026-09-07")
    }

    @Test
    fun `selectNotifyCandidate_候補がなければnull`() {
        assertNull(
            selectNotifyCandidate(
                candidates = emptyList(),
                busySlots = emptyList(),
                now = java.time.Instant.parse("2026-09-07T10:00:00+09:00"),
                windowEnd = java.time.Instant.parse("2026-09-07T21:00:00+09:00")
            )
        )
    }

    @Test
    fun `selectNotifyCandidate_空きが十分なら最初の候補を選ぶ`() {
        val candidates = listOf(
            createCandidate("exp-1", 10, NotificationCandidateDomainStatus.DIVE_CANDIDATE),
            createCandidate("exp-2", 5, NotificationCandidateDomainStatus.TRIED)
        )
        val now = java.time.Instant.parse("2026-09-07T10:00:00+09:00")
        val windowEnd = java.time.Instant.parse("2026-09-07T21:00:00+09:00")

        val selected = selectNotifyCandidate(candidates, emptyList(), now, windowEnd)

        assertEquals("exp-1", selected?.experiment?.id)
    }

    @Test
    fun `selectNotifyCandidate_先頭候補に必要時間が足りなければ後続候補を選ぶ`() {
        val candidates = listOf(
            createCandidate("exp-1", 30, NotificationCandidateDomainStatus.DIVE_CANDIDATE),
            createCandidate("exp-2", 10, NotificationCandidateDomainStatus.TRIED)
        )
        // 10:00〜10:20 の20分空きしかない（30分実験には足りないが10分実験には足りる）
        val busySlots = listOf(
            CalendarEventSlot(
                start = java.time.Instant.parse("2026-09-07T10:20:00+09:00"),
                end = java.time.Instant.parse("2026-09-07T21:00:00+09:00")
            )
        )
        val now = java.time.Instant.parse("2026-09-07T10:00:00+09:00")
        val windowEnd = java.time.Instant.parse("2026-09-07T21:00:00+09:00")

        val selected = selectNotifyCandidate(candidates, busySlots, now, windowEnd)

        assertEquals("exp-2", selected?.experiment?.id)
    }

    @Test
    fun `selectNotifyCandidate_全候補に必要時間が足りなければnull`() {
        val candidates = listOf(
            createCandidate("exp-1", 30, NotificationCandidateDomainStatus.DIVE_CANDIDATE)
        )
        val busySlots = listOf(
            CalendarEventSlot(
                start = java.time.Instant.parse("2026-09-07T10:15:00+09:00"),
                end = java.time.Instant.parse("2026-09-07T21:00:00+09:00")
            )
        )
        val now = java.time.Instant.parse("2026-09-07T10:00:00+09:00")
        val windowEnd = java.time.Instant.parse("2026-09-07T21:00:00+09:00")

        assertNull(selectNotifyCandidate(candidates, busySlots, now, windowEnd))
    }

    @Test
    fun `selectNotifyCandidate_準備バッファ5分を含めて判定する`() {
        // 10分実験には 15分必要。
        val candidates = listOf(
            createCandidate("exp-1", 10, NotificationCandidateDomainStatus.TRIED)
        )
        val busySlots = listOf(
            CalendarEventSlot(
                start = java.time.Instant.parse("2026-09-07T10:14:00+09:00"),
                end = java.time.Instant.parse("2026-09-07T21:00:00+09:00")
            )
        )
        val now = java.time.Instant.parse("2026-09-07T10:00:00+09:00")
        val windowEnd = java.time.Instant.parse("2026-09-07T21:00:00+09:00")

        // 14分しか空いていないので通知しない
        assertNull(selectNotifyCandidate(candidates, busySlots, now, windowEnd))
    }

    private fun createCandidate(
        plannedMinutes: Int,
        status: NotificationCandidateDomainStatus
    ): NotificationCandidate = createCandidate(
        id = "exp-default",
        title = "テスト実験",
        plannedMinutes = plannedMinutes,
        status = status
    )

    private fun createCandidate(
        id: String,
        plannedMinutes: Int,
        status: NotificationCandidateDomainStatus
    ): NotificationCandidate = createCandidate(
        id = id,
        title = "テスト実験",
        plannedMinutes = plannedMinutes,
        status = status
    )

    private fun createCandidate(
        id: String,
        title: String,
        plannedMinutes: Int,
        status: NotificationCandidateDomainStatus
    ): NotificationCandidate = NotificationCandidate(
        experiment = Experiment(
            id = id,
            title = title,
            description = "",
            plannedMinutes = plannedMinutes,
            actionType = com.example.myapplication.shared.discovery.BehaviorSignal.ANALYZE
        ),
        domainStatus = status,
        reason = ""
    )
}
