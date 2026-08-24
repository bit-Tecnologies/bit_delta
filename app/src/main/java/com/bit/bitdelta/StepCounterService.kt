package com.bit.bitdelta

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StepCounterService : Service(), SensorEventListener {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var sensorManager: SensorManager
    private var tracker: StepCounterTracker? = null
    private var initialized = false
    private var sensorRegistered = false

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        createNotificationChannel()
        startForegroundCompat()

        serviceScope.launch {
            val savedSteps = StepCounterStorage.loadAndMigrate(dataStore)
            tracker = StepCounterTracker(savedSteps)
            registerStepSensor()
            initialized = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private suspend fun registerStepSensor() = withContext(Dispatchers.Main.immediate) {
        if (ContextCompat.checkSelfPermission(this@StepCounterService, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "ACTIVITY_RECOGNITION permission is not granted")
            stopSelf()
            return@withContext
        }

        val stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        val stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        sensorRegistered = when {
            stepCounter != null -> sensorManager.registerListener(this@StepCounterService, stepCounter, SensorManager.SENSOR_DELAY_NORMAL)
            stepDetector != null -> sensorManager.registerListener(this@StepCounterService, stepDetector, SensorManager.SENSOR_DELAY_FASTEST)
            else -> false
        }

        if (!sensorRegistered) {
            Log.e(TAG, "No usable step sensor was found")
            stopSelf()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!initialized || event == null) return

        val newSteps = when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> tracker?.onCounterValue(event.values[0].toLong())
            Sensor.TYPE_STEP_DETECTOR -> tracker?.onDetectedStep()
            else -> null
        } ?: return

        serviceScope.launch(Dispatchers.IO) {
            dataStore.edit { preferences ->
                val storedSteps = preferences[StepCounterStorage.stepsKey] ?: 0L
                if (newSteps > storedSteps) {
                    preferences[StepCounterStorage.stepsKey] = newSteps
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        if (sensorRegistered) {
            sensorManager.unregisterListener(this)
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Фоновый счётчик шагов", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun startForegroundCompat() {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("bit Delta")
            .setContentText("Подсчёт шагов продолжается в фоне")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private companion object {
        const val TAG = "StepCounterService"
        const val CHANNEL_ID = "step_tracking_channel"
        const val NOTIFICATION_ID = 2
    }
}
