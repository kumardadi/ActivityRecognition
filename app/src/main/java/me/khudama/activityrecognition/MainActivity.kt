package me.khudama.activityrecognition

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import me.khudama.activityrecognition.databinding.ActivityMainBinding
import me.khudama.activityrecognition.databinding.ItemActivityConfidenceBinding
import me.khudama.activityrecognition.databinding.ItemHistoryLogBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val activityRecognitionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions[Manifest.permission.ACTIVITY_RECOGNITION] ?: false
        } else {
            true
        }

        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
        } else {
            true
        }

        if (activityRecognitionGranted) {
            startTracking()
        } else {
            Toast.makeText(
                this,
                "Activity Recognition permission is required to detect activities.",
                Toast.LENGTH_LONG
            ).show()
        }

        if (!notificationGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(
                this,
                "Notification permission is recommended to receive 1-min activity notifications.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ActivityNotificationHelper.createNotificationChannel(this)
        ActivityRepository.initialize(this)

        setupListeners()
        observeState()
    }

    private fun setupListeners() {
        binding.btnToggleTracking.setOnClickListener {
            val isCurrentlyTracking = ActivityRepository.isTracking.value
            if (isCurrentlyTracking) {
                stopTracking()
            } else {
                checkPermissionsAndStartTracking()
            }
        }
    }

    private fun checkPermissionsAndStartTracking() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACTIVITY_RECOGNITION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isEmpty()) {
            startTracking()
        } else {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun startTracking() {
        ActivityRecognitionManager.requestActivityUpdates(
            context = this,
            onSuccess = {
                Toast.makeText(
                    this,
                    "Activity recognition tracking started (updates every 1 min)",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onFailure = { exception ->
                Toast.makeText(
                    this,
                    "Failed to start tracking: ${exception.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    private fun stopTracking() {
        ActivityRecognitionManager.removeActivityUpdates(
            context = this,
            onSuccess = {
                Toast.makeText(this, "Activity recognition tracking stopped", Toast.LENGTH_SHORT).show()
            },
            onFailure = { exception ->
                Toast.makeText(
                    this,
                    "Failed to stop tracking: ${exception.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    ActivityRepository.isTracking.collect { isTracking ->
                        updateTrackingUi(isTracking)
                    }
                }

                launch {
                    ActivityRepository.latestEvent.collect { event ->
                        updateLatestActivityUi(event)
                    }
                }

                launch {
                    ActivityRepository.history.collect { historyList ->
                        updateHistoryUi(historyList)
                    }
                }
            }
        }
    }

    private fun updateTrackingUi(isTracking: Boolean) {
        if (isTracking) {
            binding.tvTrackingStatusBadge.text = getString(R.string.activity_tracking_active)
            binding.tvTrackingStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_active))
            binding.tvTrackingStatusBadge.setBackgroundColor(
                ContextCompat.getColor(this, R.color.status_active_bg)
            )

            binding.btnToggleTracking.text = getString(R.string.stop_tracking)
            binding.btnToggleTracking.setIconResource(R.drawable.ic_stop)
            binding.btnToggleTracking.setBackgroundColor(ContextCompat.getColor(this, R.color.text_secondary))
        } else {
            binding.tvTrackingStatusBadge.text = getString(R.string.activity_tracking_inactive)
            binding.tvTrackingStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_inactive))
            binding.tvTrackingStatusBadge.setBackgroundColor(
                ContextCompat.getColor(this, R.color.status_inactive_bg)
            )

            binding.btnToggleTracking.text = getString(R.string.start_tracking)
            binding.btnToggleTracking.setIconResource(R.drawable.ic_play)
            binding.btnToggleTracking.setBackgroundColor(ContextCompat.getColor(this, R.color.primary))
        }
    }

    private fun updateLatestActivityUi(event: ActivityUpdateEvent?) {
        if (event == null) {
            binding.tvTopActivityName.text = "Waiting for updates…"
            binding.tvTopActivityEmoji.text = "❓"
            binding.tvTopActivityConfidence.text = "0%"
            binding.tvLastUpdatedTime.text = "No activity detected yet"
            binding.layoutActivityBreakdown.removeAllViews()
            binding.layoutActivityBreakdown.addView(binding.tvBreakdownEmpty)
            binding.tvBreakdownEmpty.visibility = View.VISIBLE
            return
        }

        val top = event.mostProbable
        binding.tvTopActivityEmoji.text = top.emoji
        binding.tvTopActivityName.text = top.name
        binding.tvTopActivityConfidence.text = "${top.confidence}%"
        binding.tvLastUpdatedTime.text = "Updated: ${event.formattedTime}"

        // Update Breakdown list
        binding.layoutActivityBreakdown.removeAllViews()
        binding.tvBreakdownEmpty.visibility = View.GONE

        val inflater = LayoutInflater.from(this)
        for (activity in event.allActivities) {
            val itemBinding = ItemActivityConfidenceBinding.inflate(
                inflater,
                binding.layoutActivityBreakdown,
                false
            )
            itemBinding.tvActivityEmoji.text = activity.emoji
            itemBinding.tvActivityName.text = activity.name
            itemBinding.tvConfidenceValue.text = "${activity.confidence}%"
            itemBinding.progressConfidence.progress = activity.confidence

            if (activity.confidence >= 70) {
                itemBinding.progressConfidence.setIndicatorColor(
                    ContextCompat.getColor(this, R.color.confidence_high)
                )
            } else if (activity.confidence >= 40) {
                itemBinding.progressConfidence.setIndicatorColor(
                    ContextCompat.getColor(this, R.color.confidence_medium)
                )
            } else {
                itemBinding.progressConfidence.setIndicatorColor(
                    ContextCompat.getColor(this, R.color.confidence_low)
                )
            }

            binding.layoutActivityBreakdown.addView(itemBinding.root)
        }
    }

    private fun updateHistoryUi(historyList: List<ActivityUpdateEvent>) {
        binding.layoutHistoryList.removeAllViews()

        if (historyList.isEmpty()) {
            binding.layoutHistoryList.addView(binding.tvHistoryEmpty)
            binding.tvHistoryEmpty.visibility = View.VISIBLE
            return
        }

        binding.tvHistoryEmpty.visibility = View.GONE
        val inflater = LayoutInflater.from(this)

        for (event in historyList.take(15)) {
            val itemBinding = ItemHistoryLogBinding.inflate(
                inflater,
                binding.layoutHistoryList,
                false
            )
            val top = event.mostProbable
            itemBinding.tvHistoryEmoji.text = top.emoji
            itemBinding.tvHistoryActivityName.text = top.name
            itemBinding.tvHistoryTimestamp.text = event.formattedTime
            itemBinding.tvHistoryConfidence.text = "${top.confidence}%"

            binding.layoutHistoryList.addView(itemBinding.root)
        }
    }
}
