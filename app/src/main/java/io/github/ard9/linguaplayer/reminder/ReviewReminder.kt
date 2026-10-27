package io.github.ard9.linguaplayer.reminder

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
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.ard9.linguaplayer.LinguaApp
import io.github.ard9.linguaplayer.MainActivity
import io.github.ard9.linguaplayer.R
import io.github.ard9.linguaplayer.data.Leitner
import io.github.ard9.linguaplayer.inAppLanguage
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Optional daily notification when cards are due. One WorkManager job is scheduled for the
 * next reminder time; each run shows the notification (if anything is due) and schedules the
 * next one. WorkManager keeps the job across restarts of the phone.
 */
object ReviewReminder {
    private const val WORK = "review-reminder"
    private const val CHANNEL = "review"
    private const val NOTIFICATION_ID = 1

    fun schedule(context: Context, minutesOfDay: Int) {
        val delay = ReminderTime.delayUntilNext(Instant.now(), ZoneId.systemDefault(), minutesOfDay)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /** Whether the app may post notifications (always before Android 13). */
    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    internal fun notifyIfDue(appContext: Context, dueCount: Int) {
        if (dueCount <= 0 || !canNotify(appContext)) return
        val context = inAppLanguage(appContext)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(context.resources.getQuantityString(R.plurals.due_count, dueCount, dueCount))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission") // Checked in canNotify.
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}

/** Shows the reminder if cards are due, then schedules the next day's reminder. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as LinguaApp
        val prefs = app.settings.reminder.value
        if (!prefs.enabled) return Result.success()
        val now = System.currentTimeMillis()
        ReviewReminder.notifyIfDue(app, app.cards.cards.value.count { Leitner.isDue(it, now) })
        ReviewReminder.schedule(app, prefs.minutesOfDay)
        return Result.success()
    }
}
