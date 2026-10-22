package com.crecker.manager.core.run

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.crecker.manager.MainActivity
import com.crecker.manager.R
import com.crecker.manager.core.model.LogLevel
import com.crecker.manager.core.model.RunRecord

object Notifications {
    private const val CHANNEL_RUN_PROBLEMS = "run_problems"

    fun createChannels(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_RUN_PROBLEMS,
            "כשלים בפעולות מתוזמנות",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun runProblem(context: Context, systemName: String, actionTitle: String, record: RunRecord) {
        if (!canNotify(context)) return
        val firstError = record.entries.firstOrNull { it.level == LogLevel.ERROR }?.message
        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_RUN_PROBLEMS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$systemName – ${record.status.label}")
            .setContentText(firstError ?: actionTitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(listOfNotNull(actionTitle, firstError).joinToString("\n")))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(record.id.hashCode(), notification)
        } catch (_: SecurityException) {
            // ההרשאה בוטלה בין הבדיקה לשליחה
        }
    }
}
