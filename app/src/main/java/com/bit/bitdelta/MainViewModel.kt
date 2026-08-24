package com.bit.bitdelta

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dataStore = application.dataStore

    private val ACTIVITY_RECOGNITION_ENABLED_KEY = booleanPreferencesKey("activity_recognition_enabled")

    var stepsCount by mutableLongStateOf(0L)
        private set

    var activityRecognitionEnabled by mutableStateOf(false)
        private set

    var currentActivity by mutableStateOf("Покой")
        private set

    init {
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            stepsCount = StepCounterStorage.loadAndMigrate(dataStore)
            activityRecognitionEnabled = prefs[ACTIVITY_RECOGNITION_ENABLED_KEY] ?: false

            dataStore.data
                .map { it[StepCounterStorage.stepsKey] ?: 0L }
                .distinctUntilChanged()
                .collect { newSteps ->
                    stepsCount = newSteps
                }
        }

        viewModelScope.launch {
            ActivityState.currentActivity.collect { activity ->
                currentActivity = activity
            }
        }
    }

    fun updateActivityRecognitionEnabled(enabled: Boolean) {
        activityRecognitionEnabled = enabled
        viewModelScope.launch {
            dataStore.edit {
                it[ACTIVITY_RECOGNITION_ENABLED_KEY] = enabled
            }
        }
    }
}
