package com.example.myapplication.work

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.myapplication.BuildConfig
import com.example.myapplication.shared.discovery.AndroidDiscoverySettingsStorage
import com.example.myapplication.shared.discovery.AndroidSessionStorage
import com.example.myapplication.shared.discovery.DiscoveryRepository
import com.example.myapplication.shared.discovery.DiscoverySettingsStorage
import com.example.myapplication.shared.discovery.RealDiscoveryRepository
import com.example.myapplication.shared.discovery.ReportType
import com.example.myapplication.shared.discovery.SessionStorage
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

/**
 * 実行時の UTC 日時を含む週の月曜日 00:00:00 UTC の日付 YYYY-MM-DD を算出する。
 */
fun computeUtcWeekKey(now: Instant): String {
    val utcZoned = now.atZone(ZoneOffset.UTC)
    val monday = utcZoned.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return monday.toString()
}

/**
 * 実行時 UTC 月の YYYY-MM を算出する。
 */
fun computeUtcMonthKey(now: Instant): String {
    val date = now.atZone(ZoneOffset.UTC).toLocalDate()
    val month = date.monthValue
    val monthStr = if (month < 10) "0$month" else "$month"
    return "${date.year}-$monthStr"
}

/**
 * 期間キーに基づき、通知を発行すべきかを判定する。
 * - 未保存（初回）は対象
 * - 保存済みキーと同一または過去キー（時計巻戻り等）なら抑制
 * - 保存済みキーより新しいキーなら対象
 */
fun shouldNotifyPeriodReport(savedKey: String?, currentKey: String): Boolean {
    if (savedKey == null) return true
    return currentKey > savedKey
}

/**
 * 週次／月次「気付きレポート」の定期配信 Worker。
 *
 * 24時間間隔で起動し、以下を順に判定・実行する:
 * 1. Discovery 通知 ON / OS 通知権限あり
 * 2. 有効な永続化済みセッションあり
 * 3. 週次・月次をそれぞれ独立判定:
 *    - 週次: 対象キー未通知なら既存週次APIを事前取得し、成功後に通知・キー更新
 *    - 月次: 対象キー未通知なら月次APIを事前取得し、成功後に通知・キー更新
 * 4. API失敗・通信エラー・通知発行エラー時は送信フラグを更新せず次回へ委ねる
 * 5. 安全に Result.success() で終了する
 */
class DiscoveryPeriodicReportWorker @JvmOverloads constructor(
    context: Context,
    params: WorkerParameters,
    private val settingsStorage: DiscoverySettingsStorage = AndroidDiscoverySettingsStorage.get(context),
    private val sessionStorage: SessionStorage = AndroidSessionStorage.get(context),
    private val repository: DiscoveryRepository = RealDiscoveryRepository(
        baseUrl = BuildConfig.DISCOVERY_BASE_URL,
        settingsStorage = settingsStorage,
        sessionStorage = sessionStorage,
        enableHttpLogging = BuildConfig.DEBUG
    ),
    private val notifier: DiscoveryReportNotifier = AndroidDiscoveryReportNotifier(context),
    private val notificationPermissionGranted: () -> Boolean = {
        NotificationManagerCompat.from(context.applicationContext).areNotificationsEnabled()
    },
    private val clock: Clock = Clock.systemUTC()
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // 1. 基本トリガー条件
        val settings = settingsStorage.load()
        if (!settings.notificationsEnabled) return Result.success()
        if (!notificationPermissionGranted()) return Result.success()

        // 2. 有効な永続化済みセッション判定
        val sessionId = repository.getActiveSessionId() ?: sessionStorage.getLastSessionId()
        if (sessionId == null) return Result.success()

        // 3. UTC期間キー算出
        val nowInstant = clock.instant()
        val currentWeekKey = computeUtcWeekKey(nowInstant)
        val currentMonthKey = computeUtcMonthKey(nowInstant)

        // 4. 週次通知の独立判定と実行
        val lastWeekKey = settingsStorage.getLastNotifiedWeekKey(sessionId)
        if (shouldNotifyPeriodReport(lastWeekKey, currentWeekKey)) {
            try {
                repository.getWeeklyNarrative()
                if (notifier.notifyReport(ReportType.WEEKLY, sessionId)) {
                    settingsStorage.saveLastNotifiedWeekKey(sessionId, currentWeekKey)
                }
            } catch (_: Exception) {
                // 通信エラーや503時はキー保存せず次回再試行
            }
        }

        // 5. 月次通知の独立判定と実行
        val lastMonthKey = settingsStorage.getLastNotifiedMonthKey(sessionId)
        if (shouldNotifyPeriodReport(lastMonthKey, currentMonthKey)) {
            try {
                repository.getMonthlyNarrative()
                if (notifier.notifyReport(ReportType.MONTHLY, sessionId)) {
                    settingsStorage.saveLastNotifiedMonthKey(sessionId, currentMonthKey)
                }
            } catch (_: Exception) {
                // 通信エラーや503時はキー保存せず次回再試行
            }
        }

        return Result.success()
    }

    companion object {
        const val WORK_NAME = "discovery_periodic_report_worker"
    }
}
