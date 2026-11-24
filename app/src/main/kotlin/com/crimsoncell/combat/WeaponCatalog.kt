package com.crimsoncell.combat

import kotlin.random.Random

enum class ItemCategory {
    MELEE, RANGED, SHIELD, SKILL
}

enum class StatColor(val label: String, val colorHex: Int) {
    BRUTALITY("BRUTALITY", 0xFFE63946.toInt()),
    TACTICS("TACTICS", 0xFFA855F7.toInt()),
    SURVIVAL("SURVIVAL", 0xFF2EC4B6.toInt())
}

enum class StatusType(val colorHex: Int, val label: String) {
    BLEED(0xFFDC2626.toInt(), "BLEED"),
    BURN(0xFFF97316.toInt(), "BURN"),
    FROZEN(0xFF38BDF8.toInt(), "FROZEN"),
    POISON(0xFF4ADE80.toInt(), "POISON"),
    STUN(0xFFFACC15.toInt(), "STUN"),
    ROOT(0xFFA78BFA.toInt(), "ROOT")
}

enum class CritCondition {
    NONE,
    THIRD_COMBO_HIT,
    HEAVY_FINISHER,
    BACKSTAB,
    TARGET_BLEEDING,
    TARGET_FROZEN,
    TARGET_STACKED_ARROWS,
    PARRY_COUNTER
}

enum class WeaponAffix(val description: String) {
    BONUS_VS_BLEED("+25% vs Bleeding"),
    BONUS_VS_FROZEN("+30% vs Frozen"),
    BONUS_VS_BURN("+25% vs Burning"),
    POISON_ON_HIT("Poisons on hit"),
    PIERCE_TARGET("Pierces +1 enemy"),
    FAST_SWING("+18% Attack Speed"),
    VAMPIRIC("Heals 2 HP on kill")
}

enum class WeaponId {
    // 8 Melee Weapons
    RUSTY_BLADE,
    TWIN_DAGGERS,
    BROADSWORD,
    ASSASSINS_DAGGER,
    BLOOD_SWORD,
    FROST_BLADE,
    LIGHTNING_WHIP,
    VAMPIRIC_SCYTHE,

    // 6 Ranged & Shield
    QUICK_BOW,
    HEAVY_CROSSBOW,
    FIREBRANDS,
    ICE_BOW,
    THROWING_KNIVES,
    SPIKED_SHIELD,

    // 6 Active Skills
    CLUSTER_GRENADE,
    FROST_NOVA,
    SINEW_SLICER,
    PHASER,
    FLAME_WAVE,
    WOLF_TRAP
}

data class WeaponDef(
    val id: WeaponId,
    val name: String,
    val shortName: String,
    val category: ItemCategory,
    val statColor: StatColor,
    val baseDamage: Float,
    val critMultiplier: Float,
    val critCondition: CritCondition,
    val windupSec: Float,
    val activeSec: Float,
    val recoverySec: Float,
    val rangePx: Float,
    val knockback: Float,
    val comboCount: Int = 1,
    val cooldownSec: Float = 0f,
    val appliesStatus: StatusType? = null,
    val statusDuration: Float = 0f,
    val projectileSpeed: Float = 0f,
    val projectileCount: Int = 0,
    val colorHex: Int,
    val description: String,
    val basePrice: Int = 120
)

data class WeaponInstance(
    val def: WeaponDef,
    val tier: Int = 1,
    val affix: WeaponAffix? = null,
    var cooldownRemaining: Float = 0f
) {
    val displayName: String
        get() {
            val roman = when (tier.coerceIn(1, 5)) {
                1 -> "I"
                2 -> "II"
                3 -> "III"
                4 -> "IV"
                else -> "V"
            }
            return "${def.name} $roman"
        }

    fun scaledDamage(scrollBonusMultiplier: Float): Float {
        val tierMult = 1f + (tier - 1) * 0.22f
        return def.baseDamage * tierMult * scrollBonusMultiplier
    }

    fun speedMultiplier(): Float = if (affix == WeaponAffix.FAST_SWING) 1.18f else 1f
}

