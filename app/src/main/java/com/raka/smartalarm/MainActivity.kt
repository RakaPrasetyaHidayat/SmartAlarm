package com.raka.smartalarm

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private val preferences by lazy {
        getSharedPreferences(AlarmReceiver.PREFERENCES, MODE_PRIVATE)
    }
    private var selectedDuration by mutableIntStateOf(25)
    private var scheduledHour by mutableIntStateOf(8)
    private var scheduledMinute by mutableIntStateOf(0)
    private var screen by mutableStateOf(Screen.HOME)
    private var alarmPending by mutableStateOf(false)
    private var statusMessage by mutableStateOf("Belum ada jadwal fokus.")

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        restoreState()
        handleIntent(intent)
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
        render()
    }

    private fun restoreState() {
        alarmPending = preferences.getBoolean(AlarmReceiver.KEY_ALARM_PENDING, false)
        val endTime = preferences.getLong(AlarmReceiver.KEY_FOCUS_END, 0L)
        if (alarmPending) {
            screen = Screen.ALARM
        } else if (endTime > System.currentTimeMillis()) {
            screen = Screen.FOCUS
        } else {
            preferences.edit().remove(AlarmReceiver.KEY_FOCUS_END).apply()
        }
        val scheduleAt = preferences.getLong(AlarmReceiver.KEY_SCHEDULE_AT, 0L)
        if (scheduleAt > System.currentTimeMillis()) {
            val scheduled = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(scheduleAt), ZoneId.systemDefault())
            scheduledHour = scheduled.hour
            scheduledMinute = scheduled.minute
            selectedDuration = preferences.getInt(AlarmReceiver.KEY_SCHEDULE_DURATION, selectedDuration)
            statusMessage = "Fokus dijadwalkan pukul ${scheduled.toLocalTime().format(timeFormatter)}."
        }
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_SHOW_ALARM -> {
                screen = Screen.ALARM
                alarmPending = true
                preferences.edit().putBoolean(AlarmReceiver.KEY_ALARM_PENDING, true).apply()
            }
            ACTION_BEGIN_FOCUS -> startFocus(intent.getIntExtra(AlarmReceiver.EXTRA_DURATION_MINUTES, selectedDuration))
        }
        intent?.action = null
    }

    private fun render() {
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when (screen) {
                        Screen.HOME -> HomeScreen(
                            duration = selectedDuration,
                            scheduleLabel = String.format("%02d:%02d", scheduledHour, scheduledMinute),
                            status = statusMessage,
                            onDurationChange = { selectedDuration = it },
                            onPickTime = {
                                TimePickerDialog(this, { _, hour, minute ->
                                    scheduledHour = hour
                                    scheduledMinute = minute
                                }, scheduledHour, scheduledMinute, true).show()
                            },
                            onSchedule = ::scheduleFocus,
                            onStartNow = { startFocus(selectedDuration) },
                        )
                        Screen.FOCUS -> FocusScreen(
                            endTime = preferences.getLong(AlarmReceiver.KEY_FOCUS_END, 0L),
                            onEmergencyExit = ::emergencyExit,
                            onFinished = ::showAlarm,
                        )
                        Screen.ALARM -> AlarmScreen(onChoice = ::dismissAlarm)
                    }
                }
            }
        }
    }

    private fun scheduleFocus() {
        val now = LocalDateTime.now()
        var target = LocalDateTime.of(LocalDate.now(), LocalTime.of(scheduledHour, scheduledMinute))
        if (!target.isAfter(now)) target = target.plusDays(1)
        val timestamp = target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        AlarmReceiver.schedule(this, timestamp, selectedDuration, sessionEnd = false)
        preferences.edit()
            .putLong(AlarmReceiver.KEY_SCHEDULE_AT, timestamp)
            .putInt(AlarmReceiver.KEY_SCHEDULE_DURATION, selectedDuration)
            .apply()
        statusMessage = "Fokus dijadwalkan pukul ${target.toLocalTime().format(timeFormatter)}."
        render()
    }

    private fun startFocus(durationMinutes: Int) {
        selectedDuration = durationMinutes.coerceIn(1, 180)
        alarmPending = false
        val endAt = System.currentTimeMillis() + selectedDuration * 60_000L
        preferences.edit()
            .putLong(AlarmReceiver.KEY_FOCUS_END, endAt)
            .putBoolean(AlarmReceiver.KEY_ALARM_PENDING, false)
            .remove(AlarmReceiver.KEY_SCHEDULE_AT)
            .apply()
        AlarmReceiver.cancel(this, sessionEnd = false)
        AlarmReceiver.schedule(this, endAt, selectedDuration, sessionEnd = true)
        screen = Screen.FOCUS
        statusMessage = "Sesi fokus sedang berjalan."
        render()
    }

    private fun emergencyExit() {
        AlarmReceiver.cancel(this, sessionEnd = true)
        preferences.edit().remove(AlarmReceiver.KEY_FOCUS_END).apply()
        screen = Screen.HOME
        statusMessage = "Sesi fokus dihentikan."
        render()
    }

    private fun showAlarm() {
        alarmPending = true
        preferences.edit().putBoolean(AlarmReceiver.KEY_ALARM_PENDING, true).apply()
        screen = Screen.ALARM
        render()
    }

    private fun dismissAlarm(choice: String) {
        AlarmReceiver.cancel(this, sessionEnd = true)
        AlarmReceiver.clearNotifications(this)
        preferences.edit()
            .putBoolean(AlarmReceiver.KEY_ALARM_PENDING, false)
            .remove(AlarmReceiver.KEY_FOCUS_END)
            .putString("last_alarm_choice", choice)
            .apply()
        alarmPending = false
        screen = Screen.HOME
        statusMessage = "Alarm dihentikan: $choice"
        render()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        const val ACTION_SHOW_ALARM = "com.raka.smartalarm.SHOW_ALARM"
        const val ACTION_BEGIN_FOCUS = "com.raka.smartalarm.BEGIN_FOCUS"
        private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

private enum class Screen { HOME, FOCUS, ALARM }

@androidx.compose.runtime.Composable
private fun HomeScreen(
    duration: Int,
    scheduleLabel: String,
    status: String,
    onDurationChange: (Int) -> Unit,
    onPickTime: () -> Unit,
    onSchedule: () -> Unit,
    onStartNow: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Smart Alarm", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text("Atur sesi fokus dan alarm", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(28.dp))
        Text("Durasi fokus", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 25, 45, 60).forEach { minutes ->
                if (duration == minutes) {
                    Button(onClick = { onDurationChange(minutes) }) { Text("${minutes}m") }
                } else {
                    OutlinedButton(onClick = { onDurationChange(minutes) }) { Text("${minutes}m") }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onPickTime, modifier = Modifier.fillMaxWidth()) {
            Text("Waktu mulai: $scheduleLabel")
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onSchedule, modifier = Modifier.fillMaxWidth()) {
            Text("Simpan jadwal")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onStartNow, modifier = Modifier.fillMaxWidth()) {
            Text("Mulai fokus sekarang")
        }
        Spacer(Modifier.height(20.dp))
        Text(status, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            "Jadwal memberi notifikasi; ketuk notifikasi untuk memulai sesi. Aplikasi tidak mengunci perangkat Android.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@androidx.compose.runtime.Composable
private fun FocusScreen(endTime: Long, onEmergencyExit: () -> Unit, onFinished: () -> Unit) {
    var remainingSeconds by remember(endTime) {
        mutableIntStateOf(((endTime - System.currentTimeMillis()).coerceAtLeast(0L) / 1000L).toInt())
    }
    LaunchedEffect(endTime) {
        while (remainingSeconds > 0) {
            delay(1_000L)
            remainingSeconds = ((endTime - System.currentTimeMillis()).coerceAtLeast(0L) / 1000L).toInt()
        }
        onFinished()
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Sesi fokus", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text("${remainingSeconds / 60}:${String.format("%02d", remainingSeconds % 60)} tersisa", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))
        Text("Tetap di layar ini sampai alarm berbunyi, atau keluar kapan saja melalui tombol darurat.")
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onEmergencyExit, modifier = Modifier.fillMaxWidth()) {
            Text("Keluar darurat")
        }
    }
}

@androidx.compose.runtime.Composable
private fun AlarmScreen(onChoice: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Waktu fokus selesai", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text("Pilih satu jawaban untuk menghentikan alarm.")
        Spacer(Modifier.height(24.dp))
        listOf("Saya siap melanjutkan", "Saya akan istirahat sejenak", "Saya menyelesaikan satu tugas").forEach { choice ->
            Button(onClick = { onChoice(choice) }, modifier = Modifier.fillMaxWidth()) {
                Text(choice)
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = { onChoice("Keluar darurat") }, modifier = Modifier.fillMaxWidth()) {
            Text("Keluar darurat")
        }
    }
}