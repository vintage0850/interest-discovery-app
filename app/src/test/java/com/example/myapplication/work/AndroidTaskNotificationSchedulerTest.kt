package com.example.myapplication.work

import android.app.AlarmManager
import android.app.PendingIntent
import com.example.myapplication.data.Task
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AndroidTaskNotificationScheduler] の単体テスト。
 * AlarmManager/PendingIntent は JVM 上で動かないため、[AlarmOperations] と PendingIntent ファクトリーを偽装する。
 */
class AndroidTaskNotificationSchedulerTest {

    private data class ScheduledAlarm(
        val exact: Boolean,
        val type: Int,
        val triggerAtMillis: Long,
        val pendingIntent: PendingIntent?
    )

    private class FakeAlarmOperations : AlarmOperations {
        val scheduled = mutableListOf<ScheduledAlarm>()
        val cancelled = mutableListOf<PendingIntent?>()

        override fun setExactAndAllowWhileIdle(
            type: Int,
            triggerAtMillis: Long,
            operation: PendingIntent
        ) {
            scheduled.add(ScheduledAlarm(exact = true, type, triggerAtMillis, operation))
        }

        override fun setAndAllowWhileIdle(
            type: Int,
            triggerAtMillis: Long,
            operation: PendingIntent
        ) {
            scheduled.add(ScheduledAlarm(exact = false, type, triggerAtMillis, operation))
        }

        override fun cancel(operation: PendingIntent) {
            cancelled.add(operation)
        }
    }

    /**
     * taskId ごとに別の [PendingIntent] インスタンスを返すファクトリー。
     * 呼び出された taskId を記録し、taskId 別に PendingIntent が識別できることを検証する。
     */
    private class FakePendingIntentFactory {
        val requestedTaskIds = mutableListOf<Int>()
        private val intentsByTaskId = mutableMapOf<Int, PendingIntent>()

        val factory: (Int) -> PendingIntent = { taskId ->
            requestedTaskIds.add(taskId)
            intentsByTaskId.getOrPut(taskId) { mockk() }
        }

        fun intentFor(taskId: Int): PendingIntent = intentsByTaskId.getValue(taskId)
    }

    private fun createScheduler(
        alarmOperations: FakeAlarmOperations,
        canScheduleExact: Boolean,
        pendingIntentFactory: FakePendingIntentFactory = FakePendingIntentFactory()
    ): AndroidTaskNotificationScheduler {
        return AndroidTaskNotificationScheduler(
            alarmOperations = alarmOperations,
            pendingIntentFactory = pendingIntentFactory.factory,
            canScheduleExactAlarms = { canScheduleExact }
        )
    }

    @Test
    fun `exact許可時はsetExactAndAllowWhileIdleを使う`() {
        val alarmOps = FakeAlarmOperations()
        val scheduler = createScheduler(alarmOps, canScheduleExact = true)
        val task = createTask(notificationTime = 1_700_000_000_000L)

        scheduler.schedule(task)

        assertEquals("予約回数", 1, alarmOps.scheduled.size)
        val alarm = alarmOps.scheduled.single()
        assertTrue("exact alarm を使う", alarm.exact)
        assertEquals("RTC_WAKEUP", AlarmManager.RTC_WAKEUP, alarm.type)
        assertEquals("triggerAtMillis", 1_700_000_000_000L, alarm.triggerAtMillis)
    }

    @Test
    fun `exact未許可時はsetAndAllowWhileIdleにフォールバックする`() {
        val alarmOps = FakeAlarmOperations()
        val scheduler = createScheduler(alarmOps, canScheduleExact = false)
        val task = createTask(notificationTime = 1_700_000_000_000L)

        scheduler.schedule(task)

        assertEquals("予約回数", 1, alarmOps.scheduled.size)
        assertFalse("exact alarm は使わない", alarmOps.scheduled.single().exact)
    }

    @Test
    fun `notificationTimeがnullなら何も予約しない`() {
        val alarmOps = FakeAlarmOperations()
        val scheduler = createScheduler(alarmOps, canScheduleExact = true)
        val task = createTask(notificationTime = null)

        scheduler.schedule(task)

        assertTrue("予約が無い", alarmOps.scheduled.isEmpty())
    }

    @Test
    fun `cancelはtaskId別のPendingIntentを使う`() {
        val alarmOps = FakeAlarmOperations()
        val pendingIntentFactory = FakePendingIntentFactory()
        val scheduler = createScheduler(alarmOps, canScheduleExact = true, pendingIntentFactory)

        scheduler.cancel(42)
        scheduler.cancel(7)

        assertEquals("cancel 回数", 2, alarmOps.cancelled.size)
        assertEquals("factory に渡された taskId", listOf(42, 7), pendingIntentFactory.requestedTaskIds)
        // taskId が違えば別の PendingIntent インスタンスが cancel に渡っていること
        assertEquals(pendingIntentFactory.intentFor(42), alarmOps.cancelled[0])
        assertEquals(pendingIntentFactory.intentFor(7), alarmOps.cancelled[1])
        assertFalse(
            "taskId 42 と 7 で同じ PendingIntent を使い回してはいけない",
            alarmOps.cancelled[0] === alarmOps.cancelled[1]
        )
    }

    @Test
    fun `scheduleはtaskIdごとにPendingIntentファクトリーへ正しいIDを渡す`() {
        val alarmOps = FakeAlarmOperations()
        val pendingIntentFactory = FakePendingIntentFactory()
        val scheduler = createScheduler(alarmOps, canScheduleExact = true, pendingIntentFactory)

        scheduler.schedule(createTask(id = 42, notificationTime = 1_700_000_000_000L))
        scheduler.schedule(createTask(id = 7, notificationTime = 1_700_000_100_000L))

        assertEquals("factory に渡された taskId", listOf(42, 7), pendingIntentFactory.requestedTaskIds)
        assertFalse(
            "taskId 42 と 7 で同じ PendingIntent を使い回してはいけない",
            alarmOps.scheduled[0].pendingIntent === alarmOps.scheduled[1].pendingIntent
        )
    }

    private fun createTask(id: Int = 1, notificationTime: Long?): Task = Task(
        id = id,
        title = "テストタスク",
        deadline = 1_700_000_000_000L,
        importance = 2,
        urgency = 2,
        notificationTime = notificationTime
    )
}
