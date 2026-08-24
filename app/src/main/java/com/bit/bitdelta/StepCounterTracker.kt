package com.bit.bitdelta

/**
 * Keeps the app's total step count independent from the sensor's boot-relative value.
 */
internal class StepCounterTracker(initialSteps: Long) {
    var totalSteps: Long = initialSteps.coerceAtLeast(0L)
        private set

    private var sensorBaseline: Long? = null
    private var lastSensorValue: Long? = null

    fun onCounterValue(sensorValue: Long): Long {
        val sensorReset = lastSensorValue != null && sensorValue < lastSensorValue!!
        if (sensorBaseline == null || sensorReset) {
            sensorBaseline = sensorValue - totalSteps
        }

        val calculatedSteps = sensorValue - sensorBaseline!!
        if (calculatedSteps > totalSteps) {
            totalSteps = calculatedSteps
        }
        lastSensorValue = sensorValue
        return totalSteps
    }

    fun onDetectedStep(): Long {
        if (totalSteps < Long.MAX_VALUE) {
            totalSteps += 1L
        }
        return totalSteps
    }
}
