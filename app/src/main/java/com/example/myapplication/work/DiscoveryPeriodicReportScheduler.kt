package com.example.myapplication.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 案件27：週次／月次「気付きレポート」Worker の登録・解除を行うスケジューラ。
 *
 * 24時間間隔の一意な PeriodicWorkRequest として登録する。
 * ネットワーク接続（NetworkType.CONNECTED）を制約として設定する。
 */
object DiscoveryPeriodicReportScheduler {

    private const val UNIQUE_WORK_NAME = DiscoveryPeriodicReportWorker.WORK_NAME

    /** 定期 Worker を登録する。既に登録済みなら上書きしない（KEEP）。 */
    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<DiscoveryPeriodicReportWorker>(
            24,
            TimeUnit.HOURS
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
