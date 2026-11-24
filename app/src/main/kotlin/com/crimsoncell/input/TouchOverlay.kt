package com.crimsoncell.input

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import kotlin.math.hypot

/**
 * Clean translucent retro touch controls shown automatically when no hardware
 * controller is active (or toggled manually from the Pause menu).
 */
class TouchOverlay {

    private data class TouchButton(
        val id: String,
        val label: String,
        val color: Int,
        var cx: Float = 0f,
        var cy: Float = 0f,
        var radius: Float = 40f,
        var pressed: Boolean = false
    )

    private val buttons = listOf(
        TouchButton("LEFT", "<", 0xFF94A3B8.toInt()),
        TouchButton("RIGHT", ">", 0xFF94A3B8.toInt()),
        TouchButton("UP", "^", 0xFF94A3B8.toInt()),
        TouchButton("DOWN", "v", 0xFF94A3B8.toInt()),
        TouchButton("JUMP", "A", 0xFF4ADE80.toInt()),
        TouchButton("ROLL", "B", 0xFF38BDF8.toInt()),
        TouchButton("W1", "X", 0xFFE63946.toInt()),
        TouchButton("W2", "Y", 0xFFFACC15.toInt()),
        TouchButton("S1", "L2", 0xFFA855F7.toInt()),
        TouchButton("S2", "R2", 0xFFF97316.toInt()),
        TouchButton("FLASK", "HP", 0xFF2EC4B6.toInt()),
        TouchButton("USE", "USE", 0xFFE2E8F0.toInt()),
        TouchButton("PAUSE", "II", 0xFFCBD5E1.toInt())
    )

    private val byId = buttons.associateBy { it.id }

    @Volatile var moveX: Float = 0f
    @Volatile var moveY: Float = 0f
    @Volatile var jumpHeld: Boolean = false
    @Volatile var weapon2Held: Boolean = false

    private var jumpEdge = false
    private var rollEdge = false
    private var w1Edge = false
    private var w2Edge = false
    private var s1Edge = false
    private var s2Edge = false
    private var flaskEdge = false
    private var interactEdge = false
    private var pauseEdge = false
    private var menuUpEdge = false
    private var menuDownEdge = false

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    fun layout(screenW: Int, screenH: Int) {
        val unit = minOf(screenW, screenH) / 14f
        val r = unit * 0.92f

        // Left D-Pad
        val padCx = unit * 2.5f
        val padCy = screenH - unit * 2.6f
        setBtn("LEFT", padCx - r * 1.45f, padCy, r * 0.95f)
        setBtn("RIGHT", padCx + r * 1.45f, padCy, r * 0.95f)
        setBtn("UP", padCx, padCy - r * 1.35f, r * 0.85f)
        setBtn("DOWN", padCx, padCy + r * 1.35f, r * 0.85f)

        // Right Action Cluster
        val actCx = screenW - unit * 2.8f
        val actCy = screenH - unit * 2.6f
        setBtn("JUMP", actCx, actCy + r * 1.2f, r)
        setBtn("ROLL", actCx + r * 1.45f, actCy, r * 0.95f)
        setBtn("W1", actCx - r * 1.45f, actCy, r)
        setBtn("W2", actCx, actCy - r * 1.2f, r * 0.95f)

        // Skills & Utilities
        setBtn("S1", actCx - r * 2.8f, actCy - r * 1.4f, r * 0.78f)
        setBtn("S2", actCx - r * 1.2f, actCy - r * 2.6f, r * 0.78f)
        setBtn("FLASK", unit * 1.2f, unit * 2.6f, r * 0.72f)
        setBtn("USE", actCx + r * 1.45f, actCy - r * 1.9f, r * 0.78f)
        setBtn("PAUSE", screenW - unit * 1.1f, unit * 1.1f, r * 0.65f)

        textPaint.textSize = r * 0.65f
    }

    private fun setBtn(id: String, cx: Float, cy: Float, r: Float) {
        byId[id]?.let {
            it.cx = cx
            it.cy = cy
            it.radius = r
        }
    }

    @Synchronized
    fun onTouchEvent(event: MotionEvent, visible: Boolean): Boolean {
        if (!visible) return false

        val prevPressed = HashSet<String>()
        for (b in buttons) {
            if (b.pressed) prevPressed.add(b.id)
            b.pressed = false
        }

        val action = event.actionMasked
        val upIndex = if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) {
            event.actionIndex
        } else -1

        for (i in 0 until event.pointerCount) {
            if (i == upIndex) continue
            val px = event.getX(i)
            val py = event.getY(i)
            for (b in buttons) {
                if (hypot((px - b.cx).toDouble(), (py - b.cy).toDouble()) <= b.radius * 1.25f) {
                    b.pressed = true
                }
            }
        }

        for (b in buttons) {
            if (b.pressed && b.id !in prevPressed) {
                when (b.id) {
                    "JUMP" -> jumpEdge = true
                    "ROLL" -> rollEdge = true
                    "W1" -> w1Edge = true
                    "W2" -> w2Edge = true
                    "S1" -> s1Edge = true
                    "S2" -> s2Edge = true
                    "FLASK" -> flaskEdge = true
                    "USE" -> interactEdge = true
                    "PAUSE" -> pauseEdge = true
                    "UP" -> menuUpEdge = true
                    "DOWN" -> menuDownEdge = true
                }
            }
        }

        val left = byId["LEFT"]?.pressed == true
        val right = byId["RIGHT"]?.pressed == true
        val up = byId["UP"]?.pressed == true
        val down = byId["DOWN"]?.pressed == true
        moveX = (if (right) 1f else 0f) - (if (left) 1f else 0f)
        moveY = (if (down) 1f else 0f) - (if (up) 1f else 0f)
        jumpHeld = byId["JUMP"]?.pressed == true
        weapon2Held = byId["W2"]?.pressed == true
        return true
    }

    @Synchronized fun consumeJump(): Boolean = jumpEdge.also { jumpEdge = false }
    @Synchronized fun consumeRoll(): Boolean = rollEdge.also { rollEdge = false }
    @Synchronized fun consumeWeapon1(): Boolean = w1Edge.also { w1Edge = false }
    @Synchronized fun consumeWeapon2(): Boolean = w2Edge.also { w2Edge = false }
    @Synchronized fun consumeSkill1(): Boolean = s1Edge.also { s1Edge = false }
    @Synchronized fun consumeSkill2(): Boolean = s2Edge.also { s2Edge = false }
    @Synchronized fun consumeFlask(): Boolean = flaskEdge.also { flaskEdge = false }
    @Synchronized fun consumeInteract(): Boolean = interactEdge.also { interactEdge = false }
    @Synchronized fun consumePause(): Boolean = pauseEdge.also { pauseEdge = false }
    @Synchronized fun consumeMenuUp(): Boolean = menuUpEdge.also { menuUpEdge = false }
    @Synchronized fun consumeMenuDown(): Boolean = menuDownEdge.also { menuDownEdge = false }

    fun draw(canvas: Canvas) {
        for (b in buttons) {
            fillPaint.color = b.color
            fillPaint.alpha = if (b.pressed) 150 else 58
            strokePaint.color = b.color
            strokePaint.alpha = if (b.pressed) 235 else 135
            canvas.drawCircle(b.cx, b.cy, b.radius, fillPaint)
            canvas.drawCircle(b.cx, b.cy, b.radius, strokePaint)
            textPaint.alpha = if (b.pressed) 255 else 190
            canvas.drawText(b.label, b.cx, b.cy + textPaint.textSize * 0.34f, textPaint)
        }
    }
}