object WeaponCatalog {
    val allDefs: List<WeaponDef> = listOf(
        // === 8 MELEE WEAPONS ===
        WeaponDef(
            id = WeaponId.RUSTY_BLADE,
            name = "Rusty Blade",
            shortName = "BLADE",
            category = ItemCategory.MELEE,
            statColor = StatColor.BRUTALITY,
            baseDamage = 18f,
            critMultiplier = 1.5f,
            critCondition = CritCondition.NONE,
            windupSec = 0.06f,
            activeSec = 0.10f,
            recoverySec = 0.14f,
            rangePx = 30f,
            knockback = 120f,
            comboCount = 3,
            colorHex = 0xFFCBD5E1.toInt(),
            description = "Fast 3-hit slash combo.",
            basePrice = 80
        ),
        WeaponDef(
            id = WeaponId.TWIN_DAGGERS,
            name = "Twin Daggers",
            shortName = "DAGGERS",
            category = ItemCategory.MELEE,
            statColor = StatColor.BRUTALITY,
            baseDamage = 15f,
            critMultiplier = 2.4f,
            critCondition = CritCondition.THIRD_COMBO_HIT,
            windupSec = 0.04f,
            activeSec = 0.08f,
            recoverySec = 0.10f,
            rangePx = 26f,
            knockback = 85f,
            comboCount = 3,
            colorHex = 0xFFF43F5E.toInt(),
            description = "Ultra-fast combo. 3rd hit always CRITS.",
            basePrice = 130
        ),
        WeaponDef(
            id = WeaponId.BROADSWORD,
            name = "Broadsword",
            shortName = "BROAD",
            category = ItemCategory.MELEE,
            statColor = StatColor.SURVIVAL,
            baseDamage = 36f,
            critMultiplier = 2.2f,
            critCondition = CritCondition.HEAVY_FINISHER,
            windupSec = 0.18f,
            activeSec = 0.14f,
            recoverySec = 0.24f,
            rangePx = 42f,
            knockback = 240f,
            comboCount = 3,
            colorHex = 0xFF94A3B8.toInt(),
            description = "Massive cleave. 2nd & 3rd swings CRIT.",
            basePrice = 160
        ),
        WeaponDef(
            id = WeaponId.ASSASSINS_DAGGER,
            name = "Assassin Dagger",
            shortName = "ASSASSIN",
            category = ItemCategory.MELEE,
            statColor = StatColor.BRUTALITY,
            baseDamage = 19f,
            critMultiplier = 2.6f,
            critCondition = CritCondition.BACKSTAB,
            windupSec = 0.05f,
            activeSec = 0.08f,
            recoverySec = 0.11f,
            rangePx = 25f,
            knockback = 90f,
            comboCount = 3,
            colorHex = 0xFFC084FC.toInt(),
            description = "CRITS when striking foes from behind.",
            basePrice = 150
        ),
        WeaponDef(
            id = WeaponId.BLOOD_SWORD,
            name = "Blood Sword",
            shortName = "BLOOD",
            category = ItemCategory.MELEE,
            statColor = StatColor.BRUTALITY,
            baseDamage = 17f,
            critMultiplier = 2.0f,
            critCondition = CritCondition.TARGET_BLEEDING,
            windupSec = 0.06f,
            activeSec = 0.10f,
            recoverySec = 0.13f,
            rangePx = 32f,
            knockback = 110f,
            comboCount = 3,
            appliesStatus = StatusType.BLEED,
            statusDuration = 4.0f,
            colorHex = 0xFFDC2626.toInt(),
            description = "Inflicts BLEED. Crits bleeding targets.",
            basePrice = 165
        ),
        WeaponDef(
            id = WeaponId.FROST_BLADE,
            name = "Frost Blade",
            shortName = "FROST",
            category = ItemCategory.MELEE,
            statColor = StatColor.SURVIVAL,
            baseDamage = 22f,
            critMultiplier = 2.5f,
            critCondition = CritCondition.TARGET_FROZEN,
            windupSec = 0.08f,
            activeSec = 0.11f,
            recoverySec = 0.15f,
            rangePx = 34f,
            knockback = 135f,
            comboCount = 2,
            appliesStatus = StatusType.FROZEN,
            statusDuration = 1.6f,
            colorHex = 0xFF38BDF8.toInt(),
            description = "Chills foes. Shatters FROZEN foes for 2.5x CRIT.",
            basePrice = 175
        ),
        WeaponDef(
            id = WeaponId.LIGHTNING_WHIP,
            name = "Lightning Whip",
            shortName = "WHIP",
            category = ItemCategory.MELEE,
            statColor = StatColor.TACTICS,
            baseDamage = 20f,
            critMultiplier = 1.8f,
            critCondition = CritCondition.THIRD_COMBO_HIT,
            windupSec = 0.07f,
            activeSec = 0.10f,
            recoverySec = 0.14f,
            rangePx = 64f,
            knockback = 70f,
            comboCount = 3,
            appliesStatus = StatusType.STUN,
            statusDuration = 0.45f,
            colorHex = 0xFFFACC15.toInt(),
            description = "Long-range electric whip that chains to nearby foes.",
            basePrice = 185
        ),
        WeaponDef(
            id = WeaponId.VAMPIRIC_SCYTHE,
            name = "Crimson Scythe",
            shortName = "SCYTHE",
            category = ItemCategory.MELEE,
            statColor = StatColor.SURVIVAL,
            baseDamage = 31f,
            critMultiplier = 2.0f,
            critCondition = CritCondition.HEAVY_FINISHER,
            windupSec = 0.14f,
            activeSec = 0.13f,
            recoverySec = 0.20f,
            rangePx = 46f,
            knockback = 170f,
            comboCount = 2,
            colorHex = 0xFFFB7185.toInt(),
            description = "Wide sweeping scythe. Restores 4% HP on kill.",
            basePrice = 200
        ),

        // === 6 RANGED WEAPONS & SHIELD ===
        WeaponDef(
            id = WeaponId.QUICK_BOW,
            name = "Quick Bow",
            shortName = "Q.BOW",
            category = ItemCategory.RANGED,
            statColor = StatColor.TACTICS,
            baseDamage = 15f,
            critMultiplier = 2.1f,
            critCondition = CritCondition.TARGET_STACKED_ARROWS,
            windupSec = 0.05f,
            activeSec = 0.05f,
            recoverySec = 0.14f,
            rangePx = 240f,
            knockback = 60f,
            projectileSpeed = 380f,
            projectileCount = 1,
            colorHex = 0xFF34D399.toInt(),
            description = "Rapid arrows. Crits when 3+ arrows stuck in foe.",
            basePrice = 110
        ),
        WeaponDef(
            id = WeaponId.HEAVY_CROSSBOW,
            name = "Heavy Crossbow",
            shortName = "XBOW",
            category = ItemCategory.RANGED,
            statColor = StatColor.SURVIVAL,
            baseDamage = 14f,
            critMultiplier = 1.9f,
            critCondition = CritCondition.NONE,
            windupSec = 0.16f,
            activeSec = 0.06f,
            recoverySec = 0.30f,
            rangePx = 180f,
            knockback = 190f,
            projectileSpeed = 340f,
            projectileCount = 5,
            colorHex = 0xFFF59E0B.toInt(),
            description = "Shotgun burst of 5 piercing bolts.",
            basePrice = 175
        ),
        WeaponDef(
            id = WeaponId.FIREBRANDS,
            name = "Firebrands",
            shortName = "FIRE",
            category = ItemCategory.RANGED,
            statColor = StatColor.BRUTALITY,
            baseDamage = 16f,
            critMultiplier = 1.6f,
            critCondition = CritCondition.NONE,
            windupSec = 0.06f,
            activeSec = 0.06f,
            recoverySec = 0.18f,
            rangePx = 150f,
            knockback = 50f,
            appliesStatus = StatusType.BURN,
            statusDuration = 4.0f,
            projectileSpeed = 230f,
            projectileCount = 1,
            colorHex = 0xFFF97316.toInt(),
            description = "Lobbed fire bottles leaving burning ground.",
            basePrice = 145
        ),
        WeaponDef(
            id = WeaponId.ICE_BOW,
            name = "Ice Bow",
            shortName = "ICEBOW",
            category = ItemCategory.RANGED,
            statColor = StatColor.TACTICS,
            baseDamage = 12f,
            critMultiplier = 1.5f,
            critCondition = CritCondition.NONE,
            windupSec = 0.08f,
            activeSec = 0.06f,
            recoverySec = 0.24f,
            rangePx = 220f,
            knockback = 40f,
            appliesStatus = StatusType.FROZEN,
            statusDuration = 2.6f,
            projectileSpeed = 330f,
            projectileCount = 1,
            colorHex = 0xFF7DD3FC.toInt(),
            description = "Freezes enemies solid for 2.6s.",
            basePrice = 150
        ),
        WeaponDef(
            id = WeaponId.THROWING_KNIVES,
            name = "Throwing Knives",
            shortName = "KNIVES",
            category = ItemCategory.RANGED,
            statColor = StatColor.BRUTALITY,
            baseDamage = 12f,
            critMultiplier = 1.7f,
            critCondition = CritCondition.TARGET_BLEEDING,
            windupSec = 0.03f,
            activeSec = 0.04f,
            recoverySec = 0.11f,
            rangePx = 190f,
            knockback = 35f,
            appliesStatus = StatusType.BLEED,
            statusDuration = 3.5f,
            projectileSpeed = 420f,
            projectileCount = 1,
            colorHex = 0xFFE2E8F0.toInt(),
            description = "Auto-aimed fast bleeding knives.",
            basePrice = 135
        ),
        WeaponDef(
            id = WeaponId.SPIKED_SHIELD,
            name = "Spiked Shield",
            shortName = "SHIELD",
            category = ItemCategory.SHIELD,
            statColor = StatColor.SURVIVAL,
            baseDamage = 44f,
            critMultiplier = 2.0f,
            critCondition = CritCondition.PARRY_COUNTER,
            windupSec = 0.02f,
            activeSec = 0.22f,
            recoverySec = 0.16f,
            rangePx = 26f,
            knockback = 200f,
            appliesStatus = StatusType.STUN,
            statusDuration = 1.5f,
            colorHex = 0xFF2EC4B6.toInt(),
            description = "Hold to block. Tap to PARRY, stun & reflect!",
            basePrice = 140
        ),

        // === 6 ACTIVE SKILLS ===
        WeaponDef(
            id = WeaponId.CLUSTER_GRENADE,
            name = "Cluster Bomb",
            shortName = "BOMB",
            category = ItemCategory.SKILL,
            statColor = StatColor.BRUTALITY,
            baseDamage = 42f,
            critMultiplier = 1.5f,
            critCondition = CritCondition.NONE,
            windupSec = 0.05f,
            activeSec = 0.05f,
            recoverySec = 0.10f,
            rangePx = 140f,
            knockback = 210f,
            cooldownSec = 7.0f,
            appliesStatus = StatusType.BURN,
            statusDuration = 2.5f,
            projectileSpeed = 220f,
            colorHex = 0xFFEF4444.toInt(),
            description = "Explodes into 4 bouncing mini-bombs.",
            basePrice = 155
        ),
        WeaponDef(
            id = WeaponId.FROST_NOVA,
            name = "Frost Nova",
            shortName = "NOVA",
            category = ItemCategory.SKILL,
            statColor = StatColor.SURVIVAL,
            baseDamage = 24f,
            critMultiplier = 1.5f,
            critCondition = CritCondition.NONE,
            windupSec = 0.04f,
            activeSec = 0.12f,
            recoverySec = 0.10f,
            rangePx = 88f,
            knockback = 120f,
            cooldownSec = 6.5f,
            appliesStatus = StatusType.FROZEN,
            statusDuration = 3.0f,
            colorHex = 0xFF38BDF8.toInt(),
            description = "Freezes all nearby enemies in an ice ring.",
            basePrice = 150
        ),
        WeaponDef(
            id = WeaponId.SINEW_SLICER,
            name = "Sinew Slicer",
            shortName = "TURRET",
            category = ItemCategory.SKILL,
            statColor = StatColor.TACTICS,
            baseDamage = 11f,
            critMultiplier = 1.4f,
            critCondition = CritCondition.NONE,
            windupSec = 0.05f,
            activeSec = 0.05f,
            recoverySec = 0.10f,
            rangePx = 140f,
            knockback = 40f,
            cooldownSec = 9.0f,
            appliesStatus = StatusType.BLEED,
            statusDuration = 2.5f,
            colorHex = 0xFFA855F7.toInt(),
            description = "Deploys an auto-firing sawblade turret.",
            basePrice = 180
        ),
        WeaponDef(
            id = WeaponId.PHASER,
            name = "Phaser",
            shortName = "PHASER",
            category = ItemCategory.SKILL,
            statColor = StatColor.BRUTALITY,
            baseDamage = 18f,
            critMultiplier = 2.0f,
            critCondition = CritCondition.NONE,
            windupSec = 0.02f,
            activeSec = 0.04f,
            recoverySec = 0.06f,
            rangePx = 150f,
            knockback = 50f,
            cooldownSec = 3.2f,
            appliesStatus = StatusType.ROOT,
            statusDuration = 1.0f,
            colorHex = 0xFFE879F9.toInt(),
            description = "Blink behind nearest foe + 2x next melee hit!",
            basePrice = 170
        ),
        WeaponDef(
            id = WeaponId.FLAME_WAVE,
            name = "Flame Wave",
            shortName = "WAVE",
            category = ItemCategory.SKILL,
            statColor = StatColor.BRUTALITY,
            baseDamage = 34f,
            critMultiplier = 1.5f,
            critCondition = CritCondition.NONE,
            windupSec = 0.06f,
            activeSec = 0.10f,
            recoverySec = 0.10f,
            rangePx = 130f,
            knockback = 130f,
            cooldownSec = 6.0f,
            appliesStatus = StatusType.BURN,
            statusDuration = 4.0f,
            projectileSpeed = 240f,
            colorHex = 0xFFFB923C.toInt(),
            description = "Sends twin ground waves of fire outward.",
            basePrice = 160
        ),
        WeaponDef(
            id = WeaponId.WOLF_TRAP,
            name = "Wolf Trap",
            shortName = "TRAP",
            category = ItemCategory.SKILL,
            statColor = StatColor.SURVIVAL,
            baseDamage = 20f,
            critMultiplier = 1.5f,
            critCondition = CritCondition.NONE,
            windupSec = 0.04f,
            activeSec = 0.05f,
            recoverySec = 0.10f,
            rangePx = 50f,
            knockback = 0f,
            cooldownSec = 8.0f,
            appliesStatus = StatusType.ROOT,
            statusDuration = 4.5f,
            colorHex = 0xFF2DD4BF.toInt(),
            description = "Roots foes in place & amplifies damage +50%.",
            basePrice = 145
        )
    )

