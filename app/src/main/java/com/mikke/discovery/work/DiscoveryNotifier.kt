package com.mikke.discovery.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mikke.discovery.MainActivity
import com.mikke.discovery.R
import com.mikke.discovery.shared.discovery.NotificationCandidate
import com.mikke.discovery.shared.discovery.NotificationCandidateDomainStatus

/**
 * Discovery の空き時間通知を発行する処理を抽象化する。
 *
 * 単体テストでは Android の通知 API が動かないため、本番用とテスト用を差し替えられるようにする。
 */
interface DiscoveryNotifier {
    fun notifyDiscoveryCandidate(candidate: NotificationCandidate)
}

/**
 * 通知文言を組み立てる。
 *
 * 実験結果の点数や confidence、Google Calendar の予定名、連携アカウント情報を
 * ロック画面に出さないよう、テンプレートに固定の情報だけを含める。
 */
fun buildDiscoveryNotificationContent(candidate: NotificationCandidate): Pair<String, String> {
    val experiment = candidate.experiment
    return when (candidate.domainStatus) {
        NotificationCandidateDomainStatus.DIVE_CANDIDATE ->
            "🔥 深掘りする時間ができました" to
                "「${experiment.title}」なら今から${experiment.plannedMinutes}分で試せそうです"

        NotificationCandidateDomainStatus.TRIED,
        NotificationCandidateDomainStatus.EXPLORED ->
            "✨ ちょっと試せる時間です" to
                "選んだ「${experiment.title}」を${experiment.plannedMinutes}分だけ試してみませんか？"

        else ->
            "✨ ちょっと試せる時間です" to
                "選んだ「${experiment.title}」を${experiment.plannedMinutes}分だけ試してみませんか？"
    }
}

/**
 * 本番用の Discovery 通知発行。
 *
 * 通知権限が無ければ静かに何もしない。
 * タップ時に [MainActivity] を起動し、通知に紐づく experiment_id を Intent へ乗せる。
 */
class AndroidDiscoveryNotifier(context: Context) : DiscoveryNotifier {

    private val appContext = context.applicationContext

    override fun notifyDiscoveryCandidate(candidate: NotificationCandidate) {
        ensureChannel()
        val manager = NotificationManagerCompat.from(appContext)
        if (!manager.areNotificationsEnabled()) return

        val (title, body) = buildDiscoveryNotificationContent(candidate)

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(contentIntent(candidate.experiment.id))
            .setAutoCancel(true)
            .build()

        manager.notify(notificationIdFor(candidate.experiment.id), notification)
    }

    private fun contentIntent(experimentId: String): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_EXPERIMENT_ID, experimentId)
        return PendingIntent.getActivity(
            appContext,
            experimentId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            appContext.getString(R.string.discovery_notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "discovery_free_time"

        /**
         * 実験 ID から通知 ID を採番する。
         * 異なる実験は別通知として出し、かつ同じ実験は上書き更新されるようにする。
         */
        fun notificationIdFor(experimentId: String): Int =
            NOTIFICATION_ID_BASE + experimentId.hashCode()

        private const val NOTIFICATION_ID_BASE = 20_000
    }
}
