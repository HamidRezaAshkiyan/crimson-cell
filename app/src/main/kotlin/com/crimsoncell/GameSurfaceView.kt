package com.crimsoncell

import android.content.Context
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.crimsoncell.audio.ChiptuneAudioEngine
import com.crimsoncell.engine.EnginePlatformHooks
import com.crimsoncell.engine.GameEngine
import com.crimsoncell.input.ControllerInputManager
import com.crimsoncell.input.InputState
import com.crimsoncell.input.TouchOverlay
import com.crimsoncell.progression.SharedPrefsProgressionRepository
import com.crimsoncell.render.PixelRenderer

class GameSurfaceView(
    context: Context,
    private val controllerInput: ControllerInputManager,
    private val audioEngine: ChiptuneAudioEngine
) : SurfaceView(context), SurfaceHolder.Callback {

    private val touchOverlay = TouchOverlay()
    private val renderer = PixelRenderer()
    private val frameInput = InputState()

    private val platformHooks = object : EnginePlatformHooks {
        override fun playSfx(name: String) = audioEngine.playSfx(name)
        override fun setMusicBiome(biomeIndex: Int, muted: Boolean) = audioEngine.setBiome(biomeIndex, muted)
        override fun triggerHaptic(durationMs: Long, amplitude: Int) = controllerInput.triggerHaptic(durationMs, amplitude)
    }

    val engine: GameEngine = GameEngine(
        progressionRepo = SharedPrefsProgressionRepository(context),
        hooks = platformHooks
    )

    @Volatile private var running = false
    private var gameThread: Thread? = null
    private var surfaceWidth = 1280
    private var surfaceHeight = 720

    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
        keepScreenOn = true
    }

    private fun shouldShowTouchControls(): Boolean {
        return engine.showTouchControlsOverride ?: !frameInput.usingController
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val visible = shouldShowTouchControls()
        return touchOverlay.onTouchEvent(event, visible) || super.onTouchEvent(event)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        startLoop()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        surfaceWidth = width.coerceAtLeast(1)
        surfaceHeight = height.coerceAtLeast(1)
        touchOverlay.layout(surfaceWidth, surfaceHeight)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopLoop()
    }

    fun startLoop() {
        if (running) return
        running = true
        audioEngine.start()
        audioEngine.setBiome(engine.currentBiomeIndex, engine.progression.audioMuted)
        gameThread = Thread(::runLoop, "CrimsonCellLoop").apply { start() }
    }

    fun stopLoop() {
        running = false
        try {
            gameThread?.join(400)
        } catch (_: InterruptedException) {
        }
        gameThread = null
        audioEngine.stop()
    }

    private fun runLoop() {
        var lastTimeNs = System.nanoTime()
        var accumulator = 0f
        val fixedDt = GameEngine.FIXED_DT

        while (running) {
            val nowNs = System.nanoTime()
            val frameDt = ((nowNs - lastTimeNs) / 1_000_000_000f).coerceIn(0f, 0.05f)
            lastTimeNs = nowNs
            accumulator += frameDt

            // Fixed 60 Hz physics/combat simulation
            var steps = 0
            while (accumulator >= fixedDt && steps < 4) {
                controllerInput.pollInto(frameInput, touchOverlay)
                engine.step(fixedDt, frameInput)
                accumulator -= fixedDt
                steps++
            }

            // Render frame
            val canvas = try {
                holder.lockCanvas()
            } catch (_: Exception) {
                null
            }
            if (canvas != null) {
                try {
                    renderer.render(canvas, surfaceWidth, surfaceHeight, engine, frameInput)
                    if (shouldShowTouchControls()) {
                        touchOverlay.draw(canvas)
                    }
                } finally {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (_: Exception) {
                    }
                }
            }

            // Light frame pacing sleep to keep battery & thermals cool
            val elapsedMs = (System.nanoTime() - nowNs) / 1_000_000L
            val sleepMs = (16L - elapsedMs).coerceAtLeast(1L)
            try {
                Thread.sleep(sleepMs)
            } catch (_: InterruptedException) {
            }
        }
    }
}
