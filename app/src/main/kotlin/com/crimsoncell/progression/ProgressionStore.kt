package com.crimsoncell.progression

import android.content.Context

enum class ForgeUpgrade(
    val title: String,
    val description: String,
    val maxLevel: Int,
    val baseCost: Int,
    val costStep: Int
) {
    FLASK_CHARGES("Health Flask+", "+1 Max Flask Charge per run", 3, 15, 20),
    STARTING_GOLD("Gold Reserve", "Start each run with +100 Gold", 4, 12, 15),
    WEAPON_TIER("Cell Forge", "Higher tier starting weapons", 3, 20, 25),
    VITALITY("Crimson Vigor", "+25 Base Max HP per rank", 4, 14, 18);

    fun costForLevel(currentLevel: Int): Int = baseCost + currentLevel * costStep
}

data class ProgressionState(
    var bankedCells: Int = 0,
    var flaskLevel: Int = 0,
    var goldLevel: Int = 0,
    var weaponTierLevel: Int = 0,
    var vitalityLevel: Int = 0,
    var totalRuns: Int = 0,
    var totalKills: Int = 0,
    var bestBiomeReached: Int = 1,
    var victories: Int = 0,
    var audioMuted: Boolean = false
) {
    fun levelOf(upgrade: ForgeUpgrade): Int = when (upgrade) {
        ForgeUpgrade.FLASK_CHARGES -> flaskLevel
        ForgeUpgrade.STARTING_GOLD -> goldLevel
        ForgeUpgrade.WEAPON_TIER -> weaponTierLevel
        ForgeUpgrade.VITALITY -> vitalityLevel
    }

    fun canBuy(upgrade: ForgeUpgrade): Boolean {
        val lvl = levelOf(upgrade)
        return lvl < upgrade.maxLevel && bankedCells >= upgrade.costForLevel(lvl)
    }

    fun buy(upgrade: ForgeUpgrade): Boolean {
        if (!canBuy(upgrade)) return false
        val lvl = levelOf(upgrade)
        bankedCells -= upgrade.costForLevel(lvl)
        when (upgrade) {
            ForgeUpgrade.FLASK_CHARGES -> flaskLevel++
            ForgeUpgrade.STARTING_GOLD -> goldLevel++
            ForgeUpgrade.WEAPON_TIER -> weaponTierLevel++
            ForgeUpgrade.VITALITY -> vitalityLevel++
        }
        return true
    }

    val startingFlaskCharges: Int get() = 2 + flaskLevel
    val startingGold: Int get() = goldLevel * 100
    val startingWeaponTier: Int get() = 1 + weaponTierLevel
    val bonusMaxHp: Float get() = vitalityLevel * 25f
}

interface ProgressionRepository {
    fun load(): ProgressionState
    fun save(state: ProgressionState)
}

class InMemoryProgressionRepository(
    private var snapshot: ProgressionState = ProgressionState()
) : ProgressionRepository {
    override fun load(): ProgressionState = snapshot.copy()
    override fun save(state: ProgressionState) {
        snapshot = state.copy()
    }
}

class SharedPrefsProgressionRepository(context: Context) : ProgressionRepository {
    private val prefs = context.getSharedPreferences("crimson_cell_save_v1", Context.MODE_PRIVATE)

    override fun load(): ProgressionState = ProgressionState(
        bankedCells = prefs.getInt("bankedCells", 0),
        flaskLevel = prefs.getInt("flaskLevel", 0),
        goldLevel = prefs.getInt("goldLevel", 0),
        weaponTierLevel = prefs.getInt("weaponTierLevel", 0),
        vitalityLevel = prefs.getInt("vitalityLevel", 0),
        totalRuns = prefs.getInt("totalRuns", 0),
        totalKills = prefs.getInt("totalKills", 0),
        bestBiomeReached = prefs.getInt("bestBiomeReached", 1),
        victories = prefs.getInt("victories", 0),
        audioMuted = prefs.getBoolean("audioMuted", false)
    )

    override fun save(state: ProgressionState) {
        prefs.edit()
            .putInt("bankedCells", state.bankedCells)
            .putInt("flaskLevel", state.flaskLevel)
            .putInt("goldLevel", state.goldLevel)
            .putInt("weaponTierLevel", state.weaponTierLevel)
            .putInt("vitalityLevel", state.vitalityLevel)
            .putInt("totalRuns", state.totalRuns)
            .putInt("totalKills", state.totalKills)
            .putInt("bestBiomeReached", state.bestBiomeReached)
            .putInt("victories", state.victories)
            .putBoolean("audioMuted", state.audioMuted)
            .apply()
    }
}
