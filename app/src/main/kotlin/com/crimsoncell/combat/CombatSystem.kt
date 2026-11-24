package com.crimsoncell.combat

import com.crimsoncell.enemy.Enemy
import com.crimsoncell.enemy.EnemyType
import com.crimsoncell.entity.AttackPhase
import com.crimsoncell.entity.ParticleSystem
import com.crimsoncell.entity.Player
import com.crimsoncell.world.InteractableType
import com.crimsoncell.world.LevelMap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sign
import kotlin.random.Random

enum class ProjectileKind {
    ARROW, BOLT, FIRE_BOTTLE, ICE_ARROW, KNIFE,
    GRENADE, MINI_BOMB, FLAME_WAVE,
    ENEMY_ARROW, ENEMY_BOMB, ENEMY_ORB, ENEMY_FLAME, ENEMY_DAGGER
}

data class Projectile(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val fromPlayer: Boolean,
    val kind: ProjectileKind,
    val damage: Float,
    val color: Int,
    var life: Float = 2.5f,
    val gravity: Float = 0f,
    var pierceLeft: Int = 0,
    val status: StatusType? = null,
    val statusDuration: Float = 0f,
    val isCrit: Boolean = false,
    val passThroughWalls: Boolean = false,
    val explodeRadius: Float = 0f,
    val spawnMiniBombs: Boolean = false,
    val spawnFirePool: Boolean = false,
    val hitIds: MutableSet<Int> = HashSet()
)

enum class HazardKind {
    FIRE_POOL, POISON_CLOUD, WOLF_TRAP, SAW_TURRET
}

data class GroundHazard(
    val kind: HazardKind,
    var x: Float,
    var y: Float,
    val radius: Float,
    val fromPlayer: Boolean,
    val damagePerTick: Float,
    var life: Float,
    var tickTimer: Float = 0.2f
)

interface CombatEventListener {
    fun onSfx(sfx: String)
    fun onHitstop(durationSec: Float)
    fun onScreenShake(intensity: Float)
    fun onHaptic(durationMs: Long, amplitude: Int)
}

class CombatSystem {
    val projectiles = ArrayList<Projectile>(64)
    val hazards = ArrayList<GroundHazard>(24)

    fun clear() {
        projectiles.clear()
        hazards.clear()
    }

    fun update(
        dt: Float,
        player: Player,
        map: LevelMap,
        particles: ParticleSystem,
        events: CombatEventListener,
        rng: Random = Random.Default
    ) {
        // 1. Trigger player weapon attack on ACTIVE phase
        if (player.attackPhase == AttackPhase.ACTIVE && !player.attackHitConsumed) {
            player.attackHitConsumed = true
            executeWeaponAttack(player, map, particles, events, rng)
        }

        // 2. Player Dive Slam shockwave on landing
        if (player.justLandedDiveSlam) {
            events.onSfx("slam")
            events.onScreenShake(5.5f)
            events.onHaptic(45, 200)
            for (enemy in map.enemies) {
                if (!enemy.isAlive) continue
                val dist = hypot((enemy.centerX - player.centerX).toDouble(), (enemy.centerY - player.centerY).toDouble()).toFloat()
                if (dist <= 64f) {
                    val dmg = 26f * player.scrollMultiplierFor(StatColor.SURVIVAL)
                    damageEnemy(enemy, dmg, false, sign(enemy.centerX - player.centerX) * 160f, StatusType.STUN, 1.3f, player, particles, events, rng)
                }
            }
        }

        // 3. Update status effects on all enemies
        updateEnemyStatuses(dt, player, map, particles, events, rng)

        // 4. Update projectiles
        updateProjectiles(dt, player, map, particles, events, rng)

        // 5. Update ground hazards (fire pools, poison clouds, wolf traps, saw turrets)
        updateHazards(dt, player, map, particles, events, rng)

        // 6. Player tile hazard check (spikes / acid)
        val ptx = map.worldToTile(player.centerX)
        val pty = map.worldToTile(player.bottomY - 2f)
        if (map.isHazard(ptx, pty) && !player.isInvulnerable) {
            val dealt = player.takeDamage(14f, -player.facing * 120f)
            if (dealt > 0f) {
                events.onSfx("hurt")
                events.onScreenShake(4f)
                particles.spawnText(player.centerX, player.y - 4f, "-${dealt.toInt()}", 0xFFEF4444.toInt())
            }
        }
    }

