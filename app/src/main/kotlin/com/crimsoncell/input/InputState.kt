package com.crimsoncell.input

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Pure Kotlin snapshot of player input for a single fixed-timestep tick,
 * including responsive Dead Cells-style input buffers.
 */
data class InputState(
    var moveX: Float = 0f,
    var moveY: Float = 0f,
    var jumpHeld: Boolean = false,
    var jumpJustPressed: Boolean = false,
    var rollJustPressed: Boolean = false,
    var weapon1JustPressed: Boolean = false,
    var weapon2JustPressed: Boolean = false,
    var weapon2Held: Boolean = false,
    var skill1JustPressed: Boolean = false,
    var skill2JustPressed: Boolean = false,
    var flaskJustPressed: Boolean = false,
    var interactJustPressed: Boolean = false,
    var pauseJustPressed: Boolean = false,
    var menuUpJustPressed: Boolean = false,
    var menuDownJustPressed: Boolean = false,
    var menuLeftJustPressed: Boolean = false,
    var menuRightJustPressed: Boolean = false,
    var menuConfirmJustPressed: Boolean = false,
    var menuBackJustPressed: Boolean = false,
    var controllerConnected: Boolean = false,
    var usingController: Boolean = false
) {
    // Input buffers (seconds remaining)
    var jumpBufferTimer: Float = 0f
    var rollBufferTimer: Float = 0f
    var weapon1BufferTimer: Float = 0f
    var weapon2BufferTimer: Float = 0f

    fun updateBuffers(dt: Float) {
        if (jumpJustPressed) jumpBufferTimer = JUMP_BUFFER_SEC
        else jumpBufferTimer = (jumpBufferTimer - dt).coerceAtLeast(0f)

        if (rollJustPressed) rollBufferTimer = ACTION_BUFFER_SEC
        else rollBufferTimer = (rollBufferTimer - dt).coerceAtLeast(0f)

        if (weapon1JustPressed) weapon1BufferTimer = ACTION_BUFFER_SEC
        else weapon1BufferTimer = (weapon1BufferTimer - dt).coerceAtLeast(0f)

        if (weapon2JustPressed) weapon2BufferTimer = ACTION_BUFFER_SEC
        else weapon2BufferTimer = (weapon2BufferTimer - dt).coerceAtLeast(0f)
    }

    fun consumeJumpBuffer(): Boolean {
        if (jumpBufferTimer > 0f) {
            jumpBufferTimer = 0f
            return true
        }
        return false
    }

    fun consumeRollBuffer(): Boolean {
        if (rollBufferTimer > 0f) {
            rollBufferTimer = 0f
            return true
        }
        return false
    }

    fun consumeWeapon1Buffer(): Boolean {
        if (weapon1BufferTimer > 0f) {
            weapon1BufferTimer = 0f
            return true
        }
        return false
    }

    fun consumeWeapon2Buffer(): Boolean {
        if (weapon2BufferTimer > 0f) {
            weapon2BufferTimer = 0f
            return true
        }
        return false
    }

    fun clearEdgeTriggers() {
        jumpJustPressed = false
        rollJustPressed = false
        weapon1JustPressed = false
        weapon2JustPressed = false
        skill1JustPressed = false
        skill2JustPressed = false
        flaskJustPressed = false
        interactJustPressed = false
        pauseJustPressed = false
        menuUpJustPressed = false
        menuDownJustPressed = false
        menuLeftJustPressed = false
        menuRightJustPressed = false
        menuConfirmJustPressed = false
        menuBackJustPressed = false
    }

    companion object {
        const val DEADZONE = 0.20f
        const val TRIGGER_THRESHOLD = 0.45f
        const val JUMP_BUFFER_SEC = 0.12f
        const val ACTION_BUFFER_SEC = 0.10f

        /**
         * Applies radial deadzone and rescales [DEADZONE..1.0] smoothly to [0..1].
         */
        fun applyRadialDeadzone(rawX: Float, rawY: Float, deadzone: Float = DEADZONE): Pair<Float, Float> {
            val mag = hypot(rawX.toDouble(), rawY.toDouble()).toFloat()
            if (mag < deadzone) return 0f to 0f
            val clamped = mag.coerceAtMost(1f)
            val scaled = (clamped - deadzone) / (1f - deadzone)
            return (rawX / mag) * scaled to (rawY / mag) * scaled
        }

        fun unifyAxis(stick: Float, dpad: Float): Float {
            return if (abs(dpad) > 0.5f) dpad.coerceIn(-1f, 1f) else stick.coerceIn(-1f, 1f)
        }
    }
}
