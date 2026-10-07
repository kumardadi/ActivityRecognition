package me.khudama.activityrecognition

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object ActivityNotificationHelper {

    const val CHANNEL_ID = "activity_recognition_channel"
    const val NOTIFICATION_ID = 1001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.notification_channel_name)
            val descriptionText = context.getString(R.string.notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(true)
                enableVibration(false)
            }
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showActivityNotification(context: Context, event: ActivityUpdateEvent) {
        createNotificationChannel(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val topActivity = event.mostProbable
        val title = "${topActivity.emoji} ${topActivity.name} (${topActivity.confidence}%)"
        val summaryText = "Highest confidence: ${topActivity.name} with ${topActivity.confidence}%"

        val bigTextBuilder = StringBuilder()
        bigTextBuilder.append("🏆 Highest: ${topActivity.name} (${topActivity.confidence}%)\n\n")
        bigTextBuilder.append("📊 Probable Activities:\n")
        for (activity in event.allActivities) {
            bigTextBuilder.append(" • ${activity.emoji} ${activity.name}: ${activity.confidence}%\n")
        }
        bigTextBuilder.append("\n⏱ Updated at ${event.formattedTime} (every 1 min)")

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(topActivity.iconResId)
            .setContentTitle(title)
            .setContentText(summaryText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(bigTextBuilder.toString())
                    .setSummaryText("Activity Update (1 min)")
            )
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(false)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun cancelNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