    fun activateSkill(
        slot: Int,
        player: Player,
        map: LevelMap,
        particles: ParticleSystem,
        events: CombatEventListener,
        rng: Random = Random.Default
    ): Boolean {
        val inst = (if (slot == 1) player.skill1 else player.skill2) ?: return false
        if (inst.cooldownRemaining > 0f) return false

        val def = inst.def
        inst.cooldownRemaining = def.cooldownSec
        val dmg = inst.scaledDamage(player.scrollMultiplierFor(def.statColor))

        when (def.id) {
            WeaponId.CLUSTER_GRENADE -> {
                projectiles.add(
                    Projectile(
                        x = player.centerX + player.facing * 6f,
                        y = player.centerY - 4f,
                        vx = player.facing * def.projectileSpeed,
                        vy = -170f,
                        fromPlayer = true,
                        kind = ProjectileKind.GRENADE,
                        damage = dmg,
                        color = def.colorHex,
                        life = 1.4f,
                        gravity = 480f,
                        status = StatusType.BURN,
                        statusDuration = 2.5f,
                        explodeRadius = 52f,
                        spawnMiniBombs = true
                    )
                )
                events.onSfx("throw")
            }
            WeaponId.FROST_NOVA -> {
                events.onSfx("freeze")
                events.onScreenShake(3.5f)
                particles.spawnBurst(player.centerX, player.centerY, 26, 0xFF7DD3FC.toInt(), 80f, 200f, 30f, 3f, 0.45f, rng)
                for (e in map.enemies) {
                    if (!e.isAlive) continue
                    val dist = hypot((e.centerX - player.centerX).toDouble(), (e.centerY - player.centerY).toDouble()).toFloat()
                    if (dist <= def.rangePx) {
                        damageEnemy(e, dmg, false, sign(e.centerX - player.centerX) * 90f, StatusType.FROZEN, def.statusDuration, player, particles, events, rng)
                    }
                }
            }
            WeaponId.SINEW_SLICER -> {
                hazards.add(
                    GroundHazard(
                        kind = HazardKind.SAW_TURRET,
                        x = player.centerX,
                        y = player.bottomY - 6f,
                        radius = def.rangePx,
                        fromPlayer = true,
                        damagePerTick = dmg,
                        life = 10f,
                        tickTimer = 0.25f
                    )
                )
                events.onSfx("deploy")
                particles.spawnBurst(player.centerX, player.bottomY - 4f, 10, def.colorHex, 30f, 90f, 180f, 2f, 0.3f, rng)
            }
            WeaponId.PHASER -> {
                val target = map.enemies
                    .filter { it.isAlive }
                    .minByOrNull { hypot((it.centerX - player.centerX).toDouble(), (it.centerY - player.centerY).toDouble()) }
                if (target != null && hypot((target.centerX - player.centerX).toDouble(), (target.centerY - player.centerY).toDouble()) <= def.rangePx) {
                    particles.spawnAfterimage(player.x, player.y, player.facing, 0xFFE879F9.toInt())
                    val behindX = target.centerX - target.facing * (target.width * 0.6f + 12f)
                    if (!map.isSolid(map.worldToTile(behindX), map.worldToTile(target.centerY))) {
                        player.x = behindX
                        player.y = target.y
                    }
                    player.facing = target.facing
                    player.phaserBuffTimer = 2.5f
                    player.invulnTimer = maxOf(player.invulnTimer, 0.28f)
                    damageEnemy(target, dmg, false, 0f, StatusType.ROOT, 1.0f, player, particles, events, rng)
                    particles.spawnBurst(player.centerX, player.centerY, 16, 0xFFE879F9.toInt(), 50f, 150f, 40f, 2f, 0.3f, rng)
                    events.onSfx("phaser")
                } else {
                    // Dash forward if no enemy in range
                    player.vx = player.facing * 320f
                    player.phaserBuffTimer = 2.0f
                    events.onSfx("phaser")
                }
            }
            WeaponId.FLAME_WAVE -> {
                events.onSfx("fire")
                events.onScreenShake(3f)
                for (dir in listOf(-1, 1)) {
                    projectiles.add(
                        Projectile(
                            x = player.centerX + dir * 8f,
                            y = player.bottomY - 7f,
                            vx = dir * def.projectileSpeed,
                            vy = 0f,
                            fromPlayer = true,
                            kind = ProjectileKind.FLAME_WAVE,
                            damage = dmg,
                            color = def.colorHex,
                            life = 0.75f,
                            pierceLeft = 6,
                            status = StatusType.BURN,
                            statusDuration = 3.5f
                        )
                    )
                }
            }
            WeaponId.WOLF_TRAP -> {
                hazards.add(
                    GroundHazard(
                        kind = HazardKind.WOLF_TRAP,
                        x = player.centerX + player.facing * 18f,
                        y = player.bottomY - 4f,
                        radius = 18f,
                        fromPlayer = true,
                        damagePerTick = dmg,
                        life = 14f,
                        tickTimer = 0.05f
                    )
                )
                events.onSfx("deploy")
            }
            else -> return false
        }
        return true
    }

