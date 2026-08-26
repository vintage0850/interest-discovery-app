package com.example.myapplication.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.myapplication.data.Task

/**
 * タスクごとの手動通知時刻の予約を抽象化する。
 * 単体テストでは AlarmManager が動かないため、本番用とテスト用を差し替えられるようにする。
 */
interface TaskNotificationScheduler {
    /** [Task.notificationTime] が null なら何もしない。 */
    fun schedule(task: Task)

    fun cancel(taskId: Int)
}

/**
 * 本番用。[AlarmManager] で指定時刻に1回だけ [TaskNotificationReceiver] を起こす。
 *
 * 「正確なアラーム」権限（Android 12+ の SCHEDULE_EXACT_ALARM）が無い端末では
 * [AlarmManager.setAndAllowWhileIdle]（数分の誤差を許容）にフォールバックする。
 * 権限が無いことでクラッシュしたり、無反応になったりはしない。
 */
class AndroidTaskNotificationScheduler(context: Context) : TaskNotificationScheduler {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    override fun schedule(task: Task) {
        val triggerAtMillis = task.notificationTime ?: return
        val pendingIntent = pendingIntentFor(task.id)
        val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        if (canScheduleExact) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    override fun cancel(taskId: Int) {
        alarmManager.cancel(pendingIntentFor(taskId))
    }

    private fun pendingIntentFor(taskId: Int): PendingIntent {
        val intent = Intent(appContext, TaskNotificationReceiver::class.java)
            .putExtra(TaskNotificationReceiver.EXTRA_TASK_ID, taskId)
        return PendingIntent.getBroadcast(
            appContext,
            taskId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
