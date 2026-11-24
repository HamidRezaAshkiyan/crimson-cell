package com.crimsoncell.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.crimsoncell.combat.HazardKind
import com.crimsoncell.combat.StatusType
import com.crimsoncell.combat.WeaponInstance
import com.crimsoncell.enemy.Enemy
import com.crimsoncell.engine.GameEngine
import com.crimsoncell.engine.GameMode
import com.crimsoncell.input.InputState
import com.crimsoncell.progression.ForgeUpgrade
import com.crimsoncell.world.BiomeId
import com.crimsoncell.world.InteractableType
import com.crimsoncell.world.LevelMap
import com.crimsoncell.world.TileType
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Crisp 480x270 nearest-neighbor retro pixel-art renderer.
 * Draws parallax dungeons, flickering torch glow, Verlet scarf, animated
 * hero & enemies, `!` attack telegraphs, particles, mini-map, HUD & menus.
 */
class PixelRenderer {

    private val virtualBitmap = Bitmap.createBitmap(GameEngine.VIRTUAL_W, GameEngine.VIRTUAL_H, Bitmap.Config.ARGB_8888)
    private val vCanvas = Canvas(virtualBitmap)
    private val sprites = PixelSprites()

    private val blitPaint = Paint().apply {
        isFilterBitmap = false
        isAntiAlias = false
    }
    private val solidPaint = Paint().apply {
        isAntiAlias = false
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint().apply {
        isAntiAlias = false
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val textPaint = Paint().apply {
        isAntiAlias = false
        typeface = Typeface.MONOSPACE
        isFakeBoldText = true
        textSize = 7f
    }

    private val srcRect = Rect(0, 0, GameEngine.VIRTUAL_W, GameEngine.VIRTUAL_H)
    private val dstRect = Rect()
    private val tmpRectF = RectF()
    private val shakeRng = Random(42)

    fun render(screenCanvas: Canvas, screenW: Int, screenH: Int, engine: GameEngine, input: InputState) {
        // Draw into 480x270 virtual pixel buffer
        renderWorldToVirtual(engine, input)

        // Letterbox / pillarbox crisp nearest-neighbor blit to physical screen
        screenCanvas.drawColor(0xFF000000.toInt())
        val aspect = GameEngine.VIRTUAL_W.toFloat() / GameEngine.VIRTUAL_H.toFloat()
        val screenAspect = screenW.toFloat() / screenH.toFloat()
        if (screenAspect > aspect) {
            val targetW = (screenH * aspect).toInt()
            val left = (screenW - targetW) / 2
            dstRect.set(left, 0, left + targetW, screenH)
        } else {
            val targetH = (screenW / aspect).toInt()
            val top = (screenH - targetH) / 2
            dstRect.set(0, top, screenW, top + targetH)
        }
        screenCanvas.drawBitmap(virtualBitmap, srcRect, dstRect, blitPaint)
    }

    private fun renderWorldToVirtual(engine: GameEngine, input: InputState) {
        val c = vCanvas
        val map = engine.map
        val biome = map.biome

        // Screen shake offset
        val shakeX = if (engine.shakeTimer > 0f) (shakeRng.nextFloat() - 0.5f) * 2f * engine.shakeIntensity else 0f
        val shakeY = if (engine.shakeTimer > 0f) (shakeRng.nextFloat() - 0.5f) * 2f * engine.shakeIntensity else 0f
        val camX = engine.cameraX + shakeX
        val camY = engine.cameraY + shakeY

        // 1. Sky & Parallax Background
        drawParallaxBackground(c, biome, camX, engine.runTimeSec)

        // 2. World Layer (camera translated)
        c.save()
        c.translate(-camX, -camY)

        drawTorches(c, map, camX, engine.runTimeSec)
        drawTilemap(c, map, camX, camY)
        drawInteractables(c, map, engine.runTimeSec)
        drawHazards(c, engine)
        drawAfterimages(c, engine)
        drawEnemies(c, map)
        drawPlayer(c, engine)
        drawSlashArcs(c, engine)
        drawProjectiles(c, engine)
        drawParticles(c, engine)
        drawFloatingTexts(c, engine)

        // Nearby interact prompt above object
        engine.nearbyInteractable?.let { item ->
            val prompt = if (input.usingController) "[RB] ${item.label}" else "[USE] ${item.label}"
            drawCenteredPill(c, item.x, item.y - 16f, prompt, 0xFFFACC15.toInt())
        }

        c.restore()

        // 3. HUD & Overlays
        when (engine.mode) {
            GameMode.TITLE -> drawTitleOverlay(c, engine, input)
            GameMode.PLAYING -> drawHud(c, engine, input)
            GameMode.PAUSED -> {
                drawHud(c, engine, input)
                drawPauseOverlay(c, engine)
            }
            GameMode.SHOP -> {
                drawHud(c, engine, input)
                drawShopOverlay(c, engine)
            }
            GameMode.FORGE -> {
                drawHud(c, engine, input)
                drawForgeOverlay(c, engine)
            }
            GameMode.GAME_OVER -> drawEndOverlay(c, engine, victory = false)
            GameMode.VICTORY -> drawEndOverlay(c, engine, victory = true)
        }
    }

    private fun drawParallaxBackground(c: Canvas, biome: com.crimsoncell.world.BiomeConfig, camX: Float, time: Float) {
        // Vertical gradient bands (retro stepped sky)
        solidPaint.color = biome.skyTopColor
        c.drawRect(0f, 0f, GameEngine.VIRTUAL_W.toFloat(), GameEngine.VIRTUAL_H * 0.55f, solidPaint)
        solidPaint.color = biome.skyBottomColor
        c.drawRect(0f, GameEngine.VIRTUAL_H * 0.55f, GameEngine.VIRTUAL_W.toFloat(), GameEngine.VIRTUAL_H.toFloat(), solidPaint)

        // Far silhouettes (0.18x parallax)
        solidPaint.color = biome.parallaxFarColor
        val farOffset = -(camX * 0.18f) % 64f
        var x = farOffset - 64f
        var idx = 0
        while (x < GameEngine.VIRTUAL_W + 64f) {
            val h = 70f + ((idx * 37) % 55)
            c.drawRect(x, GameEngine.VIRTUAL_H - h, x + 46f, GameEngine.VIRTUAL_H.toFloat(), solidPaint)
            // Clockwork Spire rotating background gears!
            if (biome.id == BiomeId.CLOCKWORK_SPIRE && idx % 2 == 0) {
                strokePaint.color = biome.parallaxNearColor
                strokePaint.strokeWidth = 2f
                val gx = x + 23f
                val gy = 70f + (idx % 3) * 32f
                c.drawCircle(gx, gy, 22f, strokePaint)
                for (spoke in 0 until 6) {
                    val a = time * 0.45f + spoke * 1.047f
                    c.drawLine(gx, gy, gx + cos(a) * 26f, gy + sin(a) * 26f, strokePaint)
                }
                strokePaint.strokeWidth = 1f
            }
            x += 64f
            idx++
        }

        // Near arches / pillars (0.38x parallax)
        solidPaint.color = biome.parallaxNearColor
        val nearOffset = -(camX * 0.38f) % 88f
        x = nearOffset - 88f
        while (x < GameEngine.VIRTUAL_W + 88f) {
            c.drawRect(x, 24f, x + 14f, GameEngine.VIRTUAL_H.toFloat(), solidPaint)
            c.drawRect(x, 24f, x + 88f, 34f, solidPaint)
            x += 88f
        }
    }

    private fun drawTorches(c: Canvas, map: LevelMap, camX: Float, time: Float) {
        for (t in map.torches) {
            if (t.x < camX - 60f || t.x > camX + GameEngine.VIRTUAL_W + 60f) continue
            val flicker = sin(time * 14f + t.x * 0.3f) * 3f
            // Soft retro concentric glow rings
            solidPaint.color = map.biome.accentGlowColor
            solidPaint.alpha = 24
            c.drawCircle(t.x, t.y, 26f + flicker, solidPaint)
            solidPaint.alpha = 46
            c.drawCircle(t.x, t.y, 13f + flicker * 0.5f, solidPaint)
            solidPaint.alpha = 255

            // Torch bracket & flame
            solidPaint.color = 0xFF78350F.toInt()
            c.drawRect(t.x - 1.5f, t.y, t.x + 1.5f, t.y + 7f, solidPaint)
            solidPaint.color = 0xFFFACC15.toInt()
            c.drawRect(t.x - 2f, t.y - 4f, t.x + 2f, t.y, solidPaint)
        }
    }

    private fun drawTilemap(c: Canvas, map: LevelMap, camX: Float, camY: Float) {
        val ts = LevelMap.TILE_SIZE
        val startX = (camX / ts).toInt().coerceAtLeast(0)
        val endX = ((camX + GameEngine.VIRTUAL_W) / ts + 1).toInt().coerceAtMost(map.widthTiles - 1)
        val startY = (camY / ts).toInt().coerceAtLeast(0)
        val endY = ((camY + GameEngine.VIRTUAL_H) / ts + 1).toInt().coerceAtMost(map.heightTiles - 1)

        for (ty in startY..endY) {
            for (tx in startX..endX) {
                val tile = map.tiles[ty][tx]
                if (tile == TileType.EMPTY) continue
                val px = (tx * ts).toFloat()
                val py = (ty * ts).toFloat()

                when (tile) {
                    TileType.SOLID -> {
                        solidPaint.color = map.biome.tileMainColor
                        c.drawRect(px, py, px + ts, py + ts, solidPaint)
                        // Highlight top edge if exposed to air
                        if (!map.isSolid(tx, ty - 1)) {
                            solidPaint.color = map.biome.tileHighlightColor
                            c.drawRect(px, py, px + ts, py + 3f, solidPaint)
                        }
                        // Brick mortar lines
                        solidPaint.color = map.biome.tileDarkColor
                        c.drawRect(px, py + ts - 1f, px + ts, py + ts, solidPaint)
                        val offset = if (ty % 2 == 0) 0f else 8f
                        c.drawRect(px + offset, py, px + offset + 1f, py + ts, solidPaint)
                    }
                    TileType.ONE_WAY -> {
                        solidPaint.color = map.biome.platformColor
                        c.drawRect(px, py, px + ts, py + 4f, solidPaint)
                        solidPaint.color = map.biome.tileDarkColor
                        c.drawRect(px, py + 4f, px + ts, py + 6f, solidPaint)
                    }
                    TileType.SPIKES -> {
                        solidPaint.color = 0xFFCBD5E1.toInt()
                        for (i in 0 until 4) {
                            val sx = px + i * 4f
                            c.drawRect(sx + 1f, py + 6f, sx + 3f, py + ts, solidPaint)
                            c.drawRect(sx + 1.5f, py + 3f, sx + 2.5f, py + 6f, solidPaint)
                        }
                    }
                    TileType.ACID -> {
                        solidPaint.color = 0xFF22C55E.toInt()
                        c.drawRect(px, py + 5f, px + ts, py + ts, solidPaint)
                        solidPaint.color = 0xFF86EFAC.toInt()
                        c.drawRect(px, py + 5f, px + ts, py + 7f, solidPaint)
                    }
                    TileType.EMPTY -> Unit
                }
            }
        }
    }

    private fun drawInteractables(c: Canvas, map: LevelMap, time: Float) {
        for (item in map.interactables) {
            if (item.type == InteractableType.CRATE && item.used) continue
            when (item.type) {
                InteractableType.CHEST -> {
                    val bmp = if (item.used) sprites.chestOpen else sprites.chestClosed
                    drawSprite(c, bmp, item.x - 8f, item.y - 2f, 16f, 12f, 1)
                }
                InteractableType.WEAPON_PEDESTAL -> {
                    if (!item.used) {
                        val bob = sin(time * 5f) * 2.5f
                        solidPaint.color = item.weapon?.def?.colorHex ?: 0xFFFACC15.toInt()
                        c.drawRect(item.x - 5f, item.y - 4f + bob, item.x + 5f, item.y + 4f + bob, solidPaint)
                        solidPaint.color = 0xFF475569.toInt()
                        c.drawRect(item.x - 7f, item.y + 6f, item.x + 7f, item.y + 12f, solidPaint)
                    }
                }
                InteractableType.SCROLL_SHRINE -> {
                    drawSprite(c, sprites.shrine, item.x - 8f, item.y - 4f, 16f, 18f, 1)
                    val sc = item.scrollColor
                    if (!item.used && sc != null) {
                        solidPaint.color = sc.colorHex
                        val bob = sin(time * 6f) * 2f
                        c.drawCircle(item.x, item.y - 6f + bob, 3.5f, solidPaint)
                    }
                }
                InteractableType.MERCHANT -> {
                    drawSprite(c, sprites.merchant, item.x - 8f, item.y - 2f, 16f, 18f, 1)
                }
                InteractableType.CELL_FORGE -> {
                    drawSprite(c, sprites.forge, item.x - 9f, item.y - 2f, 18f, 18f, 1)
                }
                InteractableType.EXIT_PORTAL -> {
                    val pulse = sin(time * 6f) * 2f
                    val locked = map.bossRoom != null && !map.bossDefeated
                    solidPaint.color = if (locked) 0xFF64748B.toInt() else 0xFF38BDF8.toInt()
                    solidPaint.alpha = 180
                    c.drawOval(item.x - 10f - pulse * 0.4f, item.y - 12f, item.x + 10f + pulse * 0.4f, item.y + 20f, solidPaint)
                    solidPaint.color = if (locked) 0xFF334155.toInt() else 0xFFE0F2FE.toInt()
                    solidPaint.alpha = 255
                    c.drawOval(item.x - 5f, item.y - 6f, item.x + 5f, item.y + 14f, solidPaint)
                }
                InteractableType.CRATE -> {
                    drawSprite(c, sprites.crate, item.x - 6f, item.y, 12f, 12f, 1)
                }
            }
        }
    }

    private fun drawHazards(c: Canvas, engine: GameEngine) {
        for (h in engine.combat.hazards) {
            when (h.kind) {
                HazardKind.FIRE_POOL -> {
                    solidPaint.color = 0xFFF97316.toInt()
                    solidPaint.alpha = 190
                    c.drawRect(h.x - h.radius, h.y - 3f, h.x + h.radius, h.y + 3f, solidPaint)
                    solidPaint.alpha = 255
                }
                HazardKind.POISON_CLOUD -> {
                    solidPaint.color = 0xFF4ADE80.toInt()
                    solidPaint.alpha = 90
                    c.drawCircle(h.x, h.y, h.radius, solidPaint)
                    solidPaint.alpha = 255
                }
                HazardKind.WOLF_TRAP -> {
                    solidPaint.color = 0xFF2DD4BF.toInt()
                    c.drawRect(h.x - 7f, h.y, h.x + 7f, h.y + 4f, solidPaint)
                }
                HazardKind.SAW_TURRET -> {
                    solidPaint.color = 0xFFA855F7.toInt()
                    c.drawRect(h.x - 5f, h.y - 6f, h.x + 5f, h.y + 6f, solidPaint)
                    solidPaint.color = 0xFFFACC15.toInt()
                    c.drawCircle(h.x, h.y - 5f, 3f, solidPaint)
                }
            }
        }
    }

    private fun drawAfterimages(c: Canvas, engine: GameEngine) {
        for (img in engine.particles.afterimages) {
            solidPaint.color = img.color
            solidPaint.alpha = ((img.life / img.maxLife) * 140).toInt().coerceIn(0, 140)
            c.drawRect(img.x, img.y, img.x + 12f, img.y + 20f, solidPaint)
        }
        solidPaint.alpha = 255
    }

    private fun drawPlayer(c: Canvas, engine: GameEngine) {
        val p = engine.player

        // Draw Verlet trailing crimson scarf!
        strokePaint.color = 0xFFE63946.toInt()
        strokePaint.strokeWidth = 2.5f
        for (i in 0 until p.scarfX.size - 1) {
            c.drawLine(p.scarfX[i], p.scarfY[i], p.scarfX[i + 1], p.scarfY[i + 1], strokePaint)
        }
        strokePaint.strokeWidth = 1f

        val bmp = when {
            p.isRolling -> sprites.heroRoll
            kotlin.math.abs(p.vx) > 25f && p.onGround -> if ((p.animTime * 12f).toInt() % 2 == 0) sprites.heroRun1 else sprites.heroRun2
            else -> sprites.heroIdle
        }

        // Flash white briefly when hurt
        if (p.hurtFlashTimer > 0f && (p.hurtFlashTimer * 30f).toInt() % 2 == 0) {
            solidPaint.color = 0xFFFFFFFF.toInt()
            c.drawRect(p.x, p.y, p.x + p.width, p.y + p.height, solidPaint)
        } else {
            drawSprite(c, bmp, p.x - 3f, p.y - 1f, p.width + 6f, p.height + 2f, p.facing)
        }

        // Shield block / Parry golden aura
        if (p.parryWindowTimer > 0f) {
            strokePaint.color = 0xFFFACC15.toInt()
            strokePaint.strokeWidth = 2f
            c.drawCircle(p.centerX + p.facing * 6f, p.centerY, 12f, strokePaint)
            strokePaint.strokeWidth = 1f
        } else if (p.isBlocking) {
            solidPaint.color = 0xFF2EC4B6.toInt()
            c.drawRect(
                p.centerX + p.facing * 7f - 2f,
                p.y + 3f,
                p.centerX + p.facing * 7f + 2f,
                p.bottomY - 2f,
                solidPaint
            )
        }
    }

    private fun drawEnemies(c: Canvas, map: LevelMap) {
        for (e in map.enemies) {
            if (!e.isAlive) continue
            val bmp = sprites.enemySprites[e.type]

            if (e.hurtFlashTimer > 0f) {
                solidPaint.color = 0xFFFFFFFF.toInt()
                c.drawRect(e.x, e.y, e.x + e.width, e.y + e.height, solidPaint)
            } else if (bmp != null) {
                drawSprite(c, bmp, e.x - 1f, e.y - 1f, e.width + 2f, e.height + 2f, e.facing)
            }

            // Frozen ice block tint
            if (e.isFrozen) {
                solidPaint.color = 0xFF7DD3FC.toInt()
                solidPaint.alpha = 120
                c.drawRect(e.x - 1f, e.y - 1f, e.x + e.width + 1f, e.y + e.height + 1f, solidPaint)
                solidPaint.alpha = 255
            }

            // Elite golden outline
            if (e.isElite) {
                strokePaint.color = 0xFFFACC15.toInt()
                c.drawRect(e.x - 1f, e.y - 1f, e.x + e.width + 1f, e.y + e.height + 1f, strokePaint)
            }

            // Crisp yellow `!` attack telegraph above enemy head!
            if (e.isTelegraphing) {
                textPaint.color = 0xFFFACC15.toInt()
                textPaint.textSize = 11f
                textPaint.textAlign = Paint.Align.CENTER
                c.drawText("!", e.centerX, e.y - 6f, textPaint)
                textPaint.textSize = 7f
            }

            // Enemy mini HP bar (non-bosses when damaged)
            if (!e.type.isBoss && e.hp < e.maxHp) {
                val barW = e.width.coerceAtLeast(16f)
                val bx = e.centerX - barW * 0.5f
                val by = e.y - 5f
                solidPaint.color = 0xFF0F172A.toInt()
                c.drawRect(bx - 1f, by - 1f, bx + barW + 1f, by + 3f, solidPaint)
                solidPaint.color = 0xFFE63946.toInt()
                c.drawRect(bx, by, bx + barW * (e.hp / e.maxHp), by + 2f, solidPaint)
            }

            // Status dots
            var sx = e.x
            for (st in e.statusTimers.keys) {
                solidPaint.color = st.colorHex
                c.drawRect(sx, e.y - 8f, sx + 2.5f, e.y - 5.5f, solidPaint)
                sx += 3.5f
            }
        }
    }

    private fun drawSlashArcs(c: Canvas, engine: GameEngine) {
        for (arc in engine.particles.slashArcs) {
            val alpha = ((arc.life / arc.maxLife) * 235).toInt().coerceIn(0, 235)
            strokePaint.color = arc.color
            strokePaint.alpha = alpha
            strokePaint.strokeWidth = if (arc.isHeavy) 4f else 2.5f
            val r = arc.radius * 0.6f
            tmpRectF.set(arc.x - r, arc.y - r * 0.65f, arc.x + r, arc.y + r * 0.65f)
            val startAng = if (arc.facing > 0) -65f else 115f
            c.drawArc(tmpRectF, startAng, 130f, false, strokePaint)
        }
        strokePaint.alpha = 255
        strokePaint.strokeWidth = 1f
    }

    private fun drawProjectiles(c: Canvas, engine: GameEngine) {
        for (p in engine.combat.projectiles) {
            solidPaint.color = p.color
            val r = if (p.explodeRadius > 0f) 3.5f else 2.2f
            c.drawRect(p.x - r, p.y - r * 0.7f, p.x + r, p.y + r * 0.7f, solidPaint)
        }
    }

    private fun drawParticles(c: Canvas, engine: GameEngine) {
        for (p in engine.particles.particles) {
            if (!p.active) continue
            solidPaint.color = p.color
            solidPaint.alpha = ((p.life / p.maxLife) * 255).toInt().coerceIn(0, 255)
            c.drawRect(p.x, p.y, p.x + p.size, p.y + p.size, solidPaint)
        }
        solidPaint.alpha = 255
    }

    private fun drawFloatingTexts(c: Canvas, engine: GameEngine) {
        textPaint.textAlign = Paint.Align.CENTER
        for (ft in engine.particles.floatingTexts) {
            if (!ft.active) continue
            textPaint.textSize = if (ft.isCrit) 9f else 7f
            textPaint.color = 0xFF000000.toInt()
            c.drawText(ft.text, ft.x + 1f, ft.y + 1f, textPaint)
            textPaint.color = ft.color
            c.drawText(ft.text, ft.x, ft.y, textPaint)
        }
        textPaint.textSize = 7f
    }

    private fun drawHud(c: Canvas, engine: GameEngine, input: InputState) {
        val p = engine.player

        // Top-Left: HP Bar with Dead Cells Rally orange recovery bar
        val hpBarW = 104f
        solidPaint.color = 0xCC0B0F19.toInt()
        c.drawRect(6f, 6f, 6f + hpBarW + 4f, 20f, solidPaint)
        solidPaint.color = 0xFF1E293B.toInt()
        c.drawRect(8f, 8f, 8f + hpBarW, 18f, solidPaint)
        // Rally bar (orange)
        val rallyFrac = (p.rallyHp / p.maxHp).coerceIn(0f, 1f)
        solidPaint.color = 0xFFF97316.toInt()
        c.drawRect(8f, 8f, 8f + hpBarW * rallyFrac, 18f, solidPaint)
        // Current HP (crimson/green)
        val hpFrac = (p.hp / p.maxHp).coerceIn(0f, 1f)
        solidPaint.color = if (hpFrac < 0.25f) 0xFFEF4444.toInt() else 0xFF22C55E.toInt()
        c.drawRect(8f, 8f, 8f + hpBarW * hpFrac, 18f, solidPaint)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = 0xFFFFFFFF.toInt()
        c.drawText("${p.hp.toInt()}/${p.maxHp.toInt()}", 11f, 16f, textPaint)

        // Flask charges (LB)
        solidPaint.color = 0xCC0B0F19.toInt()
        c.drawRect(6f, 22f, 54f, 34f, solidPaint)
        textPaint.color = 0xFF2EC4B6.toInt()
        c.drawText("LB HP:${p.flaskCharges}/${p.maxFlaskCharges}", 9f, 30f, textPaint)

        // Scrolls (B/T/S)
        solidPaint.color = 0xCC0B0F19.toInt()
        c.drawRect(57f, 22f, 114f, 34f, solidPaint)
        textPaint.color = 0xFFE63946.toInt(); c.drawText("B${p.brutalityScrolls}", 60f, 30f, textPaint)
        textPaint.color = 0xFFA855F7.toInt(); c.drawText("T${p.tacticsScrolls}", 79f, 30f, textPaint)
        textPaint.color = 0xFF2EC4B6.toInt(); c.drawText("S${p.survivalScrolls}", 97f, 30f, textPaint)

        // Top-Right: Gold, Cells, Biome & Mini-map
        val rightX = GameEngine.VIRTUAL_W - 96f
        solidPaint.color = 0xCC0B0F19.toInt()
        c.drawRect(rightX, 6f, GameEngine.VIRTUAL_W - 6f, 20f, solidPaint)
        textPaint.color = 0xFFFACC15.toInt()
        c.drawText("${p.gold}G", rightX + 4f, 16f, textPaint)
        textPaint.color = 0xFF38BDF8.toInt()
        c.drawText("CELL:${p.runCells}", rightX + 42f, 16f, textPaint)

        drawMiniMap(c, engine, rightX, 23f, 90f, 26f)

        // Bottom-Left: Weapon & Skill Slots (X, Y, LT, RT)
        drawSlot(c, 8f, GameEngine.VIRTUAL_H - 30f, "X", p.weapon1)
        drawSlot(c, 58f, GameEngine.VIRTUAL_H - 30f, "Y", p.weapon2)
        drawSlot(c, 112f, GameEngine.VIRTUAL_H - 30f, "LT", p.skill1)
        drawSlot(c, 162f, GameEngine.VIRTUAL_H - 30f, "RT", p.skill2)

        // Controller status indicator bottom-right
        if (input.usingController) {
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.color = 0xFF94A3B8.toInt()
            c.drawText("GAMEPAD READY", GameEngine.VIRTUAL_W - 8f, GameEngine.VIRTUAL_H - 8f, textPaint)
            textPaint.textAlign = Paint.Align.LEFT
        }

        // Boss Health Bar at top center
        engine.activeBoss()?.let { boss ->
            drawBossBar(c, boss)
        }

        // Biome Entry Banner
        if (engine.biomeBannerTimer > 0f) {
            val alpha = (engine.biomeBannerTimer.coerceAtMost(1f) * 255).toInt()
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = 12f
            textPaint.color = 0xFFF8FAFC.toInt()
            textPaint.alpha = alpha
            c.drawText(engine.map.biome.name, GameEngine.VIRTUAL_W * 0.5f, 64f, textPaint)
            textPaint.textSize = 7f
            textPaint.color = engine.map.biome.accentGlowColor
            textPaint.alpha = alpha
            c.drawText(engine.map.biome.subtitle, GameEngine.VIRTUAL_W * 0.5f, 76f, textPaint)
            textPaint.alpha = 255
            textPaint.textAlign = Paint.Align.LEFT
        }
    }

    private fun drawSlot(c: Canvas, x: Float, y: Float, key: String, item: WeaponInstance?) {
        val w = 46f
        val h = 24f
        solidPaint.color = 0xD00B0F19.toInt()
        c.drawRect(x, y, x + w, y + h, solidPaint)
        strokePaint.color = item?.def?.statColor?.colorHex ?: 0xFF475569.toInt()
        c.drawRect(x, y, x + w, y + h, strokePaint)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = 0xFFFACC15.toInt()
        c.drawText(key, x + 3f, y + 8f, textPaint)

        if (item != null) {
            textPaint.color = item.def.colorHex
            c.drawText(item.def.shortName, x + 3f, y + 18f, textPaint)
            if (item.cooldownRemaining > 0f && item.def.cooldownSec > 0f) {
                val frac = (item.cooldownRemaining / item.def.cooldownSec).coerceIn(0f, 1f)
                solidPaint.color = 0xB0000000.toInt()
                c.drawRect(x, y + h * (1f - frac), x + w, y + h, solidPaint)
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.color = 0xFFFFFFFF.toInt()
                c.drawText("${item.cooldownRemaining.toInt() + 1}s", x + w * 0.5f, y + 16f, textPaint)
                textPaint.textAlign = Paint.Align.LEFT
            }
        }
    }

    private fun drawMiniMap(c: Canvas, engine: GameEngine, x: Float, y: Float, w: Float, h: Float) {
        solidPaint.color = 0xB00B0F19.toInt()
        c.drawRect(x, y, x + w, y + h, solidPaint)
        strokePaint.color = 0xFF334155.toInt()
        c.drawRect(x, y, x + w, y + h, strokePaint)

        val map = engine.map
        val sx = (w - 4f) / map.widthTiles
        val sy = (h - 4f) / map.heightTiles
        for (room in map.rooms) {
            if (!room.discovered) continue
            solidPaint.color = when (room.kind) {
                com.crimsoncell.world.RoomKind.BOSS -> 0xFFEF4444.toInt()
                com.crimsoncell.world.RoomKind.SHOP -> 0xFFFACC15.toInt()
                com.crimsoncell.world.RoomKind.TREASURE -> 0xFF38BDF8.toInt()
                else -> 0xFF64748B.toInt()
            }
            c.drawRect(
                x + 2f + room.tileX * sx,
                y + 2f + room.tileY * sy,
                x + 2f + (room.tileX + room.tileW) * sx,
                y + 2f + (room.tileY + room.tileH) * sy,
                solidPaint
            )
        }
        // Player dot
        val px = x + 2f + (engine.player.centerX / LevelMap.TILE_SIZE) * sx
        val py = y + 2f + (engine.player.centerY / LevelMap.TILE_SIZE) * sy
        solidPaint.color = 0xFFFFFFFF.toInt()
        c.drawRect(px - 1f, py - 1f, px + 2f, py + 2f, solidPaint)
    }

    private fun drawBossBar(c: Canvas, boss: Enemy) {
        val barW = 200f
        val x = (GameEngine.VIRTUAL_W - barW) * 0.5f
        val y = 12f
        solidPaint.color = 0xDD0B0F19.toInt()
        c.drawRect(x - 2f, y - 2f, x + barW + 2f, y + 10f, solidPaint)
        solidPaint.color = if (boss.bossPhase == 2) 0xFFFF2E63.toInt() else 0xFFE63946.toInt()
        c.drawRect(x, y, x + barW * (boss.hp / boss.maxHp).coerceIn(0f, 1f), y + 8f, solidPaint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = 0xFFFFFFFF.toInt()
        c.drawText(boss.type.displayName, GameEngine.VIRTUAL_W * 0.5f, y + 7f, textPaint)
        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawTitleOverlay(c: Canvas, engine: GameEngine, input: InputState) {
        solidPaint.color = 0xBB090910.toInt()
        c.drawRect(0f, 0f, GameEngine.VIRTUAL_W.toFloat(), GameEngine.VIRTUAL_H.toFloat(), solidPaint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 22f
        textPaint.color = 0xFFE63946.toInt()
        c.drawText("CRIMSON CELL", GameEngine.VIRTUAL_W * 0.5f, 78f, textPaint)

        textPaint.textSize = 8f
        textPaint.color = 0xFFCBD5E1.toInt()
        c.drawText("FAST-PACED RETRO ACTION ROGUELITE", GameEngine.VIRTUAL_W * 0.5f, 96f, textPaint)

        textPaint.textSize = 7f
        textPaint.color = 0xFF94A3B8.toInt()
        c.drawText("20 WEAPONS & SKILLS  |  10 ENEMIES + 2 BOSSES  |  3 BIOMES", GameEngine.VIRTUAL_W * 0.5f, 114f, textPaint)

        // Controls card
        solidPaint.color = 0xCC111827.toInt()
        c.drawRect(88f, 128f, 392f, 198f, solidPaint)
        strokePaint.color = 0xFF334155.toInt()
        c.drawRect(88f, 128f, 392f, 198f, strokePaint)

        textPaint.color = 0xFFFACC15.toInt()
        c.drawText("GAMEPAD CONTROLS", GameEngine.VIRTUAL_W * 0.5f, 142f, textPaint)
        textPaint.color = 0xFFE2E8F0.toInt()
        c.drawText("A: Jump / Double Jump / Dive Slam (Down+A)    B: Dodge Roll", GameEngine.VIRTUAL_W * 0.5f, 156f, textPaint)
        c.drawText("X / Y: Weapons & Shield Parry     LT / RT: Active Skills", GameEngine.VIRTUAL_W * 0.5f, 169f, textPaint)
        c.drawText("LB: Health Flask     RB: Interact / Shop / Forge     START: Pause", GameEngine.VIRTUAL_W * 0.5f, 182f, textPaint)

        textPaint.textSize = 10f
        textPaint.color = 0xFF4ADE80.toInt()
        val prompt = if (input.controllerConnected) "PRESS [A] OR [START] TO BEGIN" else "TAP [A] OR CONNECT CONTROLLER"
        c.drawText(prompt, GameEngine.VIRTUAL_W * 0.5f, 226f, textPaint)

        textPaint.textSize = 7f
        textPaint.color = 0xFF38BDF8.toInt()
        c.drawText(
            "BANKED CELLS: ${engine.progression.bankedCells}   |   RUNS: ${engine.progression.totalRuns}   |   VICTORIES: ${engine.progression.victories}",
            GameEngine.VIRTUAL_W * 0.5f,
            248f,
            textPaint
        )
        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawPauseOverlay(c: Canvas, engine: GameEngine) {
        drawMenuBox(c, "PAUSED")
        val items = listOf(
            "RESUME RUN",
            "AUDIO: ${if (engine.progression.audioMuted) "MUTED" else "ON"}",
            "TOUCH HUD: ${when (engine.showTouchControlsOverride) { null -> "AUTO"; true -> "ON"; false -> "OFF" }}",
            "RESTART RUN"
        )
        drawMenuList(c, items, engine.pauseSelection, 108f)
    }

    private fun drawShopOverlay(c: Canvas, engine: GameEngine) {
        drawMenuBox(c, "MERCHANT SHOP  (GOLD: ${engine.player.gold}G)")
        val items = engine.shopOffers.map { offer ->
            if (offer.sold) "--- SOLD ---"
            else if (offer.isFoodHeal) "ROAST MEAT (+50% HP)  -  ${offer.price}G"
            else "${offer.weapon!!.displayName} (${offer.weapon.def.shortName})  -  ${offer.price}G"
        }
        drawMenuList(c, items, engine.shopSelection, 104f)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = 0xFF94A3B8.toInt()
        c.drawText("[A] BUY   |   [B] CLOSE", GameEngine.VIRTUAL_W * 0.5f, 196f, textPaint)
        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawForgeOverlay(c: Canvas, engine: GameEngine) {
        drawMenuBox(c, "CELL FORGE  (CELLS: ${engine.progression.bankedCells})")
        val items = ForgeUpgrade.entries.map { up ->
            val lvl = engine.progression.levelOf(up)
            if (lvl >= up.maxLevel) "${up.title}  [MAX]"
            else "${up.title} Lv.$lvl/${up.maxLevel}  -  ${up.costForLevel(lvl)} CELLS"
        }
        drawMenuList(c, items, engine.forgeSelection, 104f)
        val desc = ForgeUpgrade.entries[engine.forgeSelection].description
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = 0xFF38BDF8.toInt()
        c.drawText(desc, GameEngine.VIRTUAL_W * 0.5f, 182f, textPaint)
        textPaint.color = 0xFF94A3B8.toInt()
        c.drawText("[A] UPGRADE   |   [B] CLOSE", GameEngine.VIRTUAL_W * 0.5f, 196f, textPaint)
        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawEndOverlay(c: Canvas, engine: GameEngine, victory: Boolean) {
        solidPaint.color = 0xCC090910.toInt()
        c.drawRect(0f, 0f, GameEngine.VIRTUAL_W.toFloat(), GameEngine.VIRTUAL_H.toFloat(), solidPaint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 20f
        textPaint.color = if (victory) 0xFF4ADE80.toInt() else 0xFFE63946.toInt()
        c.drawText(if (victory) "SPIRE CONQUERED!" else "YOU DIED", GameEngine.VIRTUAL_W * 0.5f, 92f, textPaint)

        textPaint.textSize = 8f
        textPaint.color = 0xFFE2E8F0.toInt()
        c.drawText("BIOME REACHED: ${engine.map.biome.name}", GameEngine.VIRTUAL_W * 0.5f, 120f, textPaint)
        c.drawText("ENEMIES SLAIN: ${engine.player.kills}   |   CELLS BANKED: ${engine.progression.bankedCells}", GameEngine.VIRTUAL_W * 0.5f, 136f, textPaint)

        textPaint.textSize = 10f
        textPaint.color = 0xFFFACC15.toInt()
        c.drawText("PRESS [A] TO START NEW RUN", GameEngine.VIRTUAL_W * 0.5f, 178f, textPaint)
        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawMenuBox(c: Canvas, title: String) {
        solidPaint.color = 0xDF0B0F19.toInt()
        c.drawRect(80f, 56f, 400f, 212f, solidPaint)
        strokePaint.color = 0xFFE63946.toInt()
        c.drawRect(80f, 56f, 400f, 212f, strokePaint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 10f
        textPaint.color = 0xFFFACC15.toInt()
        c.drawText(title, GameEngine.VIRTUAL_W * 0.5f, 78f, textPaint)
        textPaint.textSize = 7f
    }

    private fun drawMenuList(c: Canvas, items: List<String>, selected: Int, startY: Float) {
        textPaint.textAlign = Paint.Align.CENTER
        for ((idx, label) in items.withIndex()) {
            val y = startY + idx * 18f
            if (idx == selected) {
                solidPaint.color = 0xFF1E293B.toInt()
                c.drawRect(98f, y - 10f, 382f, y + 4f, solidPaint)
                textPaint.color = 0xFFFACC15.toInt()
                c.drawText("> $label <", GameEngine.VIRTUAL_W * 0.5f, y, textPaint)
            } else {
                textPaint.color = 0xFFCBD5E1.toInt()
                c.drawText(label, GameEngine.VIRTUAL_W * 0.5f, y, textPaint)
            }
        }
        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawCenteredPill(c: Canvas, cx: Float, y: Float, text: String, color: Int) {
        textPaint.textSize = 7f
        val tw = textPaint.measureText(text)
        solidPaint.color = 0xCC0B0F19.toInt()
        c.drawRect(cx - tw * 0.5f - 4f, y - 8f, cx + tw * 0.5f + 4f, y + 3f, solidPaint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = color
        c.drawText(text, cx, y, textPaint)
        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawSprite(c: Canvas, bmp: Bitmap, x: Float, y: Float, w: Float, h: Float, facing: Int) {
        c.save()
        if (facing < 0) {
            c.scale(-1f, 1f, x + w * 0.5f, y + h * 0.5f)
        }
        tmpRectF.set(x, y, x + w, y + h)
        c.drawBitmap(bmp, null, tmpRectF, blitPaint)
        c.restore()
    }
}
