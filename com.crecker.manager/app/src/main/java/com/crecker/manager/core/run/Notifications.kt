package com.crecker.manager.core.run

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.crecker.manager.MainActivity
import com.crecker.manager.R
import com.crecker.manager.core.model.LogLevel
import com.crecker.manager.core.model.RunRecord
import com.crecker.manager.core.settings.localized

object Notifications {
    private const val CHANNEL_RUN_PROBLEMS = "run_problems"
    private const val CHANNEL_UPDATES = "updates"

    fun createChannels(context: Context) {
        val res = context.localized()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RUN_PROBLEMS, res.getString(R.string.notif_channel_run_problems), NotificationManager.IMPORTANCE_DEFAULT),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_UPDATES, res.getString(R.string.notif_channel_updates), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    fun canNotify(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun runProblem(context: Context, systemName: String, @StringRes actionTitleRes: Int, record: RunRecord) {
        val res = context.localized()
        val actionTitle = res.getString(actionTitleRes)
        val firstError = record.entries.firstOrNull { it.level == LogLevel.ERROR }?.message
        post(
            context,
            CHANNEL_RUN_PROBLEMS,
            record.id.hashCode(),
            title = "$systemName – ${res.getString(record.status.label)}",
            text = firstError ?: actionTitle,
            details = listOfNotNull(actionTitle, firstError).joinToString("\n"),
        )
    }

    /**
     * התראה על משהו חדש במערכת (למשל מיילים חדשים) – לא על תקלה.
     * [tag] קבוע: התראה חדשה עם אותו tag מחליפה את הקודמת במקום להצטבר.
     */
    fun systemUpdate(context: Context, tag: String, title: String, text: String, details: String = text) =
        post(context, CHANNEL_UPDATES, tag.hashCode(), title, text, details)

    private fun post(context: Context, channel: String, id: Int, title: String, text: String, details: String) {
        if (!canNotify(context)) return
        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // ההרשאה בוטלה בין הבדיקה לשליחה
        }
    }
}
