package com.mikke.discovery

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * 「時刻を指定する」トグルと、ON のときだけ出る時刻表示ボタン＋ピッカーをまとめた部品。
 * AddTaskScreen（新規登録）と TaskEditDialog（事後編集）の両方から使う。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionalTimePicker(
    label: String,
    description: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    hour: Int,
    minute: Int,
    onTimeChange: (hour: Int, minute: Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
    }
    if (enabled) {
        TextButton(onClick = { showDialog = true }) {
            Text("時刻: %02d:%02d".format(hour, minute))
        }
    }

    if (showDialog) {
        val timePickerState = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = true
        )
        SimpleTimePickerDialog(
            onDismissRequest = { showDialog = false },
            onConfirm = {
                onTimeChange(timePickerState.hour, timePickerState.minute)
                showDialog = false
            }
        ) {
            TimePicker(state = timePickerState)
        }
    }
}

/**
 * Material3 には `DatePickerDialog` はあるが `TimePickerDialog` は無いため、
 * `AlertDialog` をベースに最小限のダイアログ枠を用意する。
 */
@Composable
internal fun SimpleTimePickerDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("確定") }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text("キャンセル") }
        },
        text = { content() }
    )
}
