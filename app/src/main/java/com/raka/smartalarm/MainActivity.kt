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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val preferences by lazy {
        getSharedPreferences(AlarmReceiver.PREFERENCES, MODE_PRIVATE)
    }
    private var selectedDuration by mutableIntStateOf(25)
    private var startHour by mutableIntStateOf(17)
    private var startMinute by mutableIntStateOf(0)
    private var endHour by mutableIntStateOf(5)
    private var endMinute by mutableIntStateOf(0)
    private var use24Hour by mutableStateOf(true)
    private var useEnglish by mutableStateOf(Locale.getDefault().language == "en")
    private var screen by mutableStateOf(Screen.HOME)
    private var alarmPending by mutableStateOf(false)
    private var statusMessage by mutableStateOf("")

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
        startHour = preferences.getInt(AlarmReceiver.KEY_START_HOUR, startHour)
        startMinute = preferences.getInt(AlarmReceiver.KEY_START_MINUTE, startMinute)
        endHour = preferences.getInt(AlarmReceiver.KEY_END_HOUR, endHour)
        endMinute = preferences.getInt(AlarmReceiver.KEY_END_MINUTE, endMinute)
        use24Hour = preferences.getBoolean(AlarmReceiver.KEY_TIME_FORMAT_24, true)
        useEnglish = preferences.getString(AlarmReceiver.KEY_LANGUAGE, null)?.let { it == "en" }
            ?: (Locale.getDefault().language == "en")
        statusMessage = localizedString(R.string.status_default)
        selectedDuration = preferences.getInt(AlarmReceiver.KEY_SCHEDULE_DURATION, selectedDuration)
        if (preferences.getBoolean(AlarmReceiver.KEY_SCHEDULE_ENABLED, false) && scheduleAt > 0L) {
            statusMessage = localizedString(R.string.schedule_active_status)
        }
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_SHOW_ALARM -> {
                screen = Screen.ALARM
                alarmPending = true
                preferences.edit().putBoolean(AlarmReceiver.KEY_ALARM_PENDING, true).apply()
            }
            ACTION_BEGIN_FOCUS -> {
                val endAt = intent.getLongExtra(AlarmReceiver.EXTRA_END_AT, 0L)
                if (endAt > System.currentTimeMillis()) startFocusUntil(endAt)
                else statusMessage = localizedString(R.string.schedule_missed_status)
            }
        }
        intent?.action = null
    }

    private fun render() {
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val configuration = LocalConfiguration.current.copy().apply {
                        setLocale(Locale(if (useEnglish) "en" else "id"))
                    }
                    CompositionLocalProvider(LocalConfiguration provides configuration) {
                        when (screen) {
                            Screen.HOME -> HomeScreen(
                            duration = selectedDuration,
                            startLabel = AlarmReceiver.formatTime(this, startHour, startMinute),
                            endLabel = AlarmReceiver.formatTime(this, endHour, endMinute),
                            status = statusMessage,
                            use24Hour = use24Hour,
                            useEnglish = useEnglish,
                            onDurationChange = { selectedDuration = it },
                            onPickStart = {
                                TimePickerDialog(this, { _, hour, minute ->
                                    startHour = hour
                                    startMinute = minute
                                }, startHour, startMinute, use24Hour).show()
                            },
                            onPickEnd = {
                                TimePickerDialog(this, { _, hour, minute ->
                                    endHour = hour
                                    endMinute = minute
                                }, endHour, endMinute, use24Hour).show()
                            },
                            onTimeFormatChange = { selected ->
                                use24Hour = selected
                                preferences.edit().putBoolean(AlarmReceiver.KEY_TIME_FORMAT_24, selected).apply()
                            },
                            onLanguageChange = { english ->
                                useEnglish = english
                                preferences.edit().putString(AlarmReceiver.KEY_LANGUAGE, if (english) "en" else "id").apply()
                            },
                            onSchedule = ::scheduleFocus,
                            onCancelSchedule = ::cancelSchedule,
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
    }

    private fun scheduleFocus() {
        val now = LocalDateTime.now()
        val start = LocalTime.of(startHour, startMinute)
        val end = LocalTime.of(endHour, endMinute)
        if (start == end) {
            statusMessage = localizedString(R.string.invalid_window)
            render()
            return
        }

        val currentTime = now.toLocalTime()
        val inWindow = if (end.isAfter(start)) {
            !currentTime.isBefore(start) && currentTime.isBefore(end)
        } else {
            !currentTime.isBefore(start) || currentTime.isBefore(end)
        }

        val startAt = if (inWindow) now.plusSeconds(2) else {
            var next = LocalDateTime.of(now.toLocalDate(), start)
            if (!next.isAfter(now)) next = next.plusDays(1)
            next
        }
        var endAt = LocalDateTime.of(startAt.toLocalDate(), end)
        if (!end.isAfter(startAt.toLocalTime())) endAt = endAt.plusDays(1)
        val startTimestamp = startAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endTimestamp = endAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        AlarmReceiver.cancel(this, sessionEnd = false)
        AlarmReceiver.cancelScheduleNotification(this)
        preferences.edit()
            .putLong(AlarmReceiver.KEY_SCHEDULE_AT, startTimestamp)
            .putInt(AlarmReceiver.KEY_SCHEDULE_DURATION, selectedDuration)
            .putInt(AlarmReceiver.KEY_START_HOUR, startHour)
            .putInt(AlarmReceiver.KEY_START_MINUTE, startMinute)
            .putInt(AlarmReceiver.KEY_END_HOUR, endHour)
            .putInt(AlarmReceiver.KEY_END_MINUTE, endMinute)
            .putBoolean(AlarmReceiver.KEY_SCHEDULE_ENABLED, true)
            .apply()
        AlarmReceiver.schedule(this, startTimestamp, selectedDuration, sessionEnd = false, endAtMillis = endTimestamp)
        statusMessage = localizedString(R.string.schedule_saved_status, formatTime(startHour, startMinute), formatTime(endHour, endMinute))
        render()
    }

    private fun startFocus(durationMinutes: Int) {
        selectedDuration = durationMinutes.coerceIn(1, 180)
        startFocusUntil(System.currentTimeMillis() + selectedDuration * 60_000L)
    }

    private fun startFocusUntil(endAt: Long) {
        alarmPending = false
        preferences.edit()
            .putLong(AlarmReceiver.KEY_FOCUS_END, endAt)
            .putBoolean(AlarmReceiver.KEY_ALARM_PENDING, false)
            .apply()
        AlarmReceiver.schedule(this, endAt, selectedDuration, sessionEnd = true, endAtMillis = endAt)
        screen = Screen.FOCUS
        statusMessage = localizedString(R.string.session_running)
        render()
    }

    private fun cancelSchedule() {
        AlarmReceiver.cancel(this, sessionEnd = false)
        AlarmReceiver.cancelScheduleNotification(this)
        preferences.edit()
            .putBoolean(AlarmReceiver.KEY_SCHEDULE_ENABLED, false)
            .remove(AlarmReceiver.KEY_SCHEDULE_AT)
            .apply()
        statusMessage = localizedString(R.string.schedule_canceled)
        render()
    }

    private fun emergencyExit() {
        AlarmReceiver.cancel(this, sessionEnd = true)
        preferences.edit().remove(AlarmReceiver.KEY_FOCUS_END).apply()
        screen = Screen.HOME
        statusMessage = localizedString(R.string.session_stopped)
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
        statusMessage = localizedString(R.string.alarm_stopped, choice)
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
    }

    private fun formatTime(hour: Int, minute: Int): String = AlarmReceiver.formatTime(this, hour, minute)

    private fun localizedString(id: Int, vararg arguments: Any): String {
        val configuration = resources.configuration.apply {
            setLocale(Locale(if (useEnglish) "en" else "id"))
        }
        return createConfigurationContext(configuration).getString(id, *arguments)
    }
}

private enum class Screen { HOME, FOCUS, ALARM }

@androidx.compose.runtime.Composable
private fun HomeScreen(
    duration: Int,
    startLabel: String,
    endLabel: String,
    status: String,
    use24Hour: Boolean,
    useEnglish: Boolean,
    onDurationChange: (Int) -> Unit,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
    onTimeFormatChange: (Boolean) -> Unit,
    onLanguageChange: (Boolean) -> Unit,
    onSchedule: () -> Unit,
    onCancelSchedule: () -> Unit,
    onStartNow: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.home_subtitle), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(28.dp))
        Text(stringResource(R.string.duration_title), style = MaterialTheme.typography.titleMedium)
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPickStart, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.start_time, startLabel))
            }
            OutlinedButton(onClick = onPickEnd, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.end_time, endLabel))
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onSchedule, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.save_schedule))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onCancelSchedule, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.cancel_schedule))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onStartNow, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.start_now))
        }
        Spacer(Modifier.height(20.dp))
        Text(status, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.time_format_title), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (use24Hour) Button(onClick = { onTimeFormatChange(true) }) { Text(stringResource(R.string.format_24h)) }
            else OutlinedButton(onClick = { onTimeFormatChange(true) }) { Text(stringResource(R.string.format_24h)) }
            if (!use24Hour) Button(onClick = { onTimeFormatChange(false) }) { Text(stringResource(R.string.format_12h)) }
            else OutlinedButton(onClick = { onTimeFormatChange(false) }) { Text(stringResource(R.string.format_12h)) }
        }
        Text(stringResource(R.string.language_title), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!useEnglish) Button(onClick = { onLanguageChange(false) }) { Text(stringResource(R.string.language_indonesian)) }
            else OutlinedButton(onClick = { onLanguageChange(false) }) { Text(stringResource(R.string.language_indonesian)) }
            if (useEnglish) Button(onClick = { onLanguageChange(true) }) { Text(stringResource(R.string.language_english)) }
            else OutlinedButton(onClick = { onLanguageChange(true) }) { Text(stringResource(R.string.language_english)) }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.device_limit), style = MaterialTheme.typography.bodySmall)
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
        Text(stringResource(R.string.focus_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.remaining_time, remainingSeconds / 60, remainingSeconds % 60), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.focus_description))
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onEmergencyExit, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.emergency_exit))
        }
    }
}

@androidx.compose.runtime.Composable
private fun AlarmScreen(onChoice: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.alarm_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.alarm_prompt))
        Spacer(Modifier.height(24.dp))
        listOf(stringResource(R.string.alarm_choice_1), stringResource(R.string.alarm_choice_2), stringResource(R.string.alarm_choice_3)).forEach { choice ->
            Button(onClick = { onChoice(choice) }, modifier = Modifier.fillMaxWidth()) {
                Text(choice)
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = { onChoice("Keluar darurat") }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.emergency_exit))
        }
    }
}