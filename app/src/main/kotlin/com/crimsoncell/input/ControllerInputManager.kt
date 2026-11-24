package com.crimsoncell.input

import android.content.Context
import android.hardware.input.InputManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs

/**
 * First-class Android gamepad/controller & keyboard input manager.
 * Supports Xbox, DualSense/DualShock, Backbone, Razer Kishi, 8BitDo,
 * generic HID gamepads, hot-plug detection, analog triggers, and haptics.
 */
class ControllerInputManager(private val context: Context) : InputManager.InputDeviceListener {

    private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
    private val lock = Any()

    // Raw controller axes
    private var stickX = 0f
    private var stickY = 0f
    private var hatX = 0f
    private var hatY = 0f
    private var prevHatUp = false
    private var prevHatDown = false
    private var prevHatLeft = false
    private var prevHatRight = false
    private var prevStickUp = false
    private var prevStickDown = false
    private var prevL2 = false
    private var prevR2 = false

    // Keyboard directional state
    private var keyLeft = false
    private var keyRight = false
    private var keyUp = false
    private var keyDown = false

    // Pending state written by UI thread and consumed by game thread
    private val pending = InputState()
    private var activeGamepadDeviceId: Int = -1

    @Volatile
    var connectedControllerName: String? = null
        private set

    fun register() {
        inputManager.registerInputDeviceListener(this, null)
        refreshConnectedControllers()
    }

    fun unregister() {
        inputManager.unregisterInputDeviceListener(this)
    }

    fun refreshConnectedControllers() {
        var foundName: String? = null
        var foundId = -1
        for (id in inputManager.inputDeviceIds) {
            val dev = inputManager.getInputDevice(id) ?: continue
            if (isGamepad(dev)) {
                foundName = dev.name
                foundId = id
                break
            }
        }
        connectedControllerName = foundName
        activeGamepadDeviceId = foundId
        synchronized(lock) {
            pending.controllerConnected = foundName != null
            if (foundName != null) pending.usingController = true
        }
    }

    private fun isGamepad(device: InputDevice): Boolean {
        val sources = device.sources
        val hasGamepad = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
        val hasJoystick = (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        return (hasGamepad || hasJoystick) && !device.isVirtual
    }

    override fun onInputDeviceAdded(deviceId: Int) = refreshConnectedControllers()
    override fun onInputDeviceRemoved(deviceId: Int) = refreshConnectedControllers()
    override fun onInputDeviceChanged(deviceId: Int) = refreshConnectedControllers()

    fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.repeatCount > 0) return isHandledKey(keyCode)
        val fromGamepad = (event.source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (event.source and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD

        synchronized(lock) {
            if (fromGamepad) {
                pending.usingController = true
                pending.controllerConnected = true
                activeGamepadDeviceId = event.deviceId
            }

            when (keyCode) {
                // Movement keys / D-Pad
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> {
                    keyLeft = true
                    pending.menuLeftJustPressed = true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> {
                    keyRight = true
                    pending.menuRightJustPressed = true
                }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> {
                    keyUp = true
                    pending.menuUpJustPressed = true
                }
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> {
                    keyDown = true
                    pending.menuDownJustPressed = true
                }

                // Jump / Confirm (A / Cross / Space / K)
                KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_K -> {
                    pending.jumpHeld = true
                    pending.jumpJustPressed = true
                    pending.menuConfirmJustPressed = true
                }

                // Dodge Roll / Back (B / Circle / Shift / L)
                KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_L -> {
                    pending.rollJustPressed = true
                    pending.menuBackJustPressed = true
                }

                // Primary Weapon (X / Square / J)
                KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_J -> {
                    pending.weapon1JustPressed = true
                }

                // Secondary Weapon / Shield (Y / Triangle / U)
                KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_U -> {
                    pending.weapon2JustPressed = true
                    pending.weapon2Held = true
                }

                // Skill 1 (L2 / Q)
                KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_Q -> {
                    pending.skill1JustPressed = true
                }

                // Skill 2 (R2 / E)
                KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.KEYCODE_E -> {
                    pending.skill2JustPressed = true
                }

                // Health Flask (L1 / H)
                KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_H -> {
                    pending.flaskJustPressed = true
                }

                // Interact / Pick up / Door (R1 / F)
                KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_F -> {
                    pending.interactJustPressed = true
                }

                // Pause / Menu (Start / Escape)
                KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_P -> {
                    pending.pauseJustPressed = true
                }

                else -> return false
            }
        }
        return true
    }

    fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        synchronized(lock) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> keyLeft = false
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> keyRight = false
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> keyUp = false
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> keyDown = false
                KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_K -> pending.jumpHeld = false
                KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_U -> pending.weapon2Held = false
                else -> return isHandledKey(keyCode)
            }
        }
        return true
    }

    fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val isJoystick = (event.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        if (!isJoystick || event.action != MotionEvent.ACTION_MOVE) return false

        val rawX = event.getAxisValue(MotionEvent.AXIS_X)
        val rawY = event.getAxisValue(MotionEvent.AXIS_Y)
        val (dx, dy) = InputState.applyRadialDeadzone(rawX, rawY)

        val hx = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hy = event.getAxisValue(MotionEvent.AXIS_HAT_Y)

        val lTrigger = maxOf(
            event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_BRAKE)
        )
        val rTrigger = maxOf(
            event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_GAS)
        )

        synchronized(lock) {
            if (abs(dx) > 0.05f || abs(dy) > 0.05f || abs(hx) > 0.5f || abs(hy) > 0.5f || lTrigger > 0.2f || rTrigger > 0.2f) {
                pending.usingController = true
                pending.controllerConnected = true
                activeGamepadDeviceId = event.deviceId
            }

            stickX = dx
            stickY = dy
            hatX = hx
            hatY = hy

            // Edge triggers for menu navigation via Hat/Stick
            val hatUp = hy < -0.5f
            val hatDown = hy > 0.5f
            val hatLeft = hx < -0.5f
            val hatRight = hx > 0.5f
            if (hatUp && !prevHatUp) pending.menuUpJustPressed = true
            if (hatDown && !prevHatDown) pending.menuDownJustPressed = true
            if (hatLeft && !prevHatLeft) pending.menuLeftJustPressed = true
            if (hatRight && !prevHatRight) pending.menuRightJustPressed = true
            prevHatUp = hatUp; prevHatDown = hatDown; prevHatLeft = hatLeft; prevHatRight = hatRight

            val sUp = dy < -0.6f
            val sDown = dy > 0.6f
            if (sUp && !prevStickUp) pending.menuUpJustPressed = true
            if (sDown && !prevStickDown) pending.menuDownJustPressed = true
            prevStickUp = sUp; prevStickDown = sDown

            // Analog triggers L2 / R2 -> Skill 1 / Skill 2
            val l2 = lTrigger >= InputState.TRIGGER_THRESHOLD
            val r2 = rTrigger >= InputState.TRIGGER_THRESHOLD
            if (l2 && !prevL2) pending.skill1JustPressed = true
            if (r2 && !prevR2) pending.skill2JustPressed = true
            prevL2 = l2
            prevR2 = r2
        }
        return true
    }

    /**
     * Copies current hardware controller/keyboard state + optional touch overlay state
     * into [target] for the game loop tick, and clears one-shot edges.
     */
    fun pollInto(target: InputState, touch: TouchOverlay) {
        synchronized(lock) {
            val keyX = (if (keyRight) 1f else 0f) - (if (keyLeft) 1f else 0f)
            val keyY = (if (keyDown) 1f else 0f) - (if (keyUp) 1f else 0f)

            val padX = InputState.unifyAxis(stickX, if (abs(hatX) > 0.5f) hatX else keyX)
            val padY = InputState.unifyAxis(stickY, if (abs(hatY) > 0.5f) hatY else keyY)

            target.moveX = if (abs(padX) > 0.05f) padX else touch.moveX
            target.moveY = if (abs(padY) > 0.05f) padY else touch.moveY

            target.jumpHeld = pending.jumpHeld || touch.jumpHeld
            target.weapon2Held = pending.weapon2Held || touch.weapon2Held

            target.jumpJustPressed = pending.jumpJustPressed || touch.consumeJump()
            target.rollJustPressed = pending.rollJustPressed || touch.consumeRoll()
            target.weapon1JustPressed = pending.weapon1JustPressed || touch.consumeWeapon1()
            target.weapon2JustPressed = pending.weapon2JustPressed || touch.consumeWeapon2()
            target.skill1JustPressed = pending.skill1JustPressed || touch.consumeSkill1()
            target.skill2JustPressed = pending.skill2JustPressed || touch.consumeSkill2()
            target.flaskJustPressed = pending.flaskJustPressed || touch.consumeFlask()
            target.interactJustPressed = pending.interactJustPressed || touch.consumeInteract()
            target.pauseJustPressed = pending.pauseJustPressed || touch.consumePause()

            target.menuUpJustPressed = pending.menuUpJustPressed || touch.consumeMenuUp()
            target.menuDownJustPressed = pending.menuDownJustPressed || touch.consumeMenuDown()
            target.menuLeftJustPressed = pending.menuLeftJustPressed
            target.menuRightJustPressed = pending.menuRightJustPressed
            target.menuConfirmJustPressed = pending.menuConfirmJustPressed || target.jumpJustPressed
            target.menuBackJustPressed = pending.menuBackJustPressed || target.rollJustPressed

            target.controllerConnected = pending.controllerConnected
            target.usingController = pending.usingController

            pending.clearEdgeTriggers()
        }
    }

    fun triggerHaptic(durationMs: Long, amplitude: Int) {
        try {
            val effect = VibrationEffect.createOneShot(durationMs.coerceAtLeast(10L), amplitude.coerceIn(1, 255))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Prefer controller rumble when a gamepad is active!
                if (activeGamepadDeviceId >= 0) {
                    val dev = inputManager.getInputDevice(activeGamepadDeviceId)
                    val vm = dev?.vibratorManager
                    if (vm != null && vm.vibratorIds.isNotEmpty()) {
                        vm.defaultVibrator.vibrate(effect)
                        return
                    }
                }
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                v?.vibrate(effect)
            }
        } catch (_: Exception) {
            // Safe no-op on devices without vibrator
        }
    }

    private fun isHandledKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B,
        KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
        KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1,
        KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2,
        KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT -> true
        else -> false
    }
}
