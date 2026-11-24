package com.crimsoncell.enemy

import com.crimsoncell.combat.CombatEventListener
import com.crimsoncell.combat.CombatSystem
import com.crimsoncell.combat.Projectile
import com.crimsoncell.combat.ProjectileKind
import com.crimsoncell.entity.ParticleSystem
import com.crimsoncell.entity.Player
import com.crimsoncell.world.LevelMap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sign
import kotlin.random.Random

/**
 * Distinct AI behaviors for all 10 enemy archetypes and 2 multi-phase bosses.
 * Every enemy telegraphs its attack with a visible yellow `!` before striking.
 */
object EnemyAI {

    fun updateAll(
        dt: Float,
        map: LevelMap,
        player: Player,
        combat: CombatSystem,
        particles: ParticleSystem,
        events: CombatEventListener,
        rng: Random = Random.Default
    ) {
        for (enemy in map.enemies) {
            if (!enemy.isAlive) continue
            updateEnemy(enemy, dt, map, player, combat, particles, events, rng)
        }
    }

    private fun updateEnemy(
        e: Enemy,
        dt: Float,
        map: LevelMap,
        player: Player,
        combat: CombatSystem,
        particles: ParticleSystem,
        events: CombatEventListener,
        rng: Random
    ) {
        e.animTime += dt
        e.hurtFlashTimer = (e.hurtFlashTimer - dt).coerceAtLeast(0f)
        e.attackCooldown = (e.attackCooldown - dt).coerceAtLeast(0f)

        // Frozen stops movement and AI completely
        if (e.isFrozen) {
            e.vx *= 0.8f
            applyPhysics(e, dt, map)
            return
        }

        // Stunned state
        if (e.isStunned) {
            e.stateTimer -= dt
            e.vx *= 0.85f
            if (e.stateTimer <= 0f) e.state = EnemyAiState.CHASE
            applyPhysics(e, dt, map)
            return
        }

        val dx = player.centerX - e.centerX
        val dy = player.centerY - e.centerY
        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        val aggroRange = if (e.type.isBoss) 340f else 210f

        when (e.state) {
            EnemyAiState.PATROL -> {
                if (dist < aggroRange) {
                    e.state = EnemyAiState.CHASE
                } else {
                    e.stateTimer -= dt
                    if (e.stateTimer <= 0f) {
                        e.facing = -e.facing
                        e.stateTimer = 1.4f + rng.nextFloat() * 1.4f
                    }
                    if (!e.isRooted) {
                        e.vx = e.facing * e.type.moveSpeed * 0.45f
                        if (e.type.isFlying) {
                            e.vy = sin(e.animTime * 4f) * 28f
                        } else if (isLedgeAhead(e, map)) {
                            e.facing = -e.facing
                        }
                    }
                }
            }

            EnemyAiState.CHASE -> {
                if (dist > aggroRange * 1.4f && !e.type.isBoss) {
                    e.state = EnemyAiState.PATROL
                    return
                }
                if (abs(dx) > 4f) e.facing = if (dx > 0f) 1 else -1

                val desiredRange = attackRangeFor(e.type)
                if (dist <= desiredRange && e.attackCooldown <= 0f) {
                    // Begin telegraphed attack!
                    e.state = EnemyAiState.TELEGRAPH
                    e.attackHasFired = false
                    e.stateTimer = telegraphTimeFor(e)
                    e.vx *= 0.25f
                } else if (!e.isRooted) {
                    if (e.type.isFlying) {
                        val nx = dx / dist.coerceAtLeast(1f)
                        val ny = (dy - 14f) / dist.coerceAtLeast(1f)
                        e.vx = nx * e.type.moveSpeed
                        e.vy = ny * e.type.moveSpeed
                    } else if (e.type == EnemyType.BONE_ARCHER && dist < 68f) {
                        // Archer kites backwards
                        e.vx = -e.facing * e.type.moveSpeed * 0.8f
                    } else {
                        val speedMult = if (e.bossPhase == 2) 1.28f else 1f
                        e.vx = e.facing * e.type.moveSpeed * speedMult
                        // Jump over small steps or gaps when chasing
                        if (e.onGround && isWallAhead(e, map)) {
                            e.vy = -240f
                        }
                    }
                } else {
                    e.vx = 0f
                }
            }

            EnemyAiState.TELEGRAPH -> {
                e.vx *= 0.85f
                if (e.type.isFlying) e.vy *= 0.85f
                e.stateTimer -= dt
                if (e.stateTimer <= 0f) {
                    e.state = EnemyAiState.ATTACK
                    e.stateTimer = attackDurationFor(e.type)
                    e.attackHasFired = false
                }
            }

            EnemyAiState.ATTACK -> {
                if (!e.attackHasFired) {
                    e.attackHasFired = true
                    performEnemyAttack(e, player, combat, particles, events, rng)
                }
                e.stateTimer -= dt
                if (e.stateTimer <= 0f) {
                    e.state = EnemyAiState.COOLDOWN
                    e.stateTimer = if (e.bossPhase == 2) 0.45f else 0.72f
                    e.attackCooldown = cooldownFor(e)
                    e.attackPatternIndex = (e.attackPatternIndex + 1) % 3
                }
            }

            EnemyAiState.COOLDOWN -> {
                e.vx *= 0.85f
                e.stateTimer -= dt
                if (e.stateTimer <= 0f) {
                    e.state = EnemyAiState.CHASE
                }
            }

            EnemyAiState.STUNNED -> {
                e.state = EnemyAiState.CHASE
            }
        }

        applyPhysics(e, dt, map)
    }

