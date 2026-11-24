package com.crimsoncell.world

import com.crimsoncell.enemy.EnemyType

enum class BiomeId(val index: Int) {
    PRISONERS_DEPTHS(1),
    TOXIC_SEWERS(2),
    CLOCKWORK_SPIRE(3)
}

data class BiomeConfig(
    val id: BiomeId,
    val name: String,
    val subtitle: String,
    val skyTopColor: Int,
    val skyBottomColor: Int,
    val parallaxFarColor: Int,
    val parallaxNearColor: Int,
    val tileMainColor: Int,
    val tileHighlightColor: Int,
    val tileDarkColor: Int,
    val platformColor: Int,
    val accentGlowColor: Int,
    val enemyPool: List<EnemyType>,
    val bossType: EnemyType?,
    val difficultyScale: Float,
    val musicBpm: Int,
    val baseTier: Int
)

object BiomeCatalog {
    val all: List<BiomeConfig> = listOf(
        BiomeConfig(
            id = BiomeId.PRISONERS_DEPTHS,
            name = "PRISONER'S DEPTHS",
            subtitle = "Cold stone cells & restless dead",
            skyTopColor = 0xFF0E111A.toInt(),
            skyBottomColor = 0xFF1A2233.toInt(),
            parallaxFarColor = 0xFF151C2B.toInt(),
            parallaxNearColor = 0xFF212C42.toInt(),
            tileMainColor = 0xFF334155.toInt(),
            tileHighlightColor = 0xFF475569.toInt(),
            tileDarkColor = 0xFF1E293B.toInt(),
            platformColor = 0xFF92400E.toInt(),
            accentGlowColor = 0xFFF59E0B.toInt(),
            enemyPool = listOf(
                EnemyType.UNDEAD_RUNNER,
                EnemyType.BONE_ARCHER,
                EnemyType.SHIELDBEARER,
                EnemyType.GRENADIER,
                EnemyType.CRIMSON_BAT
            ),
            bossType = null,
            difficultyScale = 1.0f,
            musicBpm = 132,
            baseTier = 1
        ),
        BiomeConfig(
            id = BiomeId.TOXIC_SEWERS,
            name = "TOXIC SEWERS",
            subtitle = "Venomous conduits & the Iron Warden",
            skyTopColor = 0xFF0A1815.toInt(),
            skyBottomColor = 0xFF132E27.toInt(),
            parallaxFarColor = 0xFF0F241F.toInt(),
            parallaxNearColor = 0xFF1A3C34.toInt(),
            tileMainColor = 0xFF2B4C43.toInt(),
            tileHighlightColor = 0xFF3D6B5E.toInt(),
            tileDarkColor = 0xFF172E28.toInt(),
            platformColor = 0xFF65A30D.toInt(),
            accentGlowColor = 0xFF4ADE80.toInt(),
            enemyPool = listOf(
                EnemyType.UNDEAD_RUNNER,
                EnemyType.TOXIC_BOMBER,
                EnemyType.CRIMSON_BAT,
                EnemyType.INQUISITOR,
                EnemyType.SHIELDBEARER,
                EnemyType.DARK_SLASHER
            ),
            bossType = EnemyType.BOSS_IRON_WARDEN,
            difficultyScale = 1.45f,
            musicBpm = 142,
            baseTier = 2
        ),
        BiomeConfig(
            id = BiomeId.CLOCKWORK_SPIRE,
            name = "CLOCKWORK SPIRE",
            subtitle = "Crimson gears & the Crimson Reaper",
            skyTopColor = 0xFF1A0A12.toInt(),
            skyBottomColor = 0xFF311220.toInt(),
            parallaxFarColor = 0xFF260E1A.toInt(),
            parallaxNearColor = 0xFF3F172A.toInt(),
            tileMainColor = 0xFF4C2332.toInt(),
            tileHighlightColor = 0xFF6B3246.toInt(),
            tileDarkColor = 0xFF2D121C.toInt(),
            platformColor = 0xFFB45309.toInt(),
            accentGlowColor = 0xFFE63946.toInt(),
            enemyPool = listOf(
                EnemyType.DARK_SLASHER,
                EnemyType.INQUISITOR,
                EnemyType.GOLEM_BRUISER,
                EnemyType.PHANTOM_ASSASSIN,
                EnemyType.GRENADIER,
                EnemyType.BONE_ARCHER
            ),
            bossType = EnemyType.BOSS_CRIMSON_REAPER,
            difficultyScale = 1.95f,
            musicBpm = 154,
            baseTier = 3
        )
    )

    fun forIndex(index: Int): BiomeConfig = all[(index - 1).coerceIn(0, all.lastIndex)]
}
