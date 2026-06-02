package com.cliplist.app.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.cliplist.app.R

/** The notification channel + builder for the playlist-build foreground service. */
object BuildNotifications {
    const val CHANNEL_ID = "playlist_build"
    const val NOTIFICATION_ID = 1001
    private const val BRAND_GREEN = 0xFF0E7C7B.toInt()   // brand teal; tints the small icon/accent

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.notif_channel_build),
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply { description = context.getString(R.string.notif_channel_build_desc) }
                )
            }
        }
    }

    /** Ongoing build notification. When [indeterminate] is false, shows a determinate bar. */
    fun build(
        context: Context,
        text: String,
        done: Int,
        total: Int,
        indeterminate: Boolean,
    ): Notification {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_playlist)
            .setContentTitle(context.getString(R.string.notif_build_title))
            .setContentText(text)
            .setColor(BRAND_GREEN)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(if (indeterminate) 0 else total, if (indeterminate) 0 else done, indeterminate)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