    private fun executeWeaponAttack(
        player: Player,
        map: LevelMap,
        particles: ParticleSystem,
        events: CombatEventListener,
        rng: Random
    ) {
        val inst = player.activeWeapon()
        val def = inst.def
        val baseDmg = inst.scaledDamage(player.scrollMultiplierFor(def.statColor))

        when (def.category) {
            ItemCategory.MELEE -> {
                events.onSfx(if (def.baseDamage >= 30f) "heavy_slash" else "slash")
                particles.spawnSlashArc(
                    player.centerX + player.facing * (def.rangePx * 0.45f),
                    player.centerY,
                    player.facing,
                    def.rangePx,
                    def.colorHex,
                    isHeavy = def.baseDamage >= 30f
                )

                val minX = if (player.facing > 0) player.centerX - 4f else player.centerX - def.rangePx
                val maxX = if (player.facing > 0) player.centerX + def.rangePx else player.centerX + 4f
                val minY = player.y - 8f
                val maxY = player.bottomY + 6f

                // Break crates
                for (item in map.interactables) {
                    if (item.type == InteractableType.CRATE && !item.used) {
                        if (item.x in minX..maxX && item.y in minY..maxY) {
                            item.used = true
                            val goldGain = rng.nextInt(12, 28)
                            player.gold += goldGain
                            particles.spawnBurst(item.x, item.y, 10, 0xFFD97706.toInt(), 40f, 120f, 340f, 2f, 0.35f, rng)
                            particles.spawnText(item.x, item.y - 6f, "+${goldGain}G", 0xFFFACC15.toInt())
                            events.onSfx("coin")
                        }
                    }
                }

                // Hit enemies
                val phaserBonus = if (player.phaserBuffTimer > 0f) {
                    player.phaserBuffTimer = 0f
                    2.0f
                } else 1.0f

                var hitAny = false
                for (enemy in map.enemies) {
                    if (!enemy.isAlive) continue
                    val overlapsX = enemy.x + enemy.width >= minX && enemy.x <= maxX
                    val overlapsY = enemy.y + enemy.height >= minY && enemy.y <= maxY
                    if (!overlapsX || !overlapsY) continue

                    // Check Shieldbearer frontal block
                    if (enemy.blocksFrontalAttack(player.centerX)) {
                        events.onSfx("clang")
                        particles.spawnDirectionalSparks(enemy.centerX, enemy.centerY, -player.facing.toFloat(), 8, 0xFF38BDF8.toInt(), rng)
                        particles.spawnText(enemy.centerX, enemy.y - 6f, "BLOCKED", 0xFF94A3B8.toInt())
                        continue
                    }

                    val isCrit = evaluateMeleeCrit(def, player, enemy)
                    var finalDmg = baseDmg * phaserBonus * (if (isCrit) def.critMultiplier else 1f)
                    finalDmg *= evaluateAffixBonus(inst.affix, enemy)

                    // Frost Blade shatters frozen targets
                    if (def.id == WeaponId.FROST_BLADE && enemy.isFrozen) {
                        enemy.statusTimers.remove(StatusType.FROZEN)
                        particles.spawnBurst(enemy.centerX, enemy.centerY, 16, 0xFF7DD3FC.toInt(), 70f, 190f, 240f, 2.5f, 0.4f, rng)
                    }

                    val statusToApply = if (inst.affix == WeaponAffix.POISON_ON_HIT && def.appliesStatus == null) {
                        StatusType.POISON
                    } else def.appliesStatus

                    val statusDur = if (statusToApply == StatusType.POISON && def.appliesStatus == null) 3.5f else def.statusDuration
                    val killed = damageEnemy(enemy, finalDmg, isCrit, player.facing * def.knockback, statusToApply, statusDur, player, particles, events, rng)
                    hitAny = true

                    // Vampiric Scythe / Vampiric affix heal on kill
                    if (killed && (def.id == WeaponId.VAMPIRIC_SCYTHE || inst.affix == WeaponAffix.VAMPIRIC)) {
                        val heal = if (def.id == WeaponId.VAMPIRIC_SCYTHE) player.maxHp * 0.04f else 2f
                        player.hp = (player.hp + heal).coerceAtMost(player.maxHp)
                        particles.spawnText(player.centerX, player.y - 8f, "+${heal.toInt()} HP", 0xFF4ADE80.toInt())
                    }

                    // Lightning Whip chains to one nearby enemy
                    if (def.id == WeaponId.LIGHTNING_WHIP) {
                        val chain = map.enemies.firstOrNull {
                            it != enemy && it.isAlive &&
                                hypot((it.centerX - enemy.centerX).toDouble(), (it.centerY - enemy.centerY).toDouble()) < 72f
                        }
                        if (chain != null) {
                            damageEnemy(chain, finalDmg * 0.6f, false, 0f, StatusType.STUN, 0.35f, player, particles, events, rng)
                            particles.spawnBurst(chain.centerX, chain.centerY, 8, 0xFFFACC15.toInt(), 40f, 110f, 40f, 2f, 0.25f, rng)
                        }
                    }
                }

                if (hitAny) {
                    events.onHitstop(if (def.baseDamage >= 30f) 0.055f else 0.028f)
                    events.onScreenShake(if (def.baseDamage >= 30f) 4.2f else 2.0f)
                }
            }

            ItemCategory.RANGED -> {
                events.onSfx("bow")
                val extraPierce = if (inst.affix == WeaponAffix.PIERCE_TARGET) 1 else 0
                when (def.id) {
                    WeaponId.HEAVY_CROSSBOW -> {
                        val angles = listOf(-0.22f, -0.11f, 0f, 0.11f, 0.22f)
                        for (ang in angles) {
                            val dir = player.facing.toFloat()
                            projectiles.add(
                                Projectile(
                                    x = player.centerX + dir * 8f,
                                    y = player.centerY - 2f,
                                    vx = cos(ang) * def.projectileSpeed * dir,
                                    vy = sin(ang) * def.projectileSpeed,
                                    fromPlayer = true,
                                    kind = ProjectileKind.BOLT,
                                    damage = baseDmg,
                                    color = def.colorHex,
                                    life = 0.65f,
                                    pierceLeft = 1 + extraPierce
                                )
                            )
                        }
                        events.onScreenShake(3.2f)
                    }
                    WeaponId.FIREBRANDS -> {
                        projectiles.add(
                            Projectile(
                                x = player.centerX + player.facing * 6f,
                                y = player.centerY - 4f,
                                vx = player.facing * def.projectileSpeed,
                                vy = -155f,
                                fromPlayer = true,
                                kind = ProjectileKind.FIRE_BOTTLE,
                                damage = baseDmg,
                                color = def.colorHex,
                                life = 1.6f,
                                gravity = 460f,
                                status = StatusType.BURN,
                                statusDuration = def.statusDuration,
                                spawnFirePool = true
                            )
                        )
                    }
                    WeaponId.THROWING_KNIVES -> {
                        val nearest = map.enemies
                            .filter { it.isAlive && sign(it.centerX - player.centerX).toInt() == player.facing }
                            .minByOrNull { hypot((it.centerX - player.centerX).toDouble(), (it.centerY - player.centerY).toDouble()) }
                        var vx = player.facing * def.projectileSpeed
                        var vy = 0f
                        if (nearest != null) {
                            val dx = nearest.centerX - player.centerX
                            val dy = nearest.centerY - player.centerY
                            val len = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
                            if (len < def.rangePx) {
                                vx = (dx / len) * def.projectileSpeed
                                vy = (dy / len) * def.projectileSpeed
                            }
                        }
                        projectiles.add(
                            Projectile(
                                x = player.centerX + player.facing * 6f,
                                y = player.centerY - 2f,
                                vx = vx,
                                vy = vy,
                                fromPlayer = true,
                                kind = ProjectileKind.KNIFE,
                                damage = baseDmg,
                                color = def.colorHex,
                                life = 0.9f,
                                pierceLeft = extraPierce,
                                status = StatusType.BLEED,
                                statusDuration = def.statusDuration
                            )
                        )
                    }
                    else -> {
                        val kind = if (def.id == WeaponId.ICE_BOW) ProjectileKind.ICE_ARROW else ProjectileKind.ARROW
                        projectiles.add(
                            Projectile(
                                x = player.centerX + player.facing * 8f,
                                y = player.centerY - 2f,
                                vx = player.facing * def.projectileSpeed,
                                vy = 0f,
                                fromPlayer = true,
                                kind = kind,
                                damage = baseDmg,
                                color = def.colorHex,
                                life = 1.2f,
                                pierceLeft = extraPierce,
                                status = def.appliesStatus,
                                statusDuration = def.statusDuration
                            )
                        )
                    }
                }
            }

            ItemCategory.SHIELD -> {
                // Parry window already opened in Player.tryStartWeaponAttack; reflect nearby enemy projectiles!
                var parried = false
                for (p in projectiles) {
                    if (p.fromPlayer) continue
                    val dist = hypot((p.x - player.centerX).toDouble(), (p.y - player.centerY).toDouble()).toFloat()
                    if (dist <= 30f) {
                        reflectProjectile(p, player, baseDmg, particles, events, rng)
                        parried = true
                    }
                }
                if (!parried) {
                    events.onSfx("shield_raise")
                }
            }

            ItemCategory.SKILL -> Unit
        }
    }

