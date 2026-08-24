package com.bit.bitdelta

import org.junit.Assert.assertEquals
import org.junit.Test

class StepCounterFloatPrecisionTest {
    @Test
    fun oneStepMustRemainRepresentableAtTheFloatPrecisionLimit() {
        val tracker = StepCounterTracker(16_777_216L)

        val steps = tracker.onDetectedStep()

        assertEquals(16_777_217L, steps)
    }
}
