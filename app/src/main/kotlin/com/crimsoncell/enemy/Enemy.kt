package com.crimsoncell.enemy

import com.crimsoncell.combat.StatusType

enum class EnemyType(
    val displayName: String,
    val baseHp: Float,
    val baseDamage: Float,
    val moveSpeed: Float,
    val width: Float,
    val height: Float,
    val goldDrop: Int,
    val cellDrop: Int,
    val isFlying: Boolean = false,
    val isBoss: Boolean = false,
    val primaryColor: Int,
    val accentColor: Int
) {
    // 10 Regular Archetypes
    UNDEAD_RUNNER("Undead Runner", 48f, 14f, 82f, 12f, 18f, 12, 1, false, false, 0xFF65A30D.toInt(), 0xFFBEF264.toInt()),
    BONE_ARCHER("Bone Archer", 38f, 13f, 58f, 12f, 18f, 15, 1, false, false, 0xFFE2E8F0.toInt(), 0xFF94A3B8.toInt()),
    SHIELDBEARER("Shieldbearer", 74f, 17f, 52f, 14f, 20f, 20, 2, false, false, 0xFF475569.toInt(), 0xFF38BDF8.toInt()),
    GRENADIER("Grenadier", 54f, 20f, 56f, 13f, 19f, 18, 2, false, false, 0xFFD97706.toInt(), 0xFFEF4444.toInt()),
    CRIMSON_BAT("Crimson Bat", 26f, 11f, 108f, 12f, 10f, 10, 1, true, false, 0xFFBE123C.toInt(), 0xFFFDA4AF.toInt()),
    INQUISITOR("Inquisitor", 52f, 18f, 48f, 12f, 20f, 22, 2, false, false, 0xFF7E22CE.toInt(), 0xFFE879F9.toInt()),
    DARK_SLASHER("Dark Slasher", 82f, 22f, 112f, 14f, 20f, 26, 2, false, false, 0xFF1E1B4B.toInt(), 0xFFF43F5E.toInt()),
    TOXIC_BOMBER("Toxic Bomber", 44f, 24f, 74f, 14f, 16f, 18, 2, false, false, 0xFF15803D.toInt(), 0xFF4ADE80.toInt()),
    GOLEM_BRUISER("Golem Bruiser", 135f, 28f, 44f, 20f, 26f, 34, 3, false, false, 0xFF57534E.toInt(), 0xFFF97316.toInt()),
    PHANTOM_ASSASSIN("Phantom Assassin", 62f, 21f, 96f, 12f, 19f, 28, 3, false, false, 0xFF312E81.toInt(), 0xFF38BDF8.toInt()),

    // 2 Multi-Phase Bosses
    BOSS_IRON_WARDEN("IRON WARDEN", 520f, 26f, 68f, 28f, 34f, 180, 18, false, true, 0xFF64748B.toInt(), 0xFFF97316.toInt()),
    BOSS_CRIMSON_REAPER("CRIMSON REAPER", 740f, 30f, 128f, 24f, 32f, 280, 30, false, true, 0xFF881337.toInt(), 0xFFFF2E63.toInt())
}

enum class EnemyAiState {
    PATROL, CHASE, TELEGRAPH, ATTACK, COOLDOWN, STUNNED
}

class Enemy(
    val type: EnemyType,
    var x: Float,
    var y: Float,
    difficultyScale: Float = 1f,
    val isElite: Boolean = false
) {
    val width: Float = if (isElite && !type.isBoss) type.width * 1.15f else type.width
    val height: Float = if (isElite && !type.isBoss) type.height * 1.15f else type.height

    val maxHp: Float = type.baseHp * difficultyScale * (if (isElite) 1.75f else 1f)
    var hp: Float = maxHp
    val damage: Float = type.baseDamage * (0.85f + 0.15f * difficultyScale) * (if (isElite) 1.25f else 1f)

    var vx: Float = 0f
    var vy: Float = 0f
    var facing: Int = -1
    var onGround: Boolean = false

    var state: EnemyAiState = EnemyAiState.PATROL
    var stateTimer: Float = 0.6f
    var attackCooldown: Float = 0.5f
    var attackPatternIndex: Int = 0
    var attackHasFired: Boolean = false
    var hurtFlashTimer: Float = 0f
    var animTime: Float = 0f

    // Status effect timers
    val statusTimers = HashMap<StatusType, Float>()
    var statusTickTimer: Float = 0f
    var stuckArrows: Int = 0

    // Boss phase (1 or 2 below 50% HP)
    val bossPhase: Int get() = if (type.isBoss && hp <= maxHp * 0.5f) 2 else 1

    val centerX: Float get() = x + width * 0.5f
    val centerY: Float get() = y + height * 0.5f
    val bottomY: Float get() = y + height
    val isAlive: Boolean get() = hp > 0f

    val isTelegraphing: Boolean get() = state == EnemyAiState.TELEGRAPH
    val isFrozen: Boolean get() = (statusTimers[StatusType.FROZEN] ?: 0f) > 0f
    val isStunned: Boolean get() = (statusTimers[StatusType.STUN] ?: 0f) > 0f || state == EnemyAiState.STUNNED
    val isRooted: Boolean get() = (statusTimers[StatusType.ROOT] ?: 0f) > 0f
    val isBleeding: Boolean get() = (statusTimers[StatusType.BLEED] ?: 0f) > 0f
    val isBurning: Boolean get() = (statusTimers[StatusType.BURN] ?: 0f) > 0f

    fun applyStatus(status: StatusType, duration: Float) {
        val existing = statusTimers[status] ?: 0f
        val mult = if (type.isBoss && (status == StatusType.FROZEN || status == StatusType.STUN)) 0.45f else 1f
        statusTimers[status] = maxOf(existing, duration * mult)
        if (status == StatusType.STUN) {
            state = EnemyAiState.STUNNED
            stateTimer = maxOf(stateTimer, duration * mult)
        }
    }

    fun hasStatus(status: StatusType): Boolean = (statusTimers[status] ?: 0f) > 0f

    fun blocksFrontalAttack(attackerX: Float): Boolean {
        if (type != EnemyType.SHIELDBEARER) return false
        if (isFrozen || isStunned || state == EnemyAiState.ATTACK) return false
        val attackerOnRight = attackerX > centerX
        return (facing == 1 && attackerOnRight) || (facing == -1 && !attackerOnRight)
    }
}
