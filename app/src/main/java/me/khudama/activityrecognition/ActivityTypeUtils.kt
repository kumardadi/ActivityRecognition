package me.khudama.activityrecognition

import com.google.android.gms.location.DetectedActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ActivityInfo(
    val type: Int,
    val name: String,
    val emoji: String,
    val confidence: Int,
    val iconResId: Int
)

data class ActivityUpdateEvent(
    val mostProbable: ActivityInfo,
    val allActivities: List<ActivityInfo>,
    val timestamp: Long = System.currentTimeMillis()
) {
    val formattedTime: String
        get() = SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date(timestamp))
}

object ActivityTypeUtils {

    fun getActivityName(type: Int): String {
        return when (type) {
            DetectedActivity.IN_VEHICLE -> "In Vehicle"
            DetectedActivity.ON_BICYCLE -> "On Bicycle"
            DetectedActivity.ON_FOOT -> "On Foot"
            DetectedActivity.RUNNING -> "Running"
            DetectedActivity.STILL -> "Still / Stationary"
            DetectedActivity.TILTING -> "Tilting"
            DetectedActivity.WALKING -> "Walking"
            DetectedActivity.UNKNOWN -> "Unknown"
            else -> "Unknown ($type)"
        }
    }

    fun getActivityEmoji(type: Int): String {
        return when (type) {
            DetectedActivity.IN_VEHICLE -> "🚗"
            DetectedActivity.ON_BICYCLE -> "🚲"
            DetectedActivity.ON_FOOT -> "👟"
            DetectedActivity.RUNNING -> "🏃"
            DetectedActivity.STILL -> "🧘"
            DetectedActivity.TILTING -> "📱"
            DetectedActivity.WALKING -> "🚶"
            DetectedActivity.UNKNOWN -> "❓"
            else -> "❓"
        }
    }

    fun getActivityIconRes(type: Int): Int {
        return when (type) {
            DetectedActivity.IN_VEHICLE -> R.drawable.ic_directions_car
            DetectedActivity.ON_BICYCLE -> R.drawable.ic_directions_bike
            DetectedActivity.RUNNING -> R.drawable.ic_directions_run
            DetectedActivity.STILL -> R.drawable.ic_motion_photos_paused
            DetectedActivity.WALKING, DetectedActivity.ON_FOOT -> R.drawable.ic_directions_walk
            else -> R.drawable.ic_directions_walk
        }
    }

    fun fromDetectedActivity(detectedActivity: DetectedActivity): ActivityInfo {
        val type = detectedActivity.type
        return ActivityInfo(
            type = type,
            name = getActivityName(type),
            emoji = getActivityEmoji(type),
            confidence = detectedActivity.confidence,
            iconResId = getActivityIconRes(type)
        )
    }
}
