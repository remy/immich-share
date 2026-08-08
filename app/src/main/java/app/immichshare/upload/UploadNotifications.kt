package app.immichshare.upload

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import app.immichshare.ImmichShareApp
import app.immichshare.MainActivity
import app.immichshare.R

class UploadNotifications(private val context: Context) {

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        PendingIntent.FLAG_IMMUTABLE,
    )

    fun progress(done: Int, total: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(context, ImmichShareApp.UPLOAD_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notification_uploading))
            .setContentText(context.getString(R.string.notification_progress, done, total))
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setProgress(total, done, total == 0)
            .setOngoing(true)
            .setContentIntent(openApp())
            .build()

        // On Android 14+ a worker calling setForeground must declare a service
        // type, and it has to match the manifest's SystemForegroundService entry.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    /**
     * Deliberately reports duplicates and failures separately rather than
     * collapsing them into "done": a duplicate is a success, a failure is not,
     * and conflating them hides the case worth acting on.
     */
    fun result(uploaded: Int, duplicates: Int, failed: Int) {
        val parts = buildList {
            if (uploaded > 0) add(context.resources.getQuantityString(R.plurals.result_uploaded, uploaded, uploaded))
            if (duplicates > 0) {
                add(context.resources.getQuantityString(R.plurals.result_duplicates, duplicates, duplicates))
            }
            if (failed > 0) {
                add(context.resources.getQuantityString(R.plurals.result_failed, failed, failed))
            }
        }
        if (parts.isEmpty()) return

        val notification = NotificationCompat.Builder(context, ImmichShareApp.UPLOAD_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(parts.joinToString(" · "))
            .setSmallIcon(
                if (failed > 0) android.R.drawable.stat_notify_error
                else android.R.drawable.stat_sys_upload_done
            )
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()

        notify(notification)
    }

    fun failure(message: String) {
        val notification = NotificationCompat.Builder(context, ImmichShareApp.UPLOAD_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notification_failed))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()

        notify(notification)
    }

    /**
     * POST_NOTIFICATIONS may be denied. The upload itself still succeeded, so a
     * missing notification is not a failure — check inline (lint does not trace
     * the check through a helper), then post.
     */
    private fun notify(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching {
            NotificationManagerCompat.from(context).notify(RESULT_ID, notification)
        }
    }

    private companion object {
        const val NOTIFICATION_ID = 1
        const val RESULT_ID = 2
    }
}
