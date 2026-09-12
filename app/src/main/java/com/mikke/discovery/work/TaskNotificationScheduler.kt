package com.mikke.discovery.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.mikke.discovery.data.Task

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
 * AlarmManager の操作を抽象化するインターフェース。
 * 単体テストでは実際の AlarmManager を使えないため、偽装実装に差し替える。
 */
interface AlarmOperations {
    fun setExactAndAllowWhileIdle(type: Int, triggerAtMillis: Long, operation: PendingIntent)
    fun setAndAllowWhileIdle(type: Int, triggerAtMillis: Long, operation: PendingIntent)
    fun cancel(operation: PendingIntent)
}

/**
 * 本番用の [AlarmOperations] 実装。
 */
class AndroidAlarmOperations(private val alarmManager: AlarmManager) : AlarmOperations {
    override fun setExactAndAllowWhileIdle(type: Int, triggerAtMillis: Long, operation: PendingIntent) {
        alarmManager.setExactAndAllowWhileIdle(type, triggerAtMillis, operation)
    }

    override fun setAndAllowWhileIdle(type: Int, triggerAtMillis: Long, operation: PendingIntent) {
        alarmManager.setAndAllowWhileIdle(type, triggerAtMillis, operation)
    }

    override fun cancel(operation: PendingIntent) {
        alarmManager.cancel(operation)
    }
}

/**
 * 本番用。[AlarmManager] で指定時刻に1回だけ [TaskNotificationReceiver] を起こす。
 *
 * 「正確なアラーム」権限（Android 12+ の SCHEDULE_EXACT_ALARM）が無い端末では
 * [AlarmManager.setAndAllowWhileIdle]（数分の誤差を許容）にフォールバックする。
 * 権限が無いことでクラッシュしたり、無反応になったりはしない。
 */
class AndroidTaskNotificationScheduler(
    private val alarmOperations: AlarmOperations,
    private val pendingIntentFactory: (Int) -> PendingIntent,
    private val canScheduleExactAlarms: () -> Boolean
) : TaskNotificationScheduler {

    constructor(context: Context) : this(
        alarmOperations = AndroidAlarmOperations(
            context.applicationContext.getSystemService(AlarmManager::class.java)
        ),
        pendingIntentFactory = { taskId ->
            val appContext = context.applicationContext
            val intent = Intent(appContext, TaskNotificationReceiver::class.java)
                .putExtra(TaskNotificationReceiver.EXTRA_TASK_ID, taskId)
            PendingIntent.getBroadcast(
                appContext,
                taskId,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        },
        canScheduleExactAlarms = {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                context.applicationContext.getSystemService(AlarmManager::class.java)
                    .canScheduleExactAlarms()
        }
    )

    override fun schedule(task: Task) {
        val triggerAtMillis = task.notificationTime ?: return
        val pendingIntent = pendingIntentFactory(task.id)
        if (canScheduleExactAlarms()) {
            alarmOperations.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } else {
            alarmOperations.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    override fun cancel(taskId: Int) {
        alarmOperations.cancel(pendingIntentFactory(taskId))
    }
}
