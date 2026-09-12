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
import com.mikke.discovery.TaskStartActionReceiver
import com.mikke.discovery.data.Task

/**
 * 「空き時間です」通知を発行する処理を抽象化する。
 * Unit テストでは Android の通知 API が動かないため、本番用とテスト用を差し替えられるようにする。
 */
interface FreeTimeNotifier {
    fun notifyTaskStart(task: Task)
}

/**
 * 本番用の通知発行。
 *
 * 通知権限（API 33+ の POST_NOTIFICATIONS、または端末設定で無効化されている場合を含む）が
 * 無ければ [NotificationManagerCompat.areNotificationsEnabled] が false を返すので、
 * その場合は静かに何もしない（[android.app.NotificationManager.notify] を SecurityException で
 * クラッシュさせないため）。
 */
class AndroidFreeTimeNotifier(context: Context) : FreeTimeNotifier {
    private val appContext = context.applicationContext

    override fun notifyTaskStart(task: Task) {
        ensureChannel()
        val manager = NotificationManagerCompat.from(appContext)
        if (!manager.areNotificationsEnabled()) return

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(appContext.getString(R.string.free_time_notification_title))
            .setContentText(
                appContext.getString(R.string.free_time_notification_body, task.title)
            )
            .setContentIntent(contentIntent())
            .addAction(
                0,
                appContext.getString(R.string.free_time_notification_start_action),
                startActionIntent(task.id)
            )
            .setAutoCancel(true)
            .build()

        manager.notify(notificationIdFor(task.id), notification)
    }

    private fun contentIntent(): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /** リクエストコードにタスク ID を使い、別タスクの通知と PendingIntent が衝突しないようにする。 */
    private fun startActionIntent(taskId: Int): PendingIntent {
        val intent = Intent(appContext, TaskStartActionReceiver::class.java)
            .putExtra(TaskStartActionReceiver.EXTRA_TASK_ID, taskId)
        return PendingIntent.getBroadcast(
            appContext,
            taskId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            appContext.getString(R.string.free_time_notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "free_time_check"

        /**
         * タスク ID から通知 ID を採番する。タスクごとに別々の通知として出すため、
         * また [TaskStartActionReceiver] が同じ通知を確実に閉じられるようにするため、
         * ID の決め方をここに集約する。
         */
        fun notificationIdFor(taskId: Int): Int = NOTIFICATION_ID_BASE + taskId

        private const val NOTIFICATION_ID_BASE = 10_000
    }
}