    fun tryParryMeleeAttack(
        attacker: Enemy,
        player: Player,
        particles: ParticleSystem,
        events: CombatEventListener,
        rng: Random = Random.Default
    ): Boolean {
        if (player.parryWindowTimer <= 0f) return false
        player.parryWindowTimer = 0f
        player.invulnTimer = maxOf(player.invulnTimer, 0.35f)
        val shield = if (player.weapon2.def.category == ItemCategory.SHIELD) player.weapon2 else player.weapon1
        val parryDmg = shield.scaledDamage(player.scrollMultiplierFor(StatColor.SURVIVAL)) * shield.def.critMultiplier
        events.onSfx("parry")
        events.onHitstop(0.07f)
        events.onScreenShake(5f)
        events.onHaptic(40, 230)
        particles.spawnBurst(player.centerX + player.facing * 8f, player.centerY, 18, 0xFFFACC15.toInt(), 80f, 210f, 160f, 2.5f, 0.35f, rng)
        particles.spawnText(player.centerX, player.y - 12f, "PARRY!", 0xFFFACC15.toInt(), isCrit = true)
        damageEnemy(attacker, parryDmg, true, player.facing * 200f, StatusType.STUN, 1.8f, player, particles, events, rng)
        return true
    }

    private fun reflectProjectile(
        p: Projectile,
        player: Player,
        parryDmg: Float,
        particles: ParticleSystem,
        events: CombatEventListener,
        rng: Random
    ) {
        val reflected = p.copy(
            vx = -p.vx * 1.45f,
            vy = -p.vy * 1.1f,
            fromPlayer = true,
            damage = maxOf(p.damage * 2.2f, parryDmg),
            color = 0xFFFACC15.toInt(),
            life = 1.6f,
            isCrit = true,
            hitIds = HashSet()
        )
        p.life = 0f
        projectiles.add(reflected)
        events.onSfx("parry")
        events.onHitstop(0.055f)
        events.onScreenShake(4f)
        particles.spawnBurst(p.x, p.y, 14, 0xFFFACC15.toInt(), 70f, 180f, 140f, 2.5f, 0.32f, rng)
        particles.spawnText(player.centerX, player.y - 12f, "PARRY!", 0xFFFACC15.toInt(), isCrit = true)
    }

