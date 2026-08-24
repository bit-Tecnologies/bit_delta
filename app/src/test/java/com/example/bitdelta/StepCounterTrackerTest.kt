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
    fun detectorIncrementsTheLongTotal() {
        val tracker = StepCounterTracker(16_777_216L)

        assertEquals(16_777_217L, tracker.onDetectedStep())
    }
}
