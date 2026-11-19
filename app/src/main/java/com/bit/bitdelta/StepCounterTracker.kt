package com.bit.bitdelta

/**
 * Keeps the app's total step count independent from the sensor's boot-relative value.
 *
 * The last seen sensor value and boot id are persisted by the caller and passed back in, so steps
 * the hardware counted while the service was not running are still added after a restart.
 */
internal class StepCounterTracker(
    initialSteps: Long,
    initialSensorValue: Long? = null,
    initialBootId: Long = 0L
) {
    var totalSteps: Long = initialSteps.coerceAtLeast(0L)
        private set

    var lastSensorValue: Long? = initialSensorValue
        private set

    var lastBootId: Long = initialBootId
        private set

    /**
     * @param bootId changes after every device reboot; the hardware counter restarts from zero then.
     */
    fun onCounterValue(sensorValue: Long, bootId: Long = lastBootId): Long {
        val last = lastSensorValue
        val reset = last != null && (bootId != lastBootId || sensorValue < last)

        when {
            // First reading ever: there is no reference point, so don't count history we never saw.
            last == null -> Unit
            // Counter restarted from zero: everything it shows now was walked since the reset.
            reset -> totalSteps += sensorValue
            else -> totalSteps += sensorValue - last
        }

        lastSensorValue = sensorValue
        lastBootId = bootId
        return totalSteps
    }

    fun onDetectedStep(): Long {
        if (totalSteps < Long.MAX_VALUE) {
            totalSteps += 1L
        }
        return totalSteps
    }
}
