package me.khudama.activityrecognition

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

object ActivityRepository {

    private const val PREFS_NAME = "activity_recognition_prefs"
    private const val KEY_IS_TRACKING = "is_tracking"
    private const val KEY_LATEST_JSON = "latest_event_json"
    private const val MAX_HISTORY_ITEMS = 30

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private val _latestEvent = MutableStateFlow<ActivityUpdateEvent?>(null)
    val latestEvent: StateFlow<ActivityUpdateEvent?> = _latestEvent.asStateFlow()

    private val _history = MutableStateFlow<List<ActivityUpdateEvent>>(emptyList())
    val history: StateFlow<List<ActivityUpdateEvent>> = _history.asStateFlow()

    private var isInitialized = false

    fun initialize(context: Context) {
        if (isInitialized) return
        isInitialized = true

        val prefs = getPrefs(context)
        _isTracking.value = prefs.getBoolean(KEY_IS_TRACKING, false)

        val latestJsonStr = prefs.getString(KEY_LATEST_JSON, null)
        if (latestJsonStr != null) {
            val restoredEvent = deserializeEvent(latestJsonStr)
            if (restoredEvent != null) {
                _latestEvent.value = restoredEvent
                _history.value = listOf(restoredEvent)
            }
        }
    }

    fun setTracking(context: Context, tracking: Boolean) {
        _isTracking.value = tracking
        getPrefs(context).edit().putBoolean(KEY_IS_TRACKING, tracking).apply()
    }

    fun onNewActivityEvent(context: Context, event: ActivityUpdateEvent) {
        initialize(context)
        _latestEvent.value = event

        val currentList = _history.value.toMutableList()
        currentList.add(0, event)
        if (currentList.size > MAX_HISTORY_ITEMS) {
            currentList.removeAt(currentList.size - 1)
        }
        _history.value = currentList

        // Persist latest event
        val json = serializeEvent(event)
        getPrefs(context).edit().putString(KEY_LATEST_JSON, json.toString()).apply()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun serializeEvent(event: ActivityUpdateEvent): JSONObject {
        val root = JSONObject()
        root.put("timestamp", event.timestamp)

        val topObj = JSONObject()
        topObj.put("type", event.mostProbable.type)
        topObj.put("name", event.mostProbable.name)
        topObj.put("emoji", event.mostProbable.emoji)
        topObj.put("confidence", event.mostProbable.confidence)
        topObj.put("iconResId", event.mostProbable.iconResId)
        root.put("mostProbable", topObj)

        val allArray = JSONArray()
        for (item in event.allActivities) {
            val itemObj = JSONObject()
            itemObj.put("type", item.type)
            itemObj.put("name", item.name)
            itemObj.put("emoji", item.emoji)
            itemObj.put("confidence", item.confidence)
            itemObj.put("iconResId", item.iconResId)
            allArray.put(itemObj)
        }
        root.put("allActivities", allArray)
        return root
    }

    private fun deserializeEvent(jsonStr: String): ActivityUpdateEvent? {
        return try {
            val root = JSONObject(jsonStr)
            val timestamp = root.getLong("timestamp")

            val topObj = root.getJSONObject("mostProbable")
            val topActivity = ActivityInfo(
                type = topObj.getInt("type"),
                name = topObj.getString("name"),
                emoji = topObj.getString("emoji"),
                confidence = topObj.getInt("confidence"),
                iconResId = topObj.getInt("iconResId")
            )

            val allArray = root.getJSONArray("allActivities")
            val allList = mutableListOf<ActivityInfo>()
            for (i in 0 until allArray.length()) {
                val itemObj = allArray.getJSONObject(i)
                allList.add(
                    ActivityInfo(
                        type = itemObj.getInt("type"),
                        name = itemObj.getString("name"),
                        emoji = itemObj.getString("emoji"),
                        confidence = itemObj.getInt("confidence"),
                        iconResId = itemObj.getInt("iconResId")
                    )
                )
            }

            ActivityUpdateEvent(
                mostProbable = topActivity,
                allActivities = allList,
                timestamp = timestamp
            )
        } catch (e: Exception) {
            null
        }
    }
}