    private fun attackRangeFor(type: EnemyType): Float = when (type) {
        EnemyType.UNDEAD_RUNNER -> 38f
        EnemyType.BONE_ARCHER -> 175f
        EnemyType.SHIELDBEARER -> 32f
        EnemyType.GRENADIER -> 150f
        EnemyType.CRIMSON_BAT -> 75f
        EnemyType.INQUISITOR -> 195f
        EnemyType.DARK_SLASHER -> 44f
        EnemyType.TOXIC_BOMBER -> 40f
        EnemyType.GOLEM_BRUISER -> 54f
        EnemyType.PHANTOM_ASSASSIN -> 130f
        EnemyType.BOSS_IRON_WARDEN -> 130f
        EnemyType.BOSS_CRIMSON_REAPER -> 150f
    }

    private fun telegraphTimeFor(e: Enemy): Float {
        val base = when (e.type) {
            EnemyType.DARK_SLASHER, EnemyType.PHANTOM_ASSASSIN -> 0.34f
            EnemyType.GOLEM_BRUISER -> 0.56f
            EnemyType.BOSS_IRON_WARDEN -> 0.48f
            EnemyType.BOSS_CRIMSON_REAPER -> 0.36f
            else -> 0.42f
        }
        return if (e.bossPhase == 2) base * 0.78f else base
    }

    private fun attackDurationFor(type: EnemyType): Float = when (type) {
        EnemyType.DARK_SLASHER, EnemyType.BOSS_CRIMSON_REAPER -> 0.28f
        EnemyType.GOLEM_BRUISER, EnemyType.BOSS_IRON_WARDEN -> 0.36f
        else -> 0.24f
    }

    private fun cooldownFor(e: Enemy): Float {
        val base = when (e.type) {
            EnemyType.BONE_ARCHER, EnemyType.INQUISITOR, EnemyType.GRENADIER -> 1.65f
            EnemyType.DARK_SLASHER -> 0.85f
            EnemyType.BOSS_IRON_WARDEN -> 1.15f
            EnemyType.BOSS_CRIMSON_REAPER -> 0.85f
            else -> 1.15f
        }
        return if (e.bossPhase == 2) base * 0.72f else base
    }

