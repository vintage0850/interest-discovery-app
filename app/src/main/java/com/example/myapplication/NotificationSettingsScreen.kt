package com.example.myapplication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.NotificationWindow
import java.time.LocalTime

/**
 * 「空き時間です」通知（始めさせる通知）を出してよい時間帯を変更する画面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    initialWindow: NotificationWindow,
    onSave: (NotificationWindow) -> Unit,
    onBack: () -> Unit
) {
    var startHour by rememberSaveable { mutableIntStateOf(initialWindow.start.hour) }
    var startMinute by rememberSaveable { mutableIntStateOf(initialWindow.start.minute) }
    var endHour by rememberSaveable { mutableIntStateOf(initialWindow.end.hour) }
    var endMinute by rememberSaveable { mutableIntStateOf(initialWindow.end.minute) }
    var showStartPicker by rememberSaveable { mutableStateOf(false) }
    var showEndPicker by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("通知設定") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "「空き時間です」通知を出してよい時間帯を設定します。",
                style = MaterialTheme.typography.bodyMedium
            )

            OutlinedButton(onClick = { showStartPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text("開始: %02d:%02d".format(startHour, startMinute))
            }
            OutlinedButton(onClick = { showEndPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text("終了: %02d:%02d".format(endHour, endMinute))
            }

            Button(
                onClick = {
                    onSave(NotificationWindow(LocalTime.of(startHour, startMinute), LocalTime.of(endHour, endMinute)))
                    onBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存する")
            }
        }
    }

    if (showStartPicker) {
        val state = rememberTimePickerState(initialHour = startHour, initialMinute = startMinute, is24Hour = true)
        SimpleTimePickerDialog(
            onDismissRequest = { showStartPicker = false },
            onConfirm = {
                startHour = state.hour
                startMinute = state.minute
                showStartPicker = false
            }
        ) { TimePicker(state = state) }
    }
    if (showEndPicker) {
        val state = rememberTimePickerState(initialHour = endHour, initialMinute = endMinute, is24Hour = true)
        SimpleTimePickerDialog(
            onDismissRequest = { showEndPicker = false },
            onConfirm = {
                endHour = state.hour
                endMinute = state.minute
                showEndPicker = false
            }
        ) { TimePicker(state = state) }
    }
}
