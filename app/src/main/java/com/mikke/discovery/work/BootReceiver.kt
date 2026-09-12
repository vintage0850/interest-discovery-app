package com.mikke.discovery.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mikke.discovery.data.AppDatabase
import com.mikke.discovery.data.Task
import com.mikke.discovery.data.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 端末再起動で消える AlarmManager の予約を復元する。
 * マニフェストの intent-filter を BOOT_COMPLETED だけに絞ってあること。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = TaskRepository(AppDatabase.getDatabase(appContext).taskDao())
                val scheduler = AndroidTaskNotificationScheduler(appContext)
                handleBoot(System.currentTimeMillis(), repository::getTasksWithFutureNotification, scheduler)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        /**
         * 再起動復元の本体。未来の未完了タスクを抽出し、それぞれ AlarmManager に再予約する。
         * 単体テストでは実際の DB・AlarmManager を使えないため、対象取得と scheduler を注入可能にする。
         */
        suspend fun handleBoot(
            now: Long,
            getTasks: suspend (Long) -> List<Task>,
            scheduler: TaskNotificationScheduler
        ) {
            getTasks(now).forEach { task -> scheduler.schedule(task) }
        }
    }
}
