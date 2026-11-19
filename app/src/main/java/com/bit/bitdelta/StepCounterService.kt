package com.bit.bitdelta

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.MutablePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class StepCounterService : Service(), SensorEventListener {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var sensorManager: SensorManager
    private var tracker: StepCounterTracker? = null
    private var initialized = false
    private var sensorRegistered = false

    /** Snapshots of the tracker state; conflated so a burst of events costs a single write. */
    private val pendingWrites = Channel<Snapshot>(Channel.CONFLATED)

    private class Snapshot(val steps: Long, val sensorValue: Long?, val bootId: Long)

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        createNotificationChannel()

        // A sticky restart from the background can be refused on Android 12+, and a health
        // service without the runtime permission is refused on Android 14+.
        try {
            migrateForegroundNotification()
        } catch (e: Exception) {
            Log.w(TAG, "Foreground start was refused", e)
            stopSelf()
            return
        }

        serviceScope.launch {
            val savedSteps = StepCounterStorage.loadAndMigrate(dataStore)
            val prefs = dataStore.data.first()
            tracker = StepCounterTracker(
                initialSteps = savedSteps,
                initialSensorValue = prefs[StepCounterStorage.lastSensorKey],
                initialBootId = prefs[StepCounterStorage.lastBootIdKey] ?: 0L
            )
            updateForegroundNotification(savedSteps)
            launchWriter()
            registerStepSensor()
            initialized = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun launchWriter() {
        serviceScope.launch(Dispatchers.IO) {
            for (snapshot in pendingWrites) {
                dataStore.edit { preferences ->
                    val storedSteps = preferences[StepCounterStorage.stepsKey] ?: 0L
                    if (snapshot.steps > storedSteps) {
                        preferences[StepCounterStorage.stepsKey] = snapshot.steps
                    }
                    saveSensorState(preferences, snapshot)
                }
                updateForegroundNotification(snapshot.steps)
            }
        }
    }

    private fun saveSensorState(preferences: MutablePreferences,snapshot: Snapshot) {
        snapshot.sensorValue?.let {
            preferences[StepCounterStorage.lastSensorKey] = it
            preferences[StepCounterStorage.lastBootIdKey] = snapshot.bootId
        }
    }

    private fun registerStepSensor() {
        if (!hasActivityPermission(this)) {
            Log.w(TAG, "ACTIVITY_RECOGNITION permission is not granted")
            stopSelf()
            return
        }

        val stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        val stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        sensorRegistered = when {
            stepCounter != null -> sensorManager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_NORMAL)
            stepDetector != null -> sensorManager.registerListener(this, stepDetector, SensorManager.SENSOR_DELAY_FASTEST)
            else -> false
        }

        if (!sensorRegistered) {
            Log.e(TAG, "No usable step sensor was found")
            stopSelf()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!initialized || event == null) return
        val tracker = tracker ?: return

        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> {
                val steps = tracker.onCounterValue(event.values[0].toLong(), currentBootId())
                pendingWrites.trySend(Snapshot(steps, tracker.lastSensorValue, tracker.lastBootId))
            }
            Sensor.TYPE_STEP_DETECTOR -> {
                val steps = tracker.onDetectedStep()
                pendingWrites.trySend(Snapshot(steps, null, tracker.lastBootId))
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        if (sensorRegistered) {
            sensorManager.unregisterListener(this)
        }
        pendingWrites.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun currentBootId(): Long =
        Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, 0).toLong()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Фоновый счётчик шагов", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun startForegroundCompat() {
        startForegroundCompat(NOTIFICATION_ID)
    }

    private fun startForegroundCompat(notificationId: Int) {
        val notification = buildNotification(null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                notificationId,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            )
        } else {
            startForeground(notificationId, notification)
        }
    }

    private fun migrateForegroundNotification() {
        // Claim and remove the old foreground ID before publishing the current one.
        startForegroundCompat(LEGACY_FOREGROUND_ID)
        stopForeground(true)
        startForegroundCompat()
    }

    private fun updateForegroundNotification(steps: Long) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(steps))
    }

    private fun buildNotification(steps: Long?): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("bit Delta")
            .setContentText(steps?.let { "Всего пройдено: $it шагов" } ?: "Подсчёт шагов продолжается в фоне")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    companion object {
        private const val TAG = "StepCounterService"
        private const val CHANNEL_ID = "step_tracking_channel"
        private const val NOTIFICATION_ID = 1
        private const val LEGACY_FOREGROUND_ID = 2

        /** ACTIVITY_RECOGNITION is a runtime permission only since Android 10. */
        fun hasActivityPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
                PackageManager.PERMISSION_GRANTED

        /** Starts the service if allowed; returns false instead of crashing when the system refuses. */
        fun start(context: Context): Boolean {
            if (!hasActivityPermission(context)) return false
            return try {
                ContextCompat.startForegroundService(context, Intent(context, StepCounterService::class.java))
                true
            } catch (e: Exception) {
                Log.w(TAG, "Could not start the step service", e)
                false
            }
        }
    }
}