    fun evaluateMeleeCrit(def: WeaponDef, player: Player, enemy: Enemy): Boolean = when (def.critCondition) {
        CritCondition.THIRD_COMBO_HIT -> player.comboIndex == def.comboCount - 1
        CritCondition.HEAVY_FINISHER -> player.comboIndex >= 1
        CritCondition.BACKSTAB -> (enemy.facing == 1 && player.centerX < enemy.centerX) ||
            (enemy.facing == -1 && player.centerX > enemy.centerX)
        CritCondition.TARGET_BLEEDING -> enemy.isBleeding
        CritCondition.TARGET_FROZEN -> enemy.isFrozen
        else -> false
    }

    private fun evaluateAffixBonus(affix: WeaponAffix?, enemy: Enemy): Float = when (affix) {
        WeaponAffix.BONUS_VS_BLEED -> if (enemy.isBleeding) 1.25f else 1f
        WeaponAffix.BONUS_VS_FROZEN -> if (enemy.isFrozen) 1.30f else 1f
        WeaponAffix.BONUS_VS_BURN -> if (enemy.isBurning) 1.25f else 1f
        else -> 1f
    }

    fun damageEnemy(
        enemy: Enemy,
        rawDamage: Float,
        isCrit: Boolean,
        knockbackX: Float,
        status: StatusType?,
        statusDuration: Float,
        player: Player,
        particles: ParticleSystem,
        events: CombatEventListener,
        rng: Random = Random.Default
    ): Boolean {
        if (!enemy.isAlive) return false
        val rootAmp = if (enemy.isRooted) 1.5f else 1f
        val finalDmg = (rawDamage * rootAmp).coerceAtLeast(1f)
        enemy.hp = (enemy.hp - finalDmg).coerceAtLeast(0f)
        enemy.hurtFlashTimer = 0.14f

        if (!enemy.type.isBoss) {
            enemy.vx = knockbackX * 0.65f
            enemy.vy = minOf(enemy.vy, -65f)
        } else {
            enemy.vx += knockbackX * 0.12f
        }

        if (status != null && statusDuration > 0f) {
            enemy.applyStatus(status, statusDuration)
        }

        // Rally recovery for player!
        player.recoverRally(finalDmg)

        particles.spawnDirectionalSparks(
            enemy.centerX,
            enemy.centerY,
            sign(if (knockbackX != 0f) knockbackX else player.facing.toFloat()),
            if (isCrit) 12 else 6,
            if (isCrit) 0xFFFACC15.toInt() else 0xFFE63946.toInt(),
            rng
        )
        particles.spawnText(
            enemy.centerX + (rng.nextFloat() - 0.5f) * 8f,
            enemy.y - 4f,
            if (isCrit) "${finalDmg.toInt()}!" else "${finalDmg.toInt()}",
            if (isCrit) 0xFFFACC15.toInt() else 0xFFFFFFFF.toInt(),
            isCrit = isCrit
        )

        if (isCrit) {
            events.onSfx("crit")
            events.onHaptic(32, 220)
        } else {
            events.onSfx("hit")
            events.onHaptic(18, 130)
        }

        if (!enemy.isAlive) {
            onEnemyKilled(enemy, player, particles, events, rng)
            return true
        }
        return false
    }

