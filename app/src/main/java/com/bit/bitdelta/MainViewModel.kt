package com.bit.bitdelta

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dataStore = application.dataStore
    private val context = application.applicationContext

    private val STEPS_KEY = floatPreferencesKey("steps_count")
    private val NOTIFICATIONS_ENABLED_KEY = booleanPreferencesKey("notifications_enabled")
    private val ACTIVITY_RECOGNITION_ENABLED_KEY = booleanPreferencesKey("activity_recognition_enabled")
    private val NOTIFICATION_SETUP_DONE_KEY = booleanPreferencesKey("notification_setup_done")

    var stepsCount by mutableFloatStateOf(0f)
        private set

    var notificationsEnabled by mutableStateOf(false)
        private set

    var activityRecognitionEnabled by mutableStateOf(false)
        private set

    var showNotificationDialog by mutableStateOf(false)
        private set

    var currentActivity by mutableStateOf("Покой")
        private set

    var initialSteps = -1f
    var isLoaded = false
        private set

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val CHANNEL_ID = "steps_channel"
    private val NOTIFICATION_ID = 1

    init {
        createNotificationChannel()
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            stepsCount = prefs[STEPS_KEY] ?: 0f
            notificationsEnabled = prefs[NOTIFICATIONS_ENABLED_KEY] ?: false
            activityRecognitionEnabled = prefs[ACTIVITY_RECOGNITION_ENABLED_KEY] ?: false

            val setupDone = prefs[NOTIFICATION_SETUP_DONE_KEY] ?: false
            if (!setupDone) {
                showNotificationDialog = true
            }

            isLoaded = true
            if (notificationsEnabled) {
                updateNotification()
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Счетчик шагов"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun updateNotificationsEnabled(enabled: Boolean) {
        notificationsEnabled = enabled
        viewModelScope.launch {
            dataStore.edit {
                it[NOTIFICATIONS_ENABLED_KEY] = enabled
                it[NOTIFICATION_SETUP_DONE_KEY] = true
            }
        }
        showNotificationDialog = false
        if (enabled) {
            updateNotification()
        } else {
            notificationManager.cancel(NOTIFICATION_ID)
        }
    }

    private fun updateNotification() {
        if (!notificationsEnabled) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass) // Можно заменить на иконку приложения
            .setContentTitle("bit Delta: Активность")
            .setContentText("Сегодня пройдено: ${stepsCount.toInt()} шагов")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun updateSteps(newSteps: Float) {
        if (!isLoaded) return
        stepsCount = newSteps
        updateNotification()
        viewModelScope.launch {
            dataStore.edit { it[STEPS_KEY] = newSteps }
        }
        }

    fun incrementStep() {
        if (!isLoaded) return
        stepsCount += 1f
        updateNotification()
        viewModelScope.launch {
            dataStore.edit { it[STEPS_KEY] = stepsCount }
        }
    }
}
