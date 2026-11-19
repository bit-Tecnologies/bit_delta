package com.bit.bitdelta

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey

internal object StepCounterStorage {
    val stepsKey = longPreferencesKey("steps_count_long")
    val lastSensorKey = longPreferencesKey("last_sensor_value")
    val lastBootIdKey = longPreferencesKey("last_boot_id")
    private val legacyStepsKey = floatPreferencesKey("steps_count")

    suspend fun loadAndMigrate(dataStore: DataStore<Preferences>): Long {
        var steps = 0L
        dataStore.edit { preferences ->
            steps = preferences[stepsKey] ?: preferences[legacyStepsKey]
                ?.takeIf { it.isFinite() && it >= 0f }
                ?.toLong()
                ?: 0L

            if (preferences[stepsKey] == null) {
                preferences[stepsKey] = steps
            }
            preferences.remove(legacyStepsKey)
        }
        return steps
    }
}
