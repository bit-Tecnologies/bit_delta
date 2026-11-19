package com.bit.bitdelta

import org.junit.Assert.assertEquals
import org.junit.Test

class StepCounterTrackerTest {
    @Test
    fun counterDeltaIsAddedToThePersistedTotal() {
        val tracker = StepCounterTracker(100L)

        assertEquals(100L, tracker.onCounterValue(5_000L))
        assertEquals(110L, tracker.onCounterValue(5_010L))
    }

    @Test
    fun counterResetKeepsThePersistedTotalAndContinuesCounting() {
        val tracker = StepCounterTracker(100L)

        tracker.onCounterValue(5_000L)
        assertEquals(100L, tracker.onCounterValue(0L))
        assertEquals(105L, tracker.onCounterValue(5L))
    }

    @Test
    fun stepsWalkedWhileServiceWasDeadAreCountedAfterRestart() {
        // Service was killed at sensor value 5_000, the hardware kept counting up to 5_250.
        val tracker = StepCounterTracker(100L, initialSensorValue = 5_000L, initialBootId = 7L)

        assertEquals(350L, tracker.onCounterValue(5_250L, bootId = 7L))
        assertEquals(5_250L, tracker.lastSensorValue)
    }

    @Test
    fun rebootIsDetectedEvenIfNewCounterExceedsTheOldValue() {
        val tracker = StepCounterTracker(100L, initialSensorValue = 500L, initialBootId = 7L)

        // New boot: the counter restarted and already walked past the old reading.
        assertEquals(1_100L, tracker.onCounterValue(1_000L, bootId = 8L))
        assertEquals(1_110L, tracker.onCounterValue(1_010L, bootId = 8L))
    }

    @Test
    fun firstEverReadingDoesNotCountUnseenHistory() {
        val tracker = StepCounterTracker(0L)

        assertEquals(0L, tracker.onCounterValue(12_345L))
    }

    @Test
    fun detectorIncrementsTheLongTotal() {
        val tracker = StepCounterTracker(16_777_216L)

        assertEquals(16_777_217L, tracker.onDetectedStep())
    }
}
