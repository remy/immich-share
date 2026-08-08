package app.immichshare

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.content.getSystemService

class ImmichShareApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createUploadChannel()
    }

    private fun createUploadChannel() {
        val channel = NotificationChannel(
            UPLOAD_CHANNEL_ID,
            getString(R.string.upload_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.upload_channel_description)
        }
        getSystemService<NotificationManager>()?.createNotificationChannel(channel)
    }

    companion object {
        const val UPLOAD_CHANNEL_ID = "uploads"
    }
}
