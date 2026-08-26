package com.example.myapplication.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.TaskRepository
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
                repository.getTasksWithFutureNotification(System.currentTimeMillis())
                    .forEach { task -> scheduler.schedule(task) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
