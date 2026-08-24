package com.bit.bitdelta

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object ActivityNames {
    const val STILL = "Покой"
    const val WALKING = "Ходьба"
    const val RUNNING = "Бег"
    const val CYCLING = "Велосипед"
    const val IN_VEHICLE = "В машине"
    const val OTHER = "Активность"
}

object ActivityState {
    private val _currentActivity = MutableStateFlow(ActivityNames.STILL)
    val currentActivity: StateFlow<String> = _currentActivity

    fun updateActivity(activity: String) {
        _currentActivity.value = activity
    }
}
