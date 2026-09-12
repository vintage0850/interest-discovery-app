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
 * 手動通知時刻の AlarmManager から呼ばれる。指定時刻に「始めさせる」通知を1回出す。
 *
 * マニフェストで exported="false" にすること（他アプリから任意のタスクを操作されないため）。
 */
class TaskNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getIntExtra(EXTRA_TASK_ID, -1)
        if (taskId == -1) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = TaskRepository(AppDatabase.getDatabase(appContext).taskDao())
                val notifier = AndroidFreeTimeNotifier(appContext)
                handleReceive(taskId, repository::getTaskById, notifier)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"

        /**
         * 受信処理の本体。DB からタスクを取得し、未完了なら通知を発行する。
         * 単体テストでは Android の通知 API が動かないため、repository・notifier を注入可能にする。
         */
        suspend fun handleReceive(
            taskId: Int,
            getTask: suspend (Int) -> Task?,
            notifier: FreeTimeNotifier
        ) {
            val task = getTask(taskId)
            // 発火までの間に完了・削除された可能性があるので、その場合は何もしない
            if (task != null && !task.isCompleted) {
                notifier.notifyTaskStart(task)
            }
        }
    }
}
