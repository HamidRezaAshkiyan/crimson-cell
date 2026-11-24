package com.crimsoncell.entity

import com.crimsoncell.combat.ItemCategory
import com.crimsoncell.combat.StatColor
import com.crimsoncell.combat.WeaponCatalog
import com.crimsoncell.combat.WeaponId
import com.crimsoncell.combat.WeaponInstance
import com.crimsoncell.input.InputState
import com.crimsoncell.world.LevelMap
import kotlin.math.abs
import kotlin.math.sign

enum class AttackPhase {
    NONE, WINDUP, ACTIVE, RECOVERY
}

class Player(
    var x: Float = 48f,
    var y: Float = 48f
) {
    val width = 12f
    val height = 20f

    var vx: Float = 0f
    var vy: Float = 0f
    var facing: Int = 1 // 1 = right, -1 = left

    // Ground & movement state
    var onGround: Boolean = false
    var touchingWall: Int = 0 // -1 left wall, 1 right wall, 0 none
    var coyoteTimer: Float = 0f
    var jumpsRemaining: Int = 2
    var dropThroughTimer: Float = 0f
    var isDiveSlamming: Boolean = false
    var justLandedDiveSlam: Boolean = false

    // Dodge roll
    var rollTimer: Float = 0f
    var rollCooldown: Float = 0f
    var invulnTimer: Float = 0f
    var rollAfterimageTimer: Float = 0f
    val isRolling: Boolean get() = rollTimer > 0f
    val isInvulnerable: Boolean get() = invulnTimer > 0f || rollTimer > 0.06f

    // Health & Rally mechanic (Dead Cells orange recoverable bar)
    var maxHp: Float = 120f
    var hp: Float = 120f
    var rallyHp: Float = 120f
    var rallyTimer: Float = 0f
    var hurtFlashTimer: Float = 0f
    var flaskCharges: Int = 2
    var maxFlaskCharges: Int = 2

    // Scrolls & currencies
    var brutalityScrolls: Int = 1
    var tacticsScrolls: Int = 1
    var survivalScrolls: Int = 1
    var gold: Int = 0
    var runCells: Int = 0
    var kills: Int = 0

    // Gear slots
    var weapon1: WeaponInstance = WeaponCatalog.createInstance(WeaponId.RUSTY_BLADE, 1, withAffix = false)
    var weapon2: WeaponInstance = WeaponCatalog.createInstance(WeaponId.QUICK_BOW, 1, withAffix = false)
    var skill1: WeaponInstance? = WeaponCatalog.createInstance(WeaponId.CLUSTER_GRENADE, 1, withAffix = false)
    var skill2: WeaponInstance? = WeaponCatalog.createInstance(WeaponId.FROST_NOVA, 1, withAffix = false)

    // Combat & attack state
    var attackPhase: AttackPhase = AttackPhase.NONE
    var attackTimer: Float = 0f
    var activeWeaponSlot: Int = 1
    var comboIndex: Int = 0
    var comboResetTimer: Float = 0f
    var attackHitConsumed: Boolean = false
    var phaserBuffTimer: Float = 0f

    // Shield block / parry
    var isBlocking: Boolean = false
    var parryWindowTimer: Float = 0f

    // Animation & Verlet scarf (6 nodes)
    var animTime: Float = 0f
    val scarfX = FloatArray(6) { x }
    val scarfY = FloatArray(6) { y }

    val centerX: Float get() = x + width * 0.5f
    val centerY: Float get() = y + height * 0.5f
    val bottomY: Float get() = y + height
    val isAlive: Boolean get() = hp > 0f

    fun resetForNewRun(
        spawnX: Float,
        spawnY: Float,
        startingTier: Int,
        startingFlasks: Int,
        startingGold: Int,
        bonusMaxHp: Float
    ) {
        x = spawnX
        y = spawnY
        vx = 0f
        vy = 0f
        facing = 1
        onGround = false
        touchingWall = 0
        coyoteTimer = 0f
        jumpsRemaining = 2
        dropThroughTimer = 0f
        isDiveSlamming = false
        justLandedDiveSlam = false
        rollTimer = 0f
        rollCooldown = 0f
        invulnTimer = 0f
        maxHp = 120f + bonusMaxHp
        hp = maxHp
        rallyHp = maxHp
        rallyTimer = 0f
        hurtFlashTimer = 0f
        maxFlaskCharges = startingFlasks
        flaskCharges = startingFlasks
        brutalityScrolls = 1
        tacticsScrolls = 1
        survivalScrolls = 1
        gold = startingGold
        runCells = 0
        kills = 0
        weapon1 = WeaponCatalog.createInstance(WeaponId.RUSTY_BLADE, startingTier, withAffix = startingTier >= 2)
        weapon2 = WeaponCatalog.createInstance(WeaponId.SPIKED_SHIELD, startingTier, withAffix = startingTier >= 2)
        skill1 = WeaponCatalog.createInstance(WeaponId.CLUSTER_GRENADE, startingTier, withAffix = startingTier >= 2)
        skill2 = WeaponCatalog.createInstance(WeaponId.PHASER, startingTier, withAffix = startingTier >= 2)
        attackPhase = AttackPhase.NONE
        attackTimer = 0f
        comboIndex = 0
        comboResetTimer = 0f
        phaserBuffTimer = 0f
        isBlocking = false
        parryWindowTimer = 0f
        resetScarf()
    }

    fun resetScarf() {
        for (i in scarfX.indices) {
            scarfX[i] = centerX - facing * i * 2.2f
            scarfY[i] = y + 5f
        }
    }

    fun scrollMultiplierFor(color: StatColor): Float {
        val count = when (color) {
            StatColor.BRUTALITY -> brutalityScrolls
            StatColor.TACTICS -> tacticsScrolls
            StatColor.SURVIVAL -> survivalScrolls
        }
        return 1f + (count - 1) * 0.16f
    }

    fun applyScroll(color: StatColor) {
        when (color) {
            StatColor.BRUTALITY -> brutalityScrolls++
            StatColor.TACTICS -> tacticsScrolls++
            StatColor.SURVIVAL -> survivalScrolls++
        }
        val hpGain = when (color) {
            StatColor.SURVIVAL -> maxHp * 0.18f
            StatColor.BRUTALITY -> maxHp * 0.12f
            StatColor.TACTICS -> maxHp * 0.08f
        }
        maxHp += hpGain
        hp = (hp + hpGain).coerceAtMost(maxHp)
        rallyHp = hp
    }

    fun useFlask(): Boolean {
        if (flaskCharges <= 0 || hp >= maxHp - 1f) return false
        flaskCharges--
        val heal = maxHp * 0.65f
        hp = (hp + heal).coerceAtMost(maxHp)
        rallyHp = hp
        invulnTimer = maxOf(invulnTimer, 0.4f)
        return true
    }

    fun takeDamage(rawDamage: Float, knockbackX: Float): Float {
        if (isInvulnerable || !isAlive) return 0f
        var dmg = rawDamage
        if (isBlocking) {
            dmg *= 0.25f
        }
        hp = (hp - dmg).coerceAtLeast(0f)
        rallyTimer = RALLY_DURATION
        hurtFlashTimer = 0.22f
        invulnTimer = 0.45f
        vx = knockbackX * 0.65f
        vy = -120f
        isDiveSlamming = false
        return dmg
    }

    fun recoverRally(damageDealt: Float) {
        if (rallyHp > hp && rallyTimer > 0f) {
            val heal = (damageDealt * 0.35f).coerceAtMost(rallyHp - hp)
            hp = (hp + heal).coerceAtMost(maxHp)
        }
    }

    fun activeWeapon(): WeaponInstance = if (activeWeaponSlot == 1) weapon1 else weapon2

    fun update(dt: Float, input: InputState, map: LevelMap, particles: ParticleSystem) {
        animTime += dt
        justLandedDiveSlam = false

        // Cooldowns & timers
        rollCooldown = (rollCooldown - dt).coerceAtLeast(0f)
        invulnTimer = (invulnTimer - dt).coerceAtLeast(0f)
        hurtFlashTimer = (hurtFlashTimer - dt).coerceAtLeast(0f)
        dropThroughTimer = (dropThroughTimer - dt).coerceAtLeast(0f)
        comboResetTimer = (comboResetTimer - dt).coerceAtLeast(0f)
        phaserBuffTimer = (phaserBuffTimer - dt).coerceAtLeast(0f)
        parryWindowTimer = (parryWindowTimer - dt).coerceAtLeast(0f)

        skill1?.let { it.cooldownRemaining = (it.cooldownRemaining - dt).coerceAtLeast(0f) }
        skill2?.let { it.cooldownRemaining = (it.cooldownRemaining - dt).coerceAtLeast(0f) }

        if (comboResetTimer <= 0f && attackPhase == AttackPhase.NONE) {
            comboIndex = 0
        }

        // Rally decay
        if (rallyTimer > 0f) {
            rallyTimer -= dt
        } else if (rallyHp > hp) {
            rallyHp = (rallyHp - maxHp * 0.6f * dt).coerceAtLeast(hp)
        } else {
            rallyHp = hp
        }

        // Facing update when not locked in mid-roll
        if (!isRolling && abs(input.moveX) > 0.15f && attackPhase != AttackPhase.ACTIVE) {
            facing = if (input.moveX > 0f) 1 else -1
        }

        // Roll input (can cancel attack recovery or windup!)
        if (input.consumeRollBuffer() && rollCooldown <= 0f && !isRolling) {
            startRoll()
            particles.spawnAfterimage(x, y, facing)
        }

        if (isRolling) {
            rollTimer -= dt
            rollAfterimageTimer -= dt
            if (rollAfterimageTimer <= 0f) {
                rollAfterimageTimer = 0.055f
                particles.spawnAfterimage(x, y, facing)
            }
            vx = facing * ROLL_SPEED
            vy = minOf(vy, 40f)
        } else {
            // Horizontal movement
            val targetSpeed = input.moveX * RUN_SPEED
            val accel = if (onGround) GROUND_ACCEL else AIR_ACCEL
            val decel = if (onGround) GROUND_DECEL else AIR_DECEL
            val slowFactor = if (attackPhase != AttackPhase.NONE) 0.35f else 1f
            val desired = targetSpeed * slowFactor

            if (abs(desired) > 1f) {
                val diff = desired - vx
                val step = accel * dt
                vx = if (abs(diff) <= step) desired else vx + sign(diff) * step
            } else {
                val step = decel * dt
                vx = if (abs(vx) <= step) 0f else vx - sign(vx) * step
            }
        }

        // Shield block state (Weapon 2 if it's a shield)
        isBlocking = !isRolling &&
            weapon2.def.category == ItemCategory.SHIELD &&
            input.weapon2Held &&
            attackPhase == AttackPhase.NONE

        // Coyote time
        if (onGround) {
            coyoteTimer = COYOTE_TIME
            jumpsRemaining = 2
        } else {
            coyoteTimer = (coyoteTimer - dt).coerceAtLeast(0f)
        }

        // Jump / Drop-through / Wall-jump / Dive Slam
        if (!isRolling && input.consumeJumpBuffer()) {
            if (input.moveY > 0.55f && onGround && map.isStandingOnOneWay(x, bottomY, width)) {
                // Drop through one-way platform
                dropThroughTimer = 0.22f
                y += 2f
                onGround = false
                vy = 60f
            } else if (input.moveY > 0.55f && !onGround && !isDiveSlamming) {
                // Dive slam!
                isDiveSlamming = true
                vy = DIVE_SLAM_SPEED
                vx *= 0.2f
                attackPhase = AttackPhase.NONE
            } else if (coyoteTimer > 0f) {
                vy = -JUMP_VELOCITY
                onGround = false
                coyoteTimer = 0f
                jumpsRemaining = 1
                particles.spawnBurst(centerX, bottomY, 5, 0xFF94A3B8.toInt(), 20f, 70f, 180f, 2f, 0.22f)
            } else if (touchingWall != 0 && !onGround) {
                // Wall jump
                vy = -JUMP_VELOCITY * 0.94f
                vx = -touchingWall * RUN_SPEED * 1.15f
                facing = -touchingWall
                jumpsRemaining = 1
                particles.spawnBurst(
                    if (touchingWall > 0) x + width else x,
                    centerY, 6, 0xFFCBD5E1.toInt(), 30f, 80f, 200f, 2f, 0.24f
                )
            } else if (jumpsRemaining > 0) {
                vy = -DOUBLE_JUMP_VELOCITY
                jumpsRemaining--
                isDiveSlamming = false
                particles.spawnBurst(centerX, bottomY, 7, 0xFFE2E8F0.toInt(), 30f, 95f, 120f, 2f, 0.25f)
            }
        }

        // Variable jump height cut
        if (!input.jumpHeld && vy < -120f && !isDiveSlamming) {
            vy *= 0.72f
        }

        // Gravity & wall slide
        val grav = if (isDiveSlamming) GRAVITY * 1.6f else GRAVITY
        vy = (vy + grav * dt).coerceAtMost(if (isDiveSlamming) DIVE_SLAM_SPEED else MAX_FALL_SPEED)

        // Wall slide cap
        if (!onGround && touchingWall != 0 && vy > WALL_SLIDE_SPEED && !isDiveSlamming) {
            if ((touchingWall < 0 && input.moveX < -0.2f) || (touchingWall > 0 && input.moveX > 0.2f)) {
                vy = WALL_SLIDE_SPEED
            }
        }

        // Advance attack state machine
        updateAttackState(dt)

        // Move & collide with tilemap
        moveAndCollide(dt, map, particles)

        // Update trailing scarf
        updateScarf(dt)
    }

    private fun startRoll() {
        rollTimer = ROLL_DURATION
        rollCooldown = ROLL_COOLDOWN
        invulnTimer = maxOf(invulnTimer, ROLL_IFRAMES)
        attackPhase = AttackPhase.NONE
        isDiveSlamming = false
        isBlocking = false
    }

    fun tryStartWeaponAttack(slot: Int): Boolean {
        if (isRolling || isDiveSlamming) return false
        if (attackPhase == AttackPhase.WINDUP || attackPhase == AttackPhase.ACTIVE) return false

        val inst = if (slot == 1) weapon1 else weapon2
        activeWeaponSlot = slot

        // Shield tap initiates Parry window
        if (inst.def.category == ItemCategory.SHIELD) {
            parryWindowTimer = 0.22f
            attackPhase = AttackPhase.ACTIVE
            attackTimer = inst.def.activeSec
            attackHitConsumed = false
            return true
        }

        val speed = inst.speedMultiplier()
        attackPhase = AttackPhase.WINDUP
        attackTimer = inst.def.windupSec / speed
        attackHitConsumed = false
        comboResetTimer = 0.75f
        return true
    }

    private fun updateAttackState(dt: Float) {
        if (attackPhase == AttackPhase.NONE) return
        attackTimer -= dt
        val inst = activeWeapon()
        val speed = inst.speedMultiplier()

        if (attackTimer <= 0f) {
            when (attackPhase) {
                AttackPhase.WINDUP -> {
                    attackPhase = AttackPhase.ACTIVE
                    attackTimer = inst.def.activeSec / speed
                    attackHitConsumed = false
                    // Slight forward lunge on melee swings
                    if (inst.def.category == ItemCategory.MELEE) {
                        vx += facing * 65f
                    }
                }
                AttackPhase.ACTIVE -> {
                    attackPhase = AttackPhase.RECOVERY
                    attackTimer = inst.def.recoverySec / speed
                    comboIndex = (comboIndex + 1) % inst.def.comboCount.coerceAtLeast(1)
                }
                AttackPhase.RECOVERY -> {
                    attackPhase = AttackPhase.NONE
                    attackTimer = 0f
                }
                AttackPhase.NONE -> Unit
            }
        }
    }

    private fun moveAndCollide(dt: Float, map: LevelMap, particles: ParticleSystem) {
        val prevBottom = bottomY

        // Horizontal movement
        x += vx * dt
        touchingWall = 0
        if (vx > 0f) {
            val rightTile = map.worldToTile(x + width)
            val topTile = map.worldToTile(y + 1f)
            val botTile = map.worldToTile(y + height - 1f)
            for (ty in topTile..botTile) {
                if (map.isSolid(rightTile, ty)) {
                    x = rightTile * LevelMap.TILE_SIZE - width - 0.01f
                    vx = 0f
                    touchingWall = 1
                    break
                }
            }
        } else if (vx < 0f) {
            val leftTile = map.worldToTile(x)
            val topTile = map.worldToTile(y + 1f)
            val botTile = map.worldToTile(y + height - 1f)
            for (ty in topTile..botTile) {
                if (map.isSolid(leftTile, ty)) {
                    x = (leftTile + 1) * LevelMap.TILE_SIZE + 0.01f
                    vx = 0f
                    touchingWall = -1
                    break
                }
            }
        }

        // Vertical movement
        y += vy * dt
        onGround = false
        val leftTile = map.worldToTile(x + 1f)
        val rightTile = map.worldToTile(x + width - 1f)

        if (vy >= 0f) {
            val botTile = map.worldToTile(y + height)
            for (tx in leftTile..rightTile) {
                val tileTop = botTile * LevelMap.TILE_SIZE.toFloat()
                val solid = map.isSolid(tx, botTile)
                val oneWay = map.isOneWay(tx, botTile) &&
                    dropThroughTimer <= 0f &&
                    prevBottom <= tileTop + 4f &&
                    !isDiveSlamming
                if (solid || oneWay) {
                    y = tileTop - height
                    if (isDiveSlamming) {
                        isDiveSlamming = false
                        justLandedDiveSlam = true
                        particles.spawnBurst(centerX, bottomY, 16, 0xFFFACC15.toInt(), 60f, 180f, 260f, 2.5f, 0.35f)
                    }
                    vy = 0f
                    onGround = true
                    break
                }
            }
        } else {
            val topTile = map.worldToTile(y)
            for (tx in leftTile..rightTile) {
                if (map.isSolid(tx, topTile)) {
                    y = (topTile + 1) * LevelMap.TILE_SIZE + 0.01f
                    vy = 0f
                    break
                }
            }
        }
    }

    private fun updateScarf(dt: Float) {
        scarfX[0] = centerX - facing * 2f
        scarfY[0] = y + 5f
        val windX = -facing * 26f - vx * 0.14f
        for (i in 1 until scarfX.size) {
            val targetX = scarfX[i - 1] + windX * 0.11f
            val targetY = scarfY[i - 1] + kotlin.math.sin(animTime * 12f + i * 0.9f) * 1.4f + 0.5f
            scarfX[i] += (targetX - scarfX[i]) * (18f * dt).coerceAtMost(1f)
            scarfY[i] += (targetY - scarfY[i]) * (18f * dt).coerceAtMost(1f)
        }
    }

    companion object {
        const val RUN_SPEED = 165f
        const val GROUND_ACCEL = 1550f
        const val GROUND_DECEL = 1650f
        const val AIR_ACCEL = 1050f
        const val AIR_DECEL = 650f
        const val GRAVITY = 880f
        const val MAX_FALL_SPEED = 370f
        const val WALL_SLIDE_SPEED = 75f
        const val JUMP_VELOCITY = 305f
        const val DOUBLE_JUMP_VELOCITY = 275f
        const val DIVE_SLAM_SPEED = 460f
        const val COYOTE_TIME = 0.10f
        const val ROLL_SPEED = 275f
        const val ROLL_DURATION = 0.28f
        const val ROLL_IFRAMES = 0.24f
        const val ROLL_COOLDOWN = 0.42f
        const val RALLY_DURATION = 3.5f
    }
}
