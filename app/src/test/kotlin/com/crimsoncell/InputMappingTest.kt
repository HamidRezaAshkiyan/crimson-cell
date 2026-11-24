package com.crimsoncell

import com.crimsoncell.input.InputState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputMappingTest {

    @Test
    fun radialDeadzoneFiltersStickDriftAndScalesSmoothly() {
        val (zx, zy) = InputState.applyRadialDeadzone(0.10f, -0.10f)
        assertEquals(0f, zx, 0.001f)
        assertEquals(0f, zy, 0.001f)

        val (fx, fy) = InputState.applyRadialDeadzone(1.0f, 0f)
        assertEquals(1f, fx, 0.001f)
        assertEquals(0f, fy, 0.001f)
    }

    @Test
    fun dpadTakesPriorityOverAnalogStick() {
        assertEquals(-1f, InputState.unifyAxis(0.4f, -1f), 0.001f)
        assertEquals(0.4f, InputState.unifyAxis(0.4f, 0f), 0.001f)
    }

    @Test
    fun jumpBufferPersistsWithinWindowAndConsumesOnce() {
        val input = InputState(jumpJustPressed = true)
        input.updateBuffers(0.016f)
        input.clearEdgeTriggers()
        input.updateBuffers(0.05f) // still inside 0.12s window
        assertTrue(input.consumeJumpBuffer())
        assertFalse("Consumed buffer should not fire twice", input.consumeJumpBuffer())
    }
}