    private fun onEnemyKilled(
        enemy: Enemy,
        player: Player,
        particles: ParticleSystem,
        events: CombatEventListener,
        rng: Random
    ) {
        player.kills++
        val goldGain = enemy.type.goldDrop * (if (enemy.isElite) 2 else 1)
        val cellGain = enemy.type.cellDrop * (if (enemy.isElite) 2 else 1)
        player.gold += goldGain
        player.runCells += cellGain

        particles.spawnBurst(enemy.centerX, enemy.centerY, if (enemy.type.isBoss) 45 else 18, enemy.type.accentColor, 50f, 190f, 320f, 2.5f, 0.55f, rng)
        particles.spawnText(enemy.centerX, enemy.y - 12f, "+${cellGain} CELL", 0xFF38BDF8.toInt())
        events.onSfx(if (enemy.type.isBoss) "boss_death" else "kill")
        events.onScreenShake(if (enemy.type.isBoss) 9f else 3f)

        // Toxic Bomber explodes into a poison cloud on death!
        if (enemy.type == EnemyType.TOXIC_BOMBER) {
            hazards.add(
                GroundHazard(
                    kind = HazardKind.POISON_CLOUD,
                    x = enemy.centerX,
                    y = enemy.centerY,
                    radius = 34f,
                    fromPlayer = false,
                    damagePerTick = 7f,
                    life = 3.5f,
                    tickTimer = 0.35f
                )
            )
            particles.spawnBurst(enemy.centerX, enemy.centerY, 20, 0xFF4ADE80.toInt(), 40f, 130f, 30f, 3f, 0.5f, rng)
        }
    }

    private fun updateEnemyStatuses(
        dt: Float,
        player: Player,
        map: LevelMap,
        particles: ParticleSystem,
        events: CombatEventListener,
        rng: Random
    ) {
        for (e in map.enemies) {
            if (!e.isAlive) continue
            val iter = e.statusTimers.entries.iterator()
            while (iter.hasNext()) {
                val entry = iter.next()
                entry.setValue(entry.value - dt)
                if (entry.value <= 0f) iter.remove()
            }

            e.statusTickTimer -= dt
            if (e.statusTickTimer <= 0f) {
                e.statusTickTimer = 0.5f
                var dotDmg = 0f
                var dotColor = 0xFFFFFFFF.toInt()
                if (e.isBleeding) {
                    dotDmg += 6f
                    dotColor = StatusType.BLEED.colorHex
                }
                if (e.isBurning) {
                    dotDmg += 8f
                    dotColor = StatusType.BURN.colorHex
                }
                if (e.hasStatus(StatusType.POISON)) {
                    dotDmg += 7f
                    dotColor = StatusType.POISON.colorHex
                }
                if (dotDmg > 0f) {
                    e.hp = (e.hp - dotDmg).coerceAtLeast(0f)
                    particles.spawnText(e.centerX, e.y - 2f, "${dotDmg.toInt()}", dotColor)
                    if (!e.isAlive) {
                        onEnemyKilled(e, player, particles, events, rng)
                    }
                }
            }
        }
    }

