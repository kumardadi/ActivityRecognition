package me.khudama.activityrecognition

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition

object ActivityRecognitionManager {

    private const val TAG = "ActivityRecManager"
    private const val REQUEST_CODE = 101

    // 1 minute detection interval (60,000 milliseconds)
    const val DETECTION_INTERVAL_MILLIS = 60_000L

    private fun getPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ActivityRecognitionReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

    fun hasActivityRecognitionPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun requestActivityUpdates(
        context: Context,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        if (!hasActivityRecognitionPermission(context)) {
            Log.w(TAG, "Activity recognition permission not granted")
            onFailure(SecurityException("ACTIVITY_RECOGNITION permission is required"))
            return
        }

        try {
            val client = ActivityRecognition.getClient(context)
            val pendingIntent = getPendingIntent(context)

            client.requestActivityUpdates(DETECTION_INTERVAL_MILLIS, pendingIntent)
                .addOnSuccessListener {
                    Log.d(TAG, "Successfully registered for activity updates (every 1 min)")
                    ActivityRepository.setTracking(context, true)
                    onSuccess()
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Failed to register activity updates: ${exception.message}", exception)
                    onFailure(exception)
                }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while requesting activity updates", e)
            onFailure(e)
        }
    }

    fun removeActivityUpdates(
        context: Context,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        try {
            val client = ActivityRecognition.getClient(context)
            val pendingIntent = getPendingIntent(context)

            client.removeActivityUpdates(pendingIntent)
                .addOnSuccessListener {
                    Log.d(TAG, "Successfully removed activity updates")
                    ActivityRepository.setTracking(context, false)
                    ActivityNotificationHelper.cancelNotification(context)
                    onSuccess()
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Failed to remove activity updates: ${exception.message}", exception)
                    onFailure(exception)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception while removing activity updates", e)
            onFailure(e)
        }
    }
}
