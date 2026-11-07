package com.raka.smartalarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val isSessionEnd = intent.getBooleanExtra(EXTRA_SESSION_END, false)
        val durationMinutes = intent.getIntExtra(EXTRA_DURATION_MINUTES, 25)
        val endAtMillis = intent.getLongExtra(EXTRA_END_AT, 0L)
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val english = preferences.getString(KEY_LANGUAGE, null)?.let { it == "en" }
            ?: (java.util.Locale.getDefault().language == "en")
        val action = if (isSessionEnd) MainActivity.ACTION_SHOW_ALARM else MainActivity.ACTION_BEGIN_FOCUS
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            this.action = action
            putExtra(EXTRA_DURATION_MINUTES, durationMinutes)
            putExtra(EXTRA_END_AT, endAtMillis)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val launchPendingIntent = PendingIntent.getActivity(
            context,
            if (isSessionEnd) REQUEST_ALARM_SCREEN else REQUEST_FOCUS_SCREEN,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        createChannel(notificationManager, english)
        val endLabel = if (endAtMillis > 0L) {
            val end = java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(endAtMillis),
                java.time.ZoneId.systemDefault(),
            )
            formatTime(context, end.hour, end.minute)
        } else ""
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(
                when {
                    isSessionEnd && english -> "Focus time is over"
                    isSessionEnd -> "Waktu fokus selesai"
                    english -> "Focus schedule"
                    else -> "Jadwal fokus"
                }
            )
            .setContentText(
                when {
                    isSessionEnd && english -> "Open Smart Alarm and type the three displayed sentences to stop the alarm."
                    isSessionEnd -> "Buka Smart Alarm dan ketik tiga kalimat yang tampil untuk menghentikan alarm."
                    english -> "Tap to focus until $endLabel."
                    else -> "Ketuk untuk fokus sampai pukul $endLabel."
                }
            )
            .setContentIntent(launchPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        notificationManager.notify(if (isSessionEnd) NOTIFICATION_ALARM else NOTIFICATION_SCHEDULE, notification)

        if (isSessionEnd) {
            preferences.edit().putBoolean(KEY_ALARM_PENDING, true).apply()
        } else if (preferences.getBoolean(KEY_SCHEDULE_ENABLED, false)) {
            val now = java.time.LocalDateTime.now()
            val start = java.time.LocalTime.of(
                preferences.getInt(KEY_START_HOUR, 17),
                preferences.getInt(KEY_START_MINUTE, 0),
            )
            val end = java.time.LocalTime.of(
                preferences.getInt(KEY_END_HOUR, 5),
                preferences.getInt(KEY_END_MINUTE, 0),
            )
            var nextStart = java.time.LocalDateTime.of(now.toLocalDate(), start)
            if (!nextStart.isAfter(now)) nextStart = nextStart.plusDays(1)
            var nextEnd = java.time.LocalDateTime.of(nextStart.toLocalDate(), end)
            if (!end.isAfter(start)) nextEnd = nextEnd.plusDays(1)
            val nextStartMillis = nextStart.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val nextEndMillis = nextEnd.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            preferences.edit().putLong(KEY_SCHEDULE_AT, nextStartMillis).apply()
            schedule(context, nextStartMillis, durationMinutes, sessionEnd = false, endAtMillis = nextEndMillis)
        }
    }

    companion object {
        const val CHANNEL_ID = "smart_alarm"
        const val EXTRA_SESSION_END = "session_end"
        const val EXTRA_DURATION_MINUTES = "duration_minutes"
        const val EXTRA_END_AT = "end_at"
        const val PREFERENCES = "smart_alarm_state"
        const val KEY_ALARM_PENDING = "alarm_pending"
        const val KEY_FOCUS_END = "focus_end"
        const val KEY_SCHEDULE_AT = "schedule_at"
        const val KEY_SCHEDULE_DURATION = "schedule_duration"
        const val KEY_SCHEDULE_ENABLED = "schedule_enabled"
        const val KEY_START_HOUR = "start_hour"
        const val KEY_START_MINUTE = "start_minute"
        const val KEY_END_HOUR = "end_hour"
        const val KEY_END_MINUTE = "end_minute"
        const val KEY_TIME_FORMAT_24 = "time_format_24"
        const val KEY_LANGUAGE = "language"

        private const val REQUEST_ALARM_SCREEN = 2002
        private const val REQUEST_FOCUS_SCREEN = 2001
        private const val NOTIFICATION_ALARM = 2002
        private const val NOTIFICATION_SCHEDULE = 2001

        fun schedule(
            context: Context,
            triggerAtMillis: Long,
            durationMinutes: Int,
            sessionEnd: Boolean,
            endAtMillis: Long = 0L,
        ) {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra(EXTRA_SESSION_END, sessionEnd)
                putExtra(EXTRA_DURATION_MINUTES, durationMinutes)
                putExtra(EXTRA_END_AT, endAtMillis)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                if (sessionEnd) REQUEST_ALARM_SCREEN else REQUEST_FOCUS_SCREEN,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        }

        fun cancel(context: Context, sessionEnd: Boolean) {
            val intent = Intent(context, AlarmReceiver::class.java)
            val requestCode = if (sessionEnd) REQUEST_ALARM_SCREEN else REQUEST_FOCUS_SCREEN
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            ) ?: return
            context.getSystemService(AlarmManager::class.java).cancel(pendingIntent)
            pendingIntent.cancel()
        }

        fun clearNotifications(context: Context) {
            context.getSystemService(NotificationManager::class.java).cancelAll()
        }

        fun cancelScheduleNotification(context: Context) {
            context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_SCHEDULE)
        }

        fun formatTime(context: Context, hour: Int, minute: Int): String {
            val use24Hour = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getBoolean(KEY_TIME_FORMAT_24, true)
            val english = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE, null)?.let { it == "en" }
                ?: (java.util.Locale.getDefault().language == "en")
            val time = java.time.LocalTime.of(hour, minute)
            val pattern = if (use24Hour) "HH:mm" else "h:mm a"
            val locale = if (english) java.util.Locale.US else java.util.Locale("id", "ID")
            return time.format(java.time.format.DateTimeFormatter.ofPattern(pattern, locale))
        }

        private fun createChannel(manager: NotificationManager, english: Boolean) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Smart Alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = if (english) "Focus schedule and end-of-session alarm" else "Jadwal fokus dan alarm akhir sesi"
                        setSound(android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI, null)
                    }
                )
            }
        }
    }
}