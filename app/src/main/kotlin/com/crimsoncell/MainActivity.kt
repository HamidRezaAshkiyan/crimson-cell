package com.crimsoncell

import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.crimsoncell.audio.ChiptuneAudioEngine
import com.crimsoncell.input.ControllerInputManager

class MainActivity : AppCompatActivity() {

    private lateinit var controllerInput: ControllerInputManager
    private lateinit var audioEngine: ChiptuneAudioEngine
    private lateinit var gameView: GameSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        controllerInput = ControllerInputManager(this)
        audioEngine = ChiptuneAudioEngine()
        gameView = GameSurfaceView(this, controllerInput, audioEngine)
        setContentView(gameView)
        enableImmersiveFullscreen()
    }

    override fun onResume() {
        super.onResume()
        enableImmersiveFullscreen()
        controllerInput.register()
        gameView.requestFocus()
        gameView.startLoop()
    }

    override fun onPause() {
        super.onPause()
        controllerInput.unregister()
        gameView.stopLoop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableImmersiveFullscreen()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (controllerInput.onKeyDown(keyCode, event)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (controllerInput.onKeyUp(keyCode, event)) return true
        return super.onKeyUp(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (controllerInput.onGenericMotionEvent(event)) return true
        return super.onGenericMotionEvent(event)
    }

    private fun enableImmersiveFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { ctrl ->
                ctrl.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                ctrl.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )
        }
    }
}
