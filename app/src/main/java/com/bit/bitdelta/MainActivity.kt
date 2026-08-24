package com.bit.bitdelta

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HomeMax
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.preferencesDataStore
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bit.bitdelta.ui.*
import com.bit.bitdelta.ui.theme.*
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient

val Context.dataStore by preferencesDataStore(name = "settings")

class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var stepCounterSensor: Sensor? = null
    private var stepDetectorSensor: Sensor? = null
    
    private val viewModel: MainViewModel by viewModels()
    private var isSensorAvailable by mutableStateOf(true)

    private lateinit var activityRecognitionClient: ActivityRecognitionClient
    private val detectionIntervalMillis = 3000L // 3 секунды

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepDetectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        
        if (stepCounterSensor == null && stepDetectorSensor == null) {
            isSensorAvailable = false
        }

        activityRecognitionClient = ActivityRecognition.getClient(this)

        setContent {
            BitDeltaTheme {
                MainNavigation(
                    viewModel = viewModel,
                    isSensorAvailable = isSensorAvailable,
                    onStartActivityRecognition = { startActivityRecognition() },
                    onStopActivityRecognition = { stopActivityRecognition() }
                )
            }
        }
    }

    private fun startActivityRecognition() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val intent = Intent(this, ActivityRecognitionReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        activityRecognitionClient.requestActivityUpdates(detectionIntervalMillis, pendingIntent)
            .addOnSuccessListener {
                Log.d("ActivityRecognition", "Successfully requested activity updates")
            }
            .addOnFailureListener { e ->
                Log.e("ActivityRecognition", "Failed to request activity updates", e)
            }
    }

    private fun stopActivityRecognition() {
        val intent = Intent(this, ActivityRecognitionReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        activityRecognitionClient.removeActivityUpdates(pendingIntent)
            .addOnSuccessListener {
                Log.d("ActivityRecognition", "Successfully removed activity updates")
            }
            .addOnFailureListener { e ->
                Log.e("ActivityRecognition", "Failed to remove activity updates", e)
            }
    }

    override fun onResume() {
        super.onResume()
        // Используем step counter, если он есть; step detector — как запасной
        if (stepCounterSensor != null) {
            sensorManager.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_NORMAL)
        } else if (stepDetectorSensor != null) {
            sensorManager.registerListener(this, stepDetectorSensor, SensorManager.SENSOR_DELAY_FASTEST)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !viewModel.isLoaded) return
        
        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> {
                val sensorValue = event.values[0]
                if (viewModel.initialSteps == -1f) {
                    viewModel.initialSteps = sensorValue - viewModel.stepsCount
                }
                
                val calculatedSteps = sensorValue - viewModel.initialSteps
                if (calculatedSteps > viewModel.stepsCount) {
                    viewModel.updateSteps(calculatedSteps)
                }
            }
            Sensor.TYPE_STEP_DETECTOR -> {
                viewModel.incrementStep()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

sealed class Screen(val route: String, val icon: ImageVector, val label: String) {
    data object Home : Screen("home", Icons.Default.HomeMax, "Главная")
    data object Achievements : Screen("achievements", Icons.Default.WorkspacePremium, "Награды")
    data object Settings : Screen("settings", Icons.Default.Settings, "Опции")
}

@Composable
fun MainNavigation(
    viewModel: MainViewModel,
    isSensorAvailable: Boolean,
    onStartActivityRecognition: () -> Unit,
    onStopActivityRecognition: () -> Unit
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val steps = viewModel.stepsCount.toInt()
    val activityRecognitionEnabled = viewModel.activityRecognitionEnabled

    var hasStepPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val stepLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        hasStepPermission = isGranted
        if (isGranted) {
            onStartActivityRecognition()
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        hasNotificationPermission = isGranted
        if (isGranted) {
            viewModel.updateNotificationsEnabled(true)
        } else {
            viewModel.updateNotificationsEnabled(false)
        }
    }

    LaunchedEffect(hasStepPermission, activityRecognitionEnabled) {
        if (activityRecognitionEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasStepPermission) {
                stepLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            } else if (hasStepPermission) {
                onStartActivityRecognition()
            }
        } else {
            onStopActivityRecognition()
        }
    }

    if (viewModel.showNotificationDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.updateNotificationsEnabled(false) },
            title = { Text("Уведомления о шагах") },
            text = { Text("Хотите видеть количество пройденных шагов в панели уведомлений?") },
            confirmButton = {
                TextButton(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.updateNotificationsEnabled(true)
                    }
                }) {
                    Text("ДА")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.updateNotificationsEnabled(false) }) {
                    Text("НЕТ")
                }
            },
            containerColor = DeepBlue,
            titleContentColor = SkyBlue,
            textContentColor = TextWhite
        )
    }
    
    val xpPerLevel = 3000
    val userLevel = (steps / xpPerLevel) + 1
    val xpProgress = (steps % xpPerLevel).toFloat() / xpPerLevel

    Row(modifier = Modifier.fillMaxSize().background(DeepBlue)) {
        if (isTablet) NavigationSideBar(navController)
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = { if (!isTablet) BottomNavigationBar(navController) },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                LiquidBackground()
                NavHost(navController, Screen.Home.route) {
                    composable(Screen.Home.route) { 
                        HomeScreen(
                            steps = steps, 
                            level = userLevel, 
                            xpProgress = xpProgress, 
                            isAvailable = isSensorAvailable,
                            hasPermission = hasStepPermission,
                            currentActivity = viewModel.currentActivity,
                            onGrantPermission = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    stepLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                                }
                            }
                        ) 
                    }
                    composable(Screen.Achievements.route) { AchievementsScreen(steps) }
                    composable(Screen.Settings.route) {
                        SettingsScreen(
                            notificationsEnabled = viewModel.notificationsEnabled,
                            onToggleNotifications = { enabled ->
                                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    viewModel.updateNotificationsEnabled(enabled)
                                }
                            },
                            activityRecognitionEnabled = activityRecognitionEnabled,
                            onToggleActivityRecognition = { enabled ->
                                viewModel.updateActivityRecognitionEnabled(enabled)
                            }
                        )
                    }
                }
            }
        }
    }
}
