package com.mikke.discovery.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Discovery 空き時間通知 Worker の定期実行を登録・解除する。
 *
 * WorkManager の実行間隔は 15 分（最小値）で、実際の実行時刻は OS によって遅延しうる。
 * 通知 ON/OFF・権限・認可の判定は [DiscoveryFreeTimeWorker] 自身が行うため、
 * スケジュール側はアプリ起動時に 1 度登録するだけでよい。
 */
object DiscoveryNotificationScheduler {

    private const val UNIQUE_WORK_NAME = DiscoveryFreeTimeWorker.WORK_NAME

    /** 定期 Worker を登録する。既に登録済みなら上書きしない。 */
    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<DiscoveryFreeTimeWorker>(
            15,
            TimeUnit.MINUTES
        ).setConstraints(constraints)
            .build()

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
    }

    /** 定期 Worker を解除する。 */
    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}
