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
        val action = if (isSessionEnd) MainActivity.ACTION_SHOW_ALARM else MainActivity.ACTION_BEGIN_FOCUS
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            this.action = action
            putExtra(EXTRA_DURATION_MINUTES, durationMinutes)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val launchPendingIntent = PendingIntent.getActivity(
            context,
            if (isSessionEnd) REQUEST_ALARM_SCREEN else REQUEST_FOCUS_SCREEN,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        createChannel(notificationManager)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if (isSessionEnd) "Waktu fokus selesai" else "Jadwal fokus tiba")
            .setContentText(
                if (isSessionEnd) "Buka Smart Alarm untuk memilih cara menghentikan alarm."
                else "Ketuk untuk memulai sesi fokus $durationMinutes menit."
            )
            .setContentIntent(launchPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        notificationManager.notify(if (isSessionEnd) NOTIFICATION_ALARM else NOTIFICATION_SCHEDULE, notification)

        if (isSessionEnd) {
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ALARM_PENDING, true)
                .apply()
        }
    }

    companion object {
        const val CHANNEL_ID = "smart_alarm"
        const val EXTRA_SESSION_END = "session_end"
        const val EXTRA_DURATION_MINUTES = "duration_minutes"
        const val PREFERENCES = "smart_alarm_state"
        const val KEY_ALARM_PENDING = "alarm_pending"
        const val KEY_FOCUS_END = "focus_end"
        const val KEY_SCHEDULE_AT = "schedule_at"
        const val KEY_SCHEDULE_DURATION = "schedule_duration"

        private const val REQUEST_ALARM_SCREEN = 2002
        private const val REQUEST_FOCUS_SCREEN = 2001
        private const val NOTIFICATION_ALARM = 2002
        private const val NOTIFICATION_SCHEDULE = 2001

        fun schedule(context: Context, triggerAtMillis: Long, durationMinutes: Int, sessionEnd: Boolean) {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra(EXTRA_SESSION_END, sessionEnd)
                putExtra(EXTRA_DURATION_MINUTES, durationMinutes)
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

        private fun createChannel(manager: NotificationManager) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Smart Alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "Jadwal fokus dan alarm akhir sesi"
                        setSound(android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI, null)
                    }
                )
            }
        }
    }
}