package com.crimsoncell.entity

import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class Particle(
    var active: Boolean = false,
    var x: Float = 0f,
    var y: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var size: Float = 2f,
    var color: Int = 0xFFFFFFFF.toInt(),
    var life: Float = 0f,
    var maxLife: Float = 0.4f,
    var gravity: Float = 380f
)

data class FloatingText(
    var active: Boolean = false,
    var x: Float = 0f,
    var y: Float = 0f,
    var vy: Float = -38f,
    var text: String = "",
    var color: Int = 0xFFFFFFFF.toInt(),
    var life: Float = 0f,
    var maxLife: Float = 0.75f,
    var isCrit: Boolean = false
)

data class Afterimage(
    var x: Float,
    var y: Float,
    var facing: Int,
    var color: Int,
    var life: Float,
    val maxLife: Float = 0.22f
)

data class SlashArc(
    var x: Float,
    var y: Float,
    var facing: Int,
    var radius: Float,
    var color: Int,
    var life: Float,
    val maxLife: Float = 0.14f,
    val isHeavy: Boolean = false
)

class ParticleSystem(maxParticles: Int = 320, maxTexts: Int = 48) {
    val particles = Array(maxParticles) { Particle() }
    val floatingTexts = Array(maxTexts) { FloatingText() }
    val afterimages = ArrayList<Afterimage>(24)
    val slashArcs = ArrayList<SlashArc>(16)

    private var particleCursor = 0
    private var textCursor = 0

    fun spawnBurst(
        x: Float,
        y: Float,
        count: Int,
        color: Int,
        minSpeed: Float = 40f,
        maxSpeed: Float = 160f,
        gravity: Float = 380f,
        size: Float = 2f,
        life: Float = 0.42f,
        rng: Random = Random.Default
    ) {
        for (i in 0 until count) {
            val p = particles[particleCursor]
            particleCursor = (particleCursor + 1) % particles.size
            val angle = rng.nextFloat() * (Math.PI * 2.0).toFloat()
            val speed = minSpeed + rng.nextFloat() * (maxSpeed - minSpeed)
            p.active = true
            p.x = x + (rng.nextFloat() - 0.5f) * 4f
            p.y = y + (rng.nextFloat() - 0.5f) * 4f
            p.vx = cos(angle) * speed
            p.vy = sin(angle) * speed - 30f
            p.size = size + rng.nextFloat() * 1.5f
            p.color = color
            p.maxLife = life * (0.65f + rng.nextFloat() * 0.6f)
            p.life = p.maxLife
            p.gravity = gravity
        }
    }

    fun spawnDirectionalSparks(
        x: Float,
        y: Float,
        dirX: Float,
        count: Int,
        color: Int,
        rng: Random = Random.Default
    ) {
        for (i in 0 until count) {
            val p = particles[particleCursor]
            particleCursor = (particleCursor + 1) % particles.size
            p.active = true
            p.x = x
            p.y = y
            p.vx = dirX * (70f + rng.nextFloat() * 160f) + (rng.nextFloat() - 0.5f) * 40f
            p.vy = -40f - rng.nextFloat() * 110f
            p.size = 1.5f + rng.nextFloat() * 2f
            p.color = color
            p.maxLife = 0.25f + rng.nextFloat() * 0.25f
            p.life = p.maxLife
            p.gravity = 420f
        }
    }

    fun spawnText(x: Float, y: Float, text: String, color: Int, isCrit: Boolean = false) {
        val ft = floatingTexts[textCursor]
        textCursor = (textCursor + 1) % floatingTexts.size
        ft.active = true
        ft.x = x
        ft.y = y
        ft.vy = if (isCrit) -52f else -36f
        ft.text = text
        ft.color = color
        ft.maxLife = if (isCrit) 0.90f else 0.68f
        ft.life = ft.maxLife
        ft.isCrit = isCrit
    }

    fun spawnAfterimage(x: Float, y: Float, facing: Int, color: Int = 0xFF38BDF8.toInt()) {
        if (afterimages.size < 24) {
            afterimages.add(Afterimage(x, y, facing, color, 0.22f, 0.22f))
        }
    }

    fun spawnSlashArc(x: Float, y: Float, facing: Int, radius: Float, color: Int, isHeavy: Boolean = false) {
        if (slashArcs.size < 16) {
            slashArcs.add(SlashArc(x, y, facing, radius, color, 0.14f, 0.14f, isHeavy))
        }
    }

    fun update(dt: Float) {
        for (p in particles) {
            if (!p.active) continue
            p.life -= dt
            if (p.life <= 0f) {
                p.active = false
                continue
            }
            p.vy += p.gravity * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vx *= 0.96f
        }

        for (ft in floatingTexts) {
            if (!ft.active) continue
            ft.life -= dt
            if (ft.life <= 0f) {
                ft.active = false
                continue
            }
            ft.y += ft.vy * dt
            ft.vy *= 0.92f
        }

        val imgIter = afterimages.iterator()
        while (imgIter.hasNext()) {
            val a = imgIter.next()
            a.life -= dt
            if (a.life <= 0f) imgIter.remove()
        }

        val arcIter = slashArcs.iterator()
        while (arcIter.hasNext()) {
            val s = arcIter.next()
            s.life -= dt
            if (s.life <= 0f) arcIter.remove()
        }
    }

    fun clear() {
        for (p in particles) p.active = false
        for (ft in floatingTexts) ft.active = false
        afterimages.clear()
        slashArcs.clear()
    }
}
