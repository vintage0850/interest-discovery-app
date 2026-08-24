package com.example.myapplication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.TaskRepository
import com.example.myapplication.data.TaskStatus
import com.example.myapplication.work.AndroidFreeTimeNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 通知の「始める」ボタンから呼ばれる。UI を起動せず、対象タスクを進行中にして通知を閉じる。
 *
 * マニフェストで exported="false" にすること（他アプリから任意のタスクを操作されないため）。
 */
class TaskStartActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getIntExtra(EXTRA_TASK_ID, -1)
        if (taskId == -1) return

        NotificationManagerCompat.from(context)
            .cancel(AndroidFreeTimeNotifier.notificationIdFor(taskId))

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // status だけを更新する部分更新。Task 全列を上書きすると他の変更を巻き戻す恐れがある
                TaskRepository(AppDatabase.getDatabase(appContext).taskDao())
                    .updateStatus(taskId, TaskStatus.IN_PROGRESS)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
    }
}
