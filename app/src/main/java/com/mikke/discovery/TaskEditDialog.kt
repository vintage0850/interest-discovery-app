package com.mikke.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.mikke.discovery.data.Task
import java.util.Calendar

private const val TASK_TITLE_MAX_LENGTH = 50

/** タスク編集ダイアログでの確定結果。 */
data class TaskEditResult(
    val title: String,
    val eventHasTime: Boolean,
    val deadline: Long,
    val notificationTime: Long?
)

/**
 * タスク名・予定の時刻指定・通知時刻をまとめて編集するダイアログ。
 * 締切の「日付」自体はここでは変更できない（変更したい場合は削除して登録し直す運用とする）。
 */
@Composable
fun TaskEditDialog(
    task: Task,
    onConfirm: (TaskEditResult) -> Unit,
    onDismiss: () -> Unit
) {
    var title by rememberSaveable(task.id) { mutableStateOf(task.title) }

    val originalDeadline = task.deadline
    var eventHasTime by rememberSaveable(task.id) { mutableStateOf(task.eventHasTime) }
    val originalEventTime = remember(task.id) { Calendar.getInstance().apply { timeInMillis = originalDeadline } }
    var eventHour by rememberSaveable(task.id) { mutableIntStateOf(originalEventTime.get(Calendar.HOUR_OF_DAY)) }
    var eventMinute by rememberSaveable(task.id) { mutableIntStateOf(originalEventTime.get(Calendar.MINUTE)) }

    var notificationEnabled by rememberSaveable(task.id) { mutableStateOf(task.notificationTime != null) }
    val originalNotificationTime = remember(task.id) {
        Calendar.getInstance().apply { timeInMillis = task.notificationTime ?: task.deadline }
    }
    var notificationHour by rememberSaveable(task.id) {
        mutableIntStateOf(originalNotificationTime.get(Calendar.HOUR_OF_DAY))
    }
    var notificationMinute by rememberSaveable(task.id) {
        mutableIntStateOf(originalNotificationTime.get(Calendar.MINUTE))
    }

    val trimmedTitle = title.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("タスクを編集") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= TASK_TITLE_MAX_LENGTH) title = it },
                    label = { Text("タスク名") },
                    singleLine = true,
                    supportingText = { Text("${title.length} / $TASK_TITLE_MAX_LENGTH") }
                )

                OptionalTimePicker(
                    label = "予定の時刻を指定する",
                    description = "カレンダー連携中なら予定の時刻も更新されます",
                    enabled = eventHasTime,
                    onEnabledChange = { eventHasTime = it },
                    hour = eventHour,
                    minute = eventMinute,
                    onTimeChange = { h, m -> eventHour = h; eventMinute = m }
                )

                OptionalTimePicker(
                    label = "通知時刻を指定する",
                    description = "指定した時刻に必ず通知します",
                    enabled = notificationEnabled,
                    onEnabledChange = { notificationEnabled = it },
                    hour = notificationHour,
                    minute = notificationMinute,
                    onTimeChange = { h, m -> notificationHour = h; notificationMinute = m }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val deadline = combineDateAndTime(
                        originalDeadline,
                        if (eventHasTime) eventHour else 23,
                        if (eventHasTime) eventMinute else 59
                    )
                    val notificationTime = if (notificationEnabled) {
                        combineDateAndTime(originalDeadline, notificationHour, notificationMinute)
                    } else {
                        null
                    }
                    onConfirm(
                        TaskEditResult(
                            title = trimmedTitle,
                            eventHasTime = eventHasTime,
                            deadline = deadline,
                            notificationTime = notificationTime
                        )
                    )
                },
                enabled = trimmedTitle.isNotEmpty()
            ) {
                Text("変更")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}

/** 元の日付はそのまま保ち、時刻部分だけ差し替える（終日予定は 23:59:59 に正規化する）。 */
private fun combineDateAndTime(baseMillis: Long, hour: Int, minute: Int): Long =
    Calendar.getInstance().apply {
        timeInMillis = baseMillis
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, if (hour == 23 && minute == 59) 59 else 0)
    }.timeInMillis