    private fun performEnemyAttack(
        e: Enemy,
        player: Player,
        combat: CombatSystem,
        particles: ParticleSystem,
        events: CombatEventListener,
        rng: Random
    ) {
        val dx = player.centerX - e.centerX
        val dy = player.centerY - e.centerY
        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)

        when (e.type) {
            EnemyType.UNDEAD_RUNNER, EnemyType.SHIELDBEARER, EnemyType.DARK_SLASHER -> {
                e.vx = e.facing * (if (e.type == EnemyType.DARK_SLASHER) 230f else 170f)
                particles.spawnSlashArc(e.centerX + e.facing * 12f, e.centerY, e.facing, 26f, e.type.accentColor)
                tryHitPlayerMelee(e, player, 34f, combat, particles, events, rng)
            }

            EnemyType.CRIMSON_BAT -> {
                e.vx = (dx / dist) * 230f
                e.vy = (dy / dist) * 230f
                tryHitPlayerMelee(e, player, 32f, combat, particles, events, rng)
            }

            EnemyType.BONE_ARCHER -> {
                combat.projectiles.add(
                    Projectile(
                        x = e.centerX + e.facing * 8f,
                        y = e.centerY - 2f,
                        vx = (dx / dist) * 255f,
                        vy = (dy / dist) * 255f,
                        fromPlayer = false,
                        kind = ProjectileKind.ENEMY_ARROW,
                        damage = e.damage,
                        color = 0xFFE2E8F0.toInt(),
                        life = 1.8f
                    )
                )
                events.onSfx("bow")
            }

            EnemyType.GRENADIER -> {
                combat.projectiles.add(
                    Projectile(
                        x = e.centerX + e.facing * 6f,
                        y = e.centerY - 4f,
                        vx = e.facing * (120f + abs(dx) * 0.65f).coerceAtMost(240f),
                        vy = -195f,
                        fromPlayer = false,
                        kind = ProjectileKind.ENEMY_BOMB,
                        damage = e.damage,
                        color = 0xFFEF4444.toInt(),
                        life = 1.6f,
                        gravity = 450f,
                        explodeRadius = 38f
                    )
                )
                events.onSfx("throw")
            }

            EnemyType.INQUISITOR -> {
                combat.projectiles.add(
                    Projectile(
                        x = e.centerX,
                        y = e.centerY - 4f,
                        vx = (dx / dist) * 145f,
                        vy = (dy / dist) * 145f,
                        fromPlayer = false,
                        kind = ProjectileKind.ENEMY_ORB,
                        damage = e.damage,
                        color = 0xFFE879F9.toInt(),
                        life = 2.6f,
                        passThroughWalls = true
                    )
                )
                events.onSfx("phaser")
            }

            EnemyType.TOXIC_BOMBER -> {
                // Self-detonates near player
                e.hp = 0f
                tryHitPlayerMelee(e, player, 46f, combat, particles, events, rng)
                combat.damageEnemy(e, 1f, false, 0f, null, 0f, player, particles, events, rng)
            }

            EnemyType.GOLEM_BRUISER -> {
                events.onSfx("slam")
                events.onScreenShake(5f)
                particles.spawnBurst(e.centerX, e.bottomY, 18, 0xFFF97316.toInt(), 60f, 170f, 260f, 3f, 0.4f, rng)
                tryHitPlayerMelee(e, player, 56f, combat, particles, events, rng)
            }

            EnemyType.PHANTOM_ASSASSIN -> {
                // Blink behind player and slash!
                particles.spawnBurst(e.centerX, e.centerY, 10, 0xFF38BDF8.toInt(), 40f, 120f, 60f, 2f, 0.3f, rng)
                e.x = player.centerX - player.facing * 20f
                e.y = player.y
                e.facing = player.facing
                particles.spawnSlashArc(e.centerX + e.facing * 10f, e.centerY, e.facing, 28f, 0xFF38BDF8.toInt())
                tryHitPlayerMelee(e, player, 36f, combat, particles, events, rng)
            }

            EnemyType.BOSS_IRON_WARDEN -> {
                when (e.attackPatternIndex) {
                    0 -> {
                        // Leap Slam
                        e.vx = e.facing * 220f
                        e.vy = -210f
                        events.onSfx("slam")
                        events.onScreenShake(6f)
                        tryHitPlayerMelee(e, player, 62f, combat, particles, events, rng)
                    }
                    1 -> {
                        // Twin Flame Wave across the arena
                        events.onSfx("fire")
                        events.onScreenShake(4.5f)
                        for (dir in listOf(-1, 1)) {
                            combat.projectiles.add(
                                Projectile(
                                    x = e.centerX + dir * 14f,
                                    y = e.bottomY - 8f,
                                    vx = dir * (if (e.bossPhase == 2) 235f else 185f),
                                    vy = 0f,
                                    fromPlayer = false,
                                    kind = ProjectileKind.ENEMY_FLAME,
                                    damage = e.damage * 0.9f,
                                    color = 0xFFF97316.toInt(),
                                    life = 1.6f
                                )
                            )
                        }
                    }
                    else -> {
                        // Heavy cleave + bomb burst
                        particles.spawnSlashArc(e.centerX + e.facing * 18f, e.centerY, e.facing, 44f, 0xFFF97316.toInt(), isHeavy = true)
                        tryHitPlayerMelee(e, player, 54f, combat, particles, events, rng)
                        if (e.bossPhase == 2) {
                            for (ang in listOf(-2.2f, -1.5f, -0.9f)) {
                                combat.projectiles.add(
                                    Projectile(
                                        x = e.centerX,
                                        y = e.centerY,
                                        vx = cos(ang) * 160f,
                                        vy = sin(ang) * 190f,
                                        fromPlayer = false,
                                        kind = ProjectileKind.ENEMY_BOMB,
                                        damage = e.damage * 0.75f,
                                        color = 0xFFEF4444.toInt(),
                                        life = 1.4f,
                                        gravity = 420f,
                                        explodeRadius = 34f
                                    )
                                )
                            }
                        }
                    }
                }
            }

            EnemyType.BOSS_CRIMSON_REAPER -> {
                when (e.attackPatternIndex) {
                    0 -> {
                        // High-speed crimson dash slash
                        e.vx = e.facing * 320f
                        particles.spawnAfterimage(e.x, e.y, e.facing, 0xFFFF2E63.toInt())
                        particles.spawnSlashArc(e.centerX + e.facing * 18f, e.centerY, e.facing, 46f, 0xFFFF2E63.toInt(), isHeavy = true)
                        events.onSfx("heavy_slash")
                        tryHitPlayerMelee(e, player, 56f, combat, particles, events, rng)
                    }
                    1 -> {
                        // Fan of crimson daggers
                        val count = if (e.bossPhase == 2) 5 else 3
                        val baseAng = kotlin.math.atan2(dy.toDouble(), dx.toDouble()).toFloat()
                        for (i in 0 until count) {
                            val offset = (i - count / 2) * 0.22f
                            val ang = baseAng + offset
                            combat.projectiles.add(
                                Projectile(
                                    x = e.centerX,
                                    y = e.centerY,
                                    vx = cos(ang) * 260f,
                                    vy = sin(ang) * 260f,
                                    fromPlayer = false,
                                    kind = ProjectileKind.ENEMY_DAGGER,
                                    damage = e.damage * 0.75f,
                                    color = 0xFFFF2E63.toInt(),
                                    life = 1.5f
                                )
                            )
                        }
                        events.onSfx("bow")
                    }
                    else -> {
                        // Shadow blink strike behind player!
                        particles.spawnBurst(e.centerX, e.centerY, 18, 0xFFFF2E63.toInt(), 50f, 160f, 50f, 2.5f, 0.35f, rng)
                        e.x = player.centerX - player.facing * 26f
                        e.y = player.y - 6f
                        e.facing = player.facing
                        particles.spawnSlashArc(e.centerX + e.facing * 16f, e.centerY, e.facing, 42f, 0xFFFF2E63.toInt(), isHeavy = true)
                        events.onSfx("phaser")
                        tryHitPlayerMelee(e, player, 52f, combat, particles, events, rng)
                    }
                }
            }
        }
    }

    private fun tryHitPlayerMelee(
        e: Enemy,
        player: Player,
        reach: Float,
        combat: CombatSystem,
        particles: ParticleSystem,
        events: CombatEventListener,
        rng: Random
    ) {
        val dist = hypot((player.centerX - e.centerX).toDouble(), (player.centerY - e.centerY).toDouble()).toFloat()
        if (dist > reach) return

        // Check player Parry first!
        if (combat.tryParryMeleeAttack(e, player, particles, events, rng)) return

        if (!player.isInvulnerable) {
            val kb = sign(player.centerX - e.centerX) * 160f
            val dealt = player.takeDamage(e.damage, kb)
            if (dealt > 0f) {
                events.onSfx("hurt")
                events.onScreenShake(4.5f)
                events.onHaptic(35, 190)
                particles.spawnText(player.centerX, player.y - 6f, "-${dealt.toInt()}", 0xFFEF4444.toInt())
            }
        }
    }

    private fun applyPhysics(e: Enemy, dt: Float, map: LevelMap) {
        if (!e.type.isFlying) {
            e.vy = (e.vy + 820f * dt).coerceAtMost(350f)
        }

        // Horizontal
        e.x += e.vx * dt
        val topT = map.worldToTile(e.y + 2f)
        val botT = map.worldToTile(e.bottomY - 2f)
        if (e.vx > 0f) {
            val rightT = map.worldToTile(e.x + e.width)
            for (ty in topT..botT) {
                if (map.isSolid(rightT, ty)) {
                    e.x = rightT * LevelMap.TILE_SIZE - e.width - 0.01f
                    e.vx = 0f
                    break
                }
            }
        } else if (e.vx < 0f) {
            val leftT = map.worldToTile(e.x)
            for (ty in topT..botT) {
                if (map.isSolid(leftT, ty)) {
                    e.x = (leftT + 1) * LevelMap.TILE_SIZE + 0.01f
                    e.vx = 0f
                    break
                }
            }
        }

        // Vertical
        val prevBot = e.bottomY
        e.y += e.vy * dt
        e.onGround = false
        val leftT = map.worldToTile(e.x + 1f)
        val rightT = map.worldToTile(e.x + e.width - 1f)
        if (e.vy >= 0f) {
            val botTile = map.worldToTile(e.bottomY)
            for (tx in leftT..rightT) {
                val tileTop = botTile * LevelMap.TILE_SIZE.toFloat()
                if (map.isSolid(tx, botTile) || (map.isOneWay(tx, botTile) && prevBot <= tileTop + 4f)) {
                    e.y = tileTop - e.height
                    e.vy = 0f
                    e.onGround = true
                    break
                }
            }
        } else {
            val topTile = map.worldToTile(e.y)
            for (tx in leftT..rightT) {
                if (map.isSolid(tx, topTile)) {
                    e.y = (topTile + 1) * LevelMap.TILE_SIZE + 0.01f
                    e.vy = 0f
                    break
                }
            }
        }
    }

    private fun isLedgeAhead(e: Enemy, map: LevelMap): Boolean {
        val probeX = if (e.facing > 0) e.x + e.width + 4f else e.x - 4f
        val tx = map.worldToTile(probeX)
        val ty = map.worldToTile(e.bottomY + 4f)
        return !map.isSolid(tx, ty) && !map.isOneWay(tx, ty)
    }

    private fun isWallAhead(e: Enemy, map: LevelMap): Boolean {
        val probeX = if (e.facing > 0) e.x + e.width + 3f else e.x - 3f
        return map.isSolid(map.worldToTile(probeX), map.worldToTile(e.centerY))
    }
}
