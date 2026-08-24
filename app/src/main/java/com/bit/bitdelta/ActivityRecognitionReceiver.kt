package com.bit.bitdelta

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity

class ActivityRecognitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (ActivityRecognitionResult.hasResult(intent)) {
            val result = ActivityRecognitionResult.extractResult(intent)
            result?.let { handleDetectedActivities(it.probableActivities) }
        }
    }

    private fun handleDetectedActivities(probableActivities: List<DetectedActivity>) {
        val mostProbable = probableActivities.maxByOrNull { it.confidence } ?: return

        if (mostProbable.confidence < 50) return

        val activityName = when (mostProbable.type) {
            DetectedActivity.WALKING -> ActivityNames.WALKING
            DetectedActivity.RUNNING -> ActivityNames.RUNNING
            DetectedActivity.ON_BICYCLE -> ActivityNames.CYCLING
            DetectedActivity.STILL -> ActivityNames.STILL
            DetectedActivity.IN_VEHICLE -> ActivityNames.IN_VEHICLE
            else -> ActivityNames.OTHER
        }

        ActivityState.updateActivity(activityName)
    }
}
