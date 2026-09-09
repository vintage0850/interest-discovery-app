package com.example.myapplication.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.myapplication.MainActivity
import com.example.myapplication.R
import com.example.myapplication.shared.discovery.ReportType

/**
 * 案件27：週次／月次「気付きレポート」通知の発行インターフェース。
 *
 * 単体テストおよび Worker テストで通知発行をモック・検証可能にする。
 */
interface DiscoveryReportNotifier {
    /**
     * 通知を発行する。
     *
     * @param reachedSignalCount マイルストーン通知の場合の到達シグナル件数。
     * @return 通知が実際に発行された場合 true。権限が無いなどで発行されなかった場合 false。
     */
    fun notifyReport(
        reportType: ReportType,
        sessionId: Int,
        reachedSignalCount: Int = 0
    ): Boolean
}

/**
 * 固定通知文言を組み立てる。
 *
 * 仕様確定第5節に従い、AI生成本文・個人データを含まない固定テンプレートを使用する。
 * マイルストーンのみ、到達件数を反映する。
 */
fun buildReportNotificationContent(
    reportType: ReportType,
    reachedSignalCount: Int = 0
): Pair<String, String> {
    return when (reportType) {
        ReportType.WEEKLY ->
            "📊 1週間の気付きレポートができました" to
                "この1週間の変化と、新しく見えてきた傾向を確認できます"

        ReportType.MONTHLY ->
            "🌱 1か月の気付きレポートができました" to
                "この30日間の進み方と、続けられたペースを振り返れます"

        ReportType.MILESTONE -> {
            val count = if (reachedSignalCount > 0) reachedSignalCount else 10
            "🎯 シグナルが${count}件溜まりました" to
                "新しく見えてきた自分の傾向を確認できます"
        }
    }
}

/**
 * セッションIDとレポート種別から一意な通知IDを生成する。
 * 週次と月次、および別セッションで衝突しない。
 */
fun reportNotificationIdFor(sessionId: Int, reportType: ReportType): Int {
    return reportIdentityFor(sessionId, reportType)
}

/**
 * セッションIDとレポート種別から一意な PendingIntent requestCode を生成する。
 */
fun reportRequestCodeFor(sessionId: Int, reportType: ReportType): Int {
    return reportIdentityFor(sessionId, reportType)
}

// 案件27の週次・月次通知ID領域を維持する。
// MILESTONE は ordinal に依存せず、別領域へ明示的に割り当てる。
private const val WEEKLY_MONTHLY_BASE = 30_000
private const val WEEKLY_OFFSET = 0
private const val MONTHLY_OFFSET = 1
private const val MILESTONE_BASE = 1_000_000

private fun reportIdentityFor(sessionId: Int, reportType: ReportType): Int =
    when (reportType) {
        ReportType.WEEKLY -> WEEKLY_MONTHLY_BASE + sessionId * 2 + WEEKLY_OFFSET
        ReportType.MONTHLY -> WEEKLY_MONTHLY_BASE + sessionId * 2 + MONTHLY_OFFSET
        ReportType.MILESTONE -> MILESTONE_BASE + sessionId
    }

/**
 * 本番用の気付きレポート通知発行クラス。
 */
class AndroidDiscoveryReportNotifier(context: Context) : DiscoveryReportNotifier {

    private val appContext = context.applicationContext

    override fun notifyReport(
        reportType: ReportType,
        sessionId: Int,
        reachedSignalCount: Int
    ): Boolean {
        ensureChannel()
        val manager = NotificationManagerCompat.from(appContext)
        if (!manager.areNotificationsEnabled()) return false

        val (title, body) = buildReportNotificationContent(reportType, reachedSignalCount)

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(contentIntent(reportType, sessionId))
            .setAutoCancel(true)
            .build()

        manager.notify(reportNotificationIdFor(sessionId, reportType), notification)
        return true
    }

    private fun contentIntent(reportType: ReportType, sessionId: Int): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_REPORT_TYPE, reportType.value)
            .putExtra(MainActivity.EXTRA_REPORT_SESSION_ID, sessionId)
        return PendingIntent.getActivity(
            appContext,
            reportRequestCodeFor(sessionId, reportType),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            appContext.getString(R.string.discovery_periodic_report_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "discovery_periodic_report"
    }
}