    private fun updateProjectiles(
        dt: Float,
        player: Player,
        map: LevelMap,
        particles: ParticleSystem,
        events: CombatEventListener,
        rng: Random
    ) {
        val spawned = ArrayList<Projectile>()
        val iter = projectiles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.life -= dt
            if (p.life <= 0f) {
                if (p.explodeRadius > 0f) {
                    explodeProjectile(p, player, map, particles, events, spawned, rng)
                }
                iter.remove()
                continue
            }

            p.vy += p.gravity * dt
            p.x += p.vx * dt
            p.y += p.vy * dt

            // Wall collision
            if (!p.passThroughWalls && map.isSolid(map.worldToTile(p.x), map.worldToTile(p.y))) {
                if (p.explodeRadius > 0f) {
                    explodeProjectile(p, player, map, particles, events, spawned, rng)
                } else if (p.spawnFirePool) {
                    hazards.add(
                        GroundHazard(HazardKind.FIRE_POOL, p.x, p.y - 2f, 28f, true, p.damage * 0.45f, 3.8f, 0.35f)
                    )
                    events.onSfx("fire")
                }
                particles.spawnBurst(p.x, p.y, 6, p.color, 30f, 90f, 200f, 1.5f, 0.25f, rng)
                iter.remove()
                continue
            }

            if (p.fromPlayer) {
                var removed = false
                for ((idx, e) in map.enemies.withIndex()) {
                    if (!e.isAlive || idx in p.hitIds) continue
                    if (p.x in (e.x - 4f)..(e.x + e.width + 4f) && p.y in (e.y - 4f)..(e.y + e.height + 4f)) {
                        p.hitIds.add(idx)

                        if (p.explodeRadius > 0f) {
                            explodeProjectile(p, player, map, particles, events, spawned, rng)
                            iter.remove()
                            removed = true
                            break
                        }

                        var crit = p.isCrit
                        if (p.kind == ProjectileKind.ARROW) {
                            e.stuckArrows++
                            if (e.stuckArrows >= 3) crit = true
                        }
                        if (p.kind == ProjectileKind.KNIFE && e.isBleeding) {
                            crit = true
                        }

                        val dmg = p.damage * (if (crit && !p.isCrit) 2.0f else 1f)
                        damageEnemy(e, dmg, crit, sign(p.vx) * 80f, p.status, p.statusDuration, player, particles, events, rng)

                        if (p.spawnFirePool) {
                            hazards.add(
                                GroundHazard(HazardKind.FIRE_POOL, p.x, e.bottomY - 2f, 28f, true, p.damage * 0.45f, 3.8f, 0.35f)
                            )
                        }

                        if (p.pierceLeft > 0) {
                            p.pierceLeft--
                        } else {
                            iter.remove()
                            removed = true
                            break
                        }
                    }
                }
                if (removed) continue
            } else {
                // Enemy projectile vs Player
                if (p.x in (player.x - 3f)..(player.x + player.width + 3f) &&
                    p.y in (player.y - 3f)..(player.y + player.height + 3f)
                ) {
                    if (player.parryWindowTimer > 0f) {
                        reflectProjectile(p, player, 45f, particles, events, rng)
                        iter.remove()
                        continue
                    }
                    if (!player.isInvulnerable) {
                        val dealt = player.takeDamage(p.damage, sign(p.vx) * 140f)
                        if (dealt > 0f) {
                            events.onSfx("hurt")
                            events.onScreenShake(4.5f)
                            events.onHaptic(35, 180)
                            particles.spawnText(player.centerX, player.y - 6f, "-${dealt.toInt()}", 0xFFEF4444.toInt())
                        }
                        iter.remove()
                    }
                }
            }
        }
        projectiles.addAll(spawned)
    }

    private fun explodeProjectile(
        p: Projectile,
        player: Player,
        map: LevelMap,
        particles: ParticleSystem,
        events: CombatEventListener,
        spawned: MutableList<Projectile>,
        rng: Random
    ) {
        events.onSfx("explosion")
        events.onScreenShake(5f)
        events.onHaptic(40, 210)
        particles.spawnBurst(p.x, p.y, 22, 0xFFF97316.toInt(), 60f, 190f, 240f, 3f, 0.45f, rng)

        if (p.fromPlayer) {
            for (e in map.enemies) {
                if (!e.isAlive) continue
                val dist = hypot((e.centerX - p.x).toDouble(), (e.centerY - p.y).toDouble()).toFloat()
                if (dist <= p.explodeRadius) {
                    damageEnemy(e, p.damage, false, sign(e.centerX - p.x) * 180f, p.status, p.statusDuration, player, particles, events, rng)
                }
            }
            if (p.spawnMiniBombs) {
                for (i in 0 until 4) {
                    val angle = -2.5f + i * 0.65f
                    spawned.add(
                        Projectile(
                            x = p.x,
                            y = p.y - 4f,
                            vx = cos(angle) * 125f,
                            vy = sin(angle) * 150f - 40f,
                            fromPlayer = true,
                            kind = ProjectileKind.MINI_BOMB,
                            damage = p.damage * 0.45f,
                            color = 0xFFFACC15.toInt(),
                            life = 0.65f,
                            gravity = 460f,
                            explodeRadius = 30f
                        )
                    )
                }
            }
        } else {
            val dist = hypot((player.centerX - p.x).toDouble(), (player.centerY - p.y).toDouble()).toFloat()
            if (dist <= p.explodeRadius && !player.isInvulnerable) {
                val dealt = player.takeDamage(p.damage, sign(player.centerX - p.x) * 170f)
                if (dealt > 0f) {
                    events.onSfx("hurt")
                    particles.spawnText(player.centerX, player.y - 6f, "-${dealt.toInt()}", 0xFFEF4444.toInt())
                }
            }
        }
    }

    private fun updateHazards(
        dt: Float,
        player: Player,
        map: LevelMap,
        particles: ParticleSystem,
        events: CombatEventListener,
        rng: Random
    ) {
        val iter = hazards.iterator()
        while (iter.hasNext()) {
            val h = iter.next()
            h.life -= dt
            if (h.life <= 0f) {
                iter.remove()
                continue
            }
            h.tickTimer -= dt
            if (h.tickTimer > 0f) continue

            when (h.kind) {
                HazardKind.FIRE_POOL -> {
                    h.tickTimer = 0.35f
                    particles.spawnBurst(h.x + (rng.nextFloat() - 0.5f) * h.radius, h.y, 2, 0xFFF97316.toInt(), 10f, 35f, -40f, 2f, 0.25f, rng)
                    for (e in map.enemies) {
                        if (!e.isAlive) continue
                        if (abs(e.centerX - h.x) <= h.radius && abs(e.bottomY - h.y) <= 22f) {
                            damageEnemy(e, h.damagePerTick, false, 0f, StatusType.BURN, 2.5f, player, particles, events, rng)
                        }
                    }
                }
                HazardKind.POISON_CLOUD -> {
                    h.tickTimer = 0.4f
                    if (!h.fromPlayer && !player.isInvulnerable) {
                        val dist = hypot((player.centerX - h.x).toDouble(), (player.centerY - h.y).toDouble()).toFloat()
                        if (dist <= h.radius) {
                            val dealt = player.takeDamage(h.damagePerTick, 0f)
                            if (dealt > 0f) {
                                events.onSfx("hurt")
                                particles.spawnText(player.centerX, player.y - 6f, "-${dealt.toInt()}", 0xFF4ADE80.toInt())
                            }
                        }
                    }
                }
                HazardKind.WOLF_TRAP -> {
                    h.tickTimer = 0.08f
                    for (e in map.enemies) {
                        if (!e.isAlive) continue
                        if (abs(e.centerX - h.x) <= h.radius && abs(e.bottomY - h.y) <= 18f) {
                            damageEnemy(e, h.damagePerTick, false, 0f, StatusType.ROOT, 4.5f, player, particles, events, rng)
                            particles.spawnText(e.centerX, e.y - 10f, "ROOTED!", 0xFF2DD4BF.toInt())
                            events.onSfx("clang")
                            h.life = 0f
                            break
                        }
                    }
                }
                HazardKind.SAW_TURRET -> {
                    h.tickTimer = 0.42f
                    val target = map.enemies
                        .filter { it.isAlive }
                        .minByOrNull { hypot((it.centerX - h.x).toDouble(), (it.centerY - h.y).toDouble()) }
                    if (target != null) {
                        val dx = target.centerX - h.x
                        val dy = target.centerY - h.y
                        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                        if (dist <= h.radius) {
                            projectiles.add(
                                Projectile(
                                    x = h.x,
                                    y = h.y - 4f,
                                    vx = (dx / dist) * 320f,
                                    vy = (dy / dist) * 320f,
                                    fromPlayer = true,
                                    kind = ProjectileKind.KNIFE,
                                    damage = h.damagePerTick,
                                    color = 0xFFA855F7.toInt(),
                                    life = 0.7f,
                                    status = StatusType.BLEED,
                                    statusDuration = 2.0f
                                )
                            )
                            events.onSfx("bow")
                        }
                    }
                }
            }
        }
    }
}
