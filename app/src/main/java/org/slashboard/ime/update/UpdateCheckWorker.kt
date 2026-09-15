package org.slashboard.ime.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slashboard.ime.BuildConfig
import org.slashboard.ime.settings.SettingsActivity
import java.util.concurrent.TimeUnit

class UpdateCheckWorker(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            performCheck(context)
            Result.success()
        } catch (e: Exception) {
            Log.e("UpdateCheckWorker", "Error in UpdateCheckWorker", e)
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "SlashboardPeriodicUpdateCheck"
        const val NOTIFICATION_ID = 2001
        const val CHANNEL_ID = "slashboard_updates"

        suspend fun performCheck(context: Context): UpdateInfo = withContext(Dispatchers.IO) {
            val manager = UpdateManager(context)
            val info = manager.checkForUpdates(BuildConfig.VERSION_NAME)
            Log.d("UpdateCheckWorker", "Update check result: hasUpdate=${info.hasUpdate}, latestVersion=${info.latestVersion}")
            if (info.hasUpdate) {
                showNotification(context, info)
            }
            info
        }

        fun showNotification(context: Context, info: UpdateInfo) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "App Updates",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for Slashboard updates"
                    enableLights(true)
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            // Check notification permission on Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasPerm = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasPerm) {
                    Log.w("UpdateCheckWorker", "POST_NOTIFICATIONS permission not granted")
                }
            }

            val contentIntent = Intent(context, SettingsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("show_update_dialog", true)
                putExtra("update_version", info.latestVersion)
                putExtra("update_url", info.downloadUrl)
                putExtra("update_notes", info.releaseNotes)
            }

            val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val contentPendingIntent = PendingIntent.getActivity(
                context,
                2002,
                contentIntent,
                pendingFlags
            )

            val updateNowIntent = Intent(context, UpdateNotificationReceiver::class.java).apply {
                action = "org.slashboard.ime.ACTION_UPDATE_NOW"
                putExtra("downloadUrl", info.downloadUrl)
            }
            val updateNowPendingIntent = PendingIntent.getBroadcast(
                context,
                2003,
                updateNowIntent,
                pendingFlags
            )

            val notesText = if (info.releaseNotes.isNotBlank()) {
                "\n\n" + info.releaseNotes
            } else {
                ""
            }

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle("New update available")
                .setContentText("Version ${info.latestVersion} is available. Tap to update.")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle("New update available")
                        .bigText("A new version of Slashboard (${info.latestVersion}) is available. Tap to update now.$notesText")
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setAutoCancel(true)
                .setContentIntent(contentPendingIntent)
                .addAction(android.R.drawable.ic_menu_save, "Update Now", updateNowPendingIntent)

            try {
                notificationManager.notify(NOTIFICATION_ID, builder.build())
            } catch (t: Throwable) {
                Log.e("UpdateCheckWorker", "Failed to post update notification", t)
            }
        }

        /**
         * Schedules update check every 30 minutes (half hour) using WorkManager.
         */
        fun schedulePeriodicCheck(context: Context) {
            runCatching {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                // WorkManager interval set to 30 minutes (half hour)
                val workRequest = PeriodicWorkRequestBuilder<UpdateCheckWorker>(30, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    workRequest
                )
                Log.d("UpdateCheckWorker", "Successfully scheduled periodic update check every 30 minutes")
            }.onFailure { it.printStackTrace() }
        }

        /**
         * Immediately checks for updates asynchronously.
         */
        fun checkNow(context: Context) {
            runCatching {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val workRequest = OneTimeWorkRequestBuilder<UpdateCheckWorker>()
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueue(workRequest)
            }.onFailure { it.printStackTrace() }
        }

        @Deprecated("Use schedulePeriodicCheck", ReplaceWith("schedulePeriodicCheck(context)"))
        fun scheduleDaily8AMCheck(context: Context) {
            schedulePeriodicCheck(context)
        }
    }
}
