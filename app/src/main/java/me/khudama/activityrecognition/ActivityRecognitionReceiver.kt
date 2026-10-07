package me.khudama.activityrecognition

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.ActivityRecognitionResult

class ActivityRecognitionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ActivityRecognitionRec"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        if (ActivityRecognitionResult.hasResult(intent)) {
            val result = ActivityRecognitionResult.extractResult(intent) ?: return
            val probableActivities = result.probableActivities
                .map { ActivityTypeUtils.fromDetectedActivity(it) }
                .sortedByDescending { it.confidence }

            val mostProbableActivity = result.mostProbableActivity?.let {
                ActivityTypeUtils.fromDetectedActivity(it)
            } ?: probableActivities.firstOrNull() ?: return

            Log.d(
                TAG,
                "Detected most probable activity: ${mostProbableActivity.name} with ${mostProbableActivity.confidence}% confidence"
            )

            val updateEvent = ActivityUpdateEvent(
                mostProbable = mostProbableActivity,
                allActivities = probableActivities,
                timestamp = result.time
            )

            // Post or update the notification with the highest confidence activity & details
            ActivityNotificationHelper.showActivityNotification(context, updateEvent)

            // Update app repository state
            ActivityRepository.onNewActivityEvent(context, updateEvent)
        }
    }
}