    private val byId: Map<WeaponId, WeaponDef> = allDefs.associateBy { it.id }

    fun get(id: WeaponId): WeaponDef = byId.getValue(id)

    val weaponsOnly: List<WeaponDef> = allDefs.filter { it.category != ItemCategory.SKILL }
    val skillsOnly: List<WeaponDef> = allDefs.filter { it.category == ItemCategory.SKILL }

    fun createInstance(id: WeaponId, tier: Int = 1, rng: Random = Random.Default, withAffix: Boolean = tier >= 2): WeaponInstance {
        val affix = if (withAffix && rng.nextFloat() < 0.7f) {
            WeaponAffix.entries[rng.nextInt(WeaponAffix.entries.size)]
        } else null
        return WeaponInstance(get(id), tier.coerceIn(1, 5), affix)
    }

    fun randomWeapon(tier: Int, rng: Random = Random.Default): WeaponInstance {
        val def = weaponsOnly[rng.nextInt(weaponsOnly.size)]
        return createInstance(def.id, tier, rng, withAffix = true)
    }

    fun randomSkill(tier: Int, rng: Random = Random.Default): WeaponInstance {
        val def = skillsOnly[rng.nextInt(skillsOnly.size)]
        return createInstance(def.id, tier, rng, withAffix = true)
    }

    fun randomAny(tier: Int, rng: Random = Random.Default): WeaponInstance {
        return if (rng.nextFloat() < 0.68f) randomWeapon(tier, rng) else randomSkill(tier, rng)
    }
}
