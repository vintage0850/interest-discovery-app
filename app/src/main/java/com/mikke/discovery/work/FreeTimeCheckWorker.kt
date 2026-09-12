package com.mikke.discovery.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mikke.discovery.data.AppDatabase
import com.mikke.discovery.data.NotificationWindowPreferences
import com.mikke.discovery.data.NotifiedSlot
import com.mikke.discovery.data.TaskRepository
import com.mikke.discovery.data.calendar.CalendarAuthState
import com.mikke.discovery.data.calendar.CalendarResult
import com.mikke.discovery.data.calendar.GoogleAuthManager
import com.mikke.discovery.data.calendar.GoogleCalendarSync
import com.mikke.discovery.data.calendar.findNextFreeSlot
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 1時間おきに空き時間を検知し、条件を満たせば「始めさせる」通知を出す WorkManager ワーカー。
 *
 * 実行間隔は目安であり保証されない点に注意。[androidx.work.PeriodicWorkRequest] の最小間隔は
 * 15分だが、Doze 等で OS が実際の実行を遅延させることがあるため、「1時間おき」という設計上の
 * 期待とズレても不具合ではない。
 */
class FreeTimeCheckWorker @JvmOverloads constructor(
    context: Context,
    params: WorkerParameters,
    private val repository: TaskRepository = TaskRepository(
        AppDatabase.getDatabase(context).taskDao()
    ),
    authManager: GoogleAuthManager = GoogleAuthManager.get(context),
    private val calendarSync: GoogleCalendarSync = GoogleCalendarSync(authManager),
    /**
     * GoogleAuthManager.authState は open ではないためサブクラス化でテスト用に差し替えられない。
     * そのため認可状態の取得だけを関数として注入できるようにする（既定は本物の authManager を見る）。
     */
    private val authState: () -> CalendarAuthState = { authManager.authState.value },
    private val clock: Clock = Clock.systemDefaultZone(),
    private val notifier: FreeTimeNotifier = AndroidFreeTimeNotifier(context),
    private val notificationWindow: () -> com.mikke.discovery.data.NotificationWindow = {
        NotificationWindowPreferences.get(context).get()
    }
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // 未連携なら通知の材料（予定）が取れないので終了
        if (authState() !is CalendarAuthState.Authorized) return Result.success()

        val window = notificationWindow()
        val zone = clock.zone
        val now = Instant.now(clock)
        val localTime = now.atZone(zone).toLocalTime()
        if (localTime.isBefore(window.start) || !localTime.isBefore(window.end)) {
            return Result.success()
        }

        // 401・ネットワークエラー等の取得失敗は静かに終了し、次回のWorkerに委ねる
        // （ユーザー通知は出さない。既存のGoogleCalendarSync/GoogleAuthManagerの401リトライ・
        //   トークン無効化の仕組みに委譲し、Worker側で特別なリトライは行わない）
        val events = when (val result = calendarSync.listTodayEvents(now, zone)) {
            is CalendarResult.Success -> result.value
            else -> return Result.success()
        }

        val windowEnd = now.atZone(zone).toLocalDate().atTime(window.end).atZone(zone).toInstant()
        val freeSlot = findNextFreeSlot(events, now, windowEnd) ?: return Result.success()

        val slotStartMillis = freeSlot.start.toEpochMilli()
        if (repository.isSlotNotified(slotStartMillis)) return Result.success()

        val task = repository.getTopEligibleTaskForNotification() ?: return Result.success()

        notifier.notifyTaskStart(task)

        repository.insertNotifiedSlot(
            NotifiedSlot(startMillis = slotStartMillis, endMillis = freeSlot.end.toEpochMilli())
        )
        repository.deleteNotifiedSlotsOlderThan(now.minus(24, ChronoUnit.HOURS).toEpochMilli())

        return Result.success()
    }

    companion object {
        const val WORK_NAME = "free_time_check"
    }
}
