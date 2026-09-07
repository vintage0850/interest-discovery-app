package com.example.myapplication.work

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.myapplication.BuildConfig
import com.example.myapplication.data.calendar.CalendarAuthState
import com.example.myapplication.data.calendar.CalendarEventSlot
import com.example.myapplication.data.calendar.CalendarResult
import com.example.myapplication.data.calendar.GoogleAuthManager
import com.example.myapplication.data.calendar.GoogleCalendarSync
import com.example.myapplication.data.calendar.findNextFreeSlot
import com.example.myapplication.shared.discovery.AndroidDiscoverySettingsStorage
import com.example.myapplication.shared.discovery.AndroidSessionStorage
import com.example.myapplication.shared.discovery.DiscoveryRepository
import com.example.myapplication.shared.discovery.DiscoverySettingsStorage
import com.example.myapplication.shared.discovery.NotificationCandidate
import com.example.myapplication.shared.discovery.NotificationLog
import com.example.myapplication.shared.discovery.RealDiscoveryRepository
import com.example.myapplication.shared.discovery.SessionStorage
import kotlinx.datetime.Instant
import kotlinx.datetime.minus
import java.time.Clock
import java.time.ZoneId
import kotlin.time.Duration.Companion.hours

/**
 * Discovery 向け Google Calendar 空き時間通知の定期 Worker。
 *
 * 15分間隔で起動し、以下を順に判定する。
 *   1. Mikke 通知 ON / OS 通知権限あり / Google Calendar 認可済み
 *   2. 永続化された Discovery セッションが復元できる
 *   3. backend から通知候補を取得できる
 *   4. 当日 21:00 までの空き時間が候補の planned_minutes + 5分（準備バッファ）以上ある
 *   5. 同一実験はローカル日付ごとに 1回、全体で 6時間に 1回しか通知しない
 *
 * いずれかの条件を満たさない場合は通知を出さず、安全に正常終了して次回の実行に委ねる。
 * WorkManager の実行間隔は目安であり、Doze 等で OS が遅延させることがある。
 */
class DiscoveryFreeTimeWorker @JvmOverloads constructor(
    context: Context,
    params: WorkerParameters,
    private val settingsStorage: DiscoverySettingsStorage = AndroidDiscoverySettingsStorage.get(context),
    private val sessionStorage: SessionStorage = AndroidSessionStorage.get(context),
    authManager: GoogleAuthManager = GoogleAuthManager.get(context),
    private val repository: DiscoveryRepository = RealDiscoveryRepository(
        baseUrl = BuildConfig.DISCOVERY_BASE_URL,
        settingsStorage = settingsStorage,
        sessionStorage = sessionStorage,
        enableHttpLogging = BuildConfig.DEBUG
    ),
    private val calendarSync: GoogleCalendarSync = GoogleCalendarSync(authManager),
    private val notifier: DiscoveryNotifier = AndroidDiscoveryNotifier(context),
    private val authState: () -> CalendarAuthState = { authManager.authState.value },
    private val notificationPermissionGranted: () -> Boolean = {
        NotificationManagerCompat.from(context.applicationContext).areNotificationsEnabled()
    },
    private val clock: Clock = Clock.systemDefaultZone()
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // 1. 基本トリガー条件
        val settings = settingsStorage.load()
        if (!settings.notificationsEnabled) return Result.success()
        if (!notificationPermissionGranted()) return Result.success()
        if (authState() !is CalendarAuthState.Authorized) return Result.success()

        val zone = clock.zone
        val nowJava = java.time.Instant.now(clock)
        val localTime = nowJava.atZone(zone).toLocalTime()
        if (localTime.isBefore(NOTIFICATION_WINDOW_START) || !localTime.isBefore(NOTIFICATION_WINDOW_END)) {
            return Result.success()
        }

        // 2. セッション復元と候補取得
        val candidates = try {
            repository.getNotificationCandidates()
        } catch (_: Exception) {
            // backend / 通信 / 401 等の失敗は次回に委ねる。送信済み記録も付けない。
            return Result.success()
        }
        if (candidates.isEmpty()) return Result.success()

        // 3. 重複抑止
        val todayDate = java.time.LocalDate.now(clock).toString()
        val nowKotlin = Instant.fromEpochMilliseconds(nowJava.toEpochMilli())
        val log = settingsStorage.loadNotificationLog()
        if (shouldSuppressNotification(log, nowKotlin, todayDate)) return Result.success()

        // 4. カレンダー空き時間判定
        val windowEnd = localDateAt(zone, NOTIFICATION_WINDOW_END).toInstant()
        val eventsResult = calendarSync.listTodayEvents(nowJava, zone)
        val busySlots = when (eventsResult) {
            is CalendarResult.Success -> eventsResult.value
            else -> return Result.success()
        }

        val candidate = selectNotifyCandidate(candidates, busySlots, nowJava, windowEnd)
            ?: return Result.success()

        // 同一実験はローカル日付ごとに 1 回まで
        if (log.notifiedExperimentDates[candidate.experiment.id] == todayDate) {
            return Result.success()
        }

        // 5. 通知発行と履歴保存
        notifier.notifyDiscoveryCandidate(candidate)
        val updatedLog = log.copy(
            lastNotifiedAt = nowKotlin,
            notifiedExperimentDates = log.notifiedExperimentDates + (candidate.experiment.id to todayDate)
        )
        settingsStorage.saveNotificationLog(updatedLog)

        return Result.success()
    }

    private fun localDateAt(zone: ZoneId, localTime: java.time.LocalTime): java.time.ZonedDateTime {
        return java.time.LocalDate.now(clock).atTime(localTime).atZone(zone)
    }

    companion object {
        /** 通知を出す時間帯（08:00〜21:00）。端末のローカル時刻で判定する。 */
        val NOTIFICATION_WINDOW_START = java.time.LocalTime.of(8, 0)
        val NOTIFICATION_WINDOW_END = java.time.LocalTime.of(21, 0)

        const val WORK_NAME = "discovery_free_time_check"
    }
}

/**
 * 重複抑止ルールを適用する。
 *
 * - 最終通知時刻から [cooldownHours] 未満なら抑制する
 *
 * 同一実験・同日の抑止は通知対象が確定してから個別に行う。
 */
fun shouldSuppressNotification(
    log: NotificationLog,
    now: Instant,
    todayDate: String = "",
    cooldownHours: Int = 6
): Boolean {
    val last = log.lastNotifiedAt ?: return false
    return (now - last) < cooldownHours.hours
}

/**
 * backend 順の候補リストから、空き時間に収まる最初の 1 件を選ぶ。
 *
 * 各候補について「planned_minutes + 5分」の連続空きがあるかを個別に判定する。
 * 先頭候補に必要時間が足りなくても、後続の短い候補が入る可能性がある。
 */
fun selectNotifyCandidate(
    candidates: List<NotificationCandidate>,
    busySlots: List<CalendarEventSlot>,
    now: java.time.Instant,
    windowEnd: java.time.Instant
): NotificationCandidate? {
    for (candidate in candidates) {
        val requiredMinutes = candidate.experiment.plannedMinutes + PREPARATION_BUFFER_MINUTES
        findNextFreeSlot(busySlots, now, windowEnd, minDurationMinutes = requiredMinutes)
            ?.let { return candidate }
    }
    return null
}

private const val PREPARATION_BUFFER_MINUTES = 5
