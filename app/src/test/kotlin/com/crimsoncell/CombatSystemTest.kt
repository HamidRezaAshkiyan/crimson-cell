package com.crimsoncell

import com.crimsoncell.combat.CombatEventListener
import com.crimsoncell.combat.CombatSystem
import com.crimsoncell.combat.StatusType
import com.crimsoncell.combat.WeaponCatalog
import com.crimsoncell.combat.WeaponId
import com.crimsoncell.enemy.Enemy
import com.crimsoncell.enemy.EnemyType
import com.crimsoncell.entity.ParticleSystem
import com.crimsoncell.entity.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CombatSystemTest {

    private val silentEvents = object : CombatEventListener {
        override fun onSfx(sfx: String) = Unit
        override fun onHitstop(durationSec: Float) = Unit
        override fun onScreenShake(intensity: Float) = Unit
        override fun onHaptic(durationMs: Long, amplitude: Int) = Unit
    }

    @Test
    fun catalogHasAllTwentyDistinctWeaponsAndSkills() {
        assertEquals(20, WeaponCatalog.allDefs.size)
        assertEquals(20, WeaponCatalog.allDefs.map { it.id }.toSet().size)
    }

    @Test
    fun evaluatesMeleeCritConditionsAccurately() {
        val combat = CombatSystem()
        val player = Player(x = 40f, y = 100f)
        val enemy = Enemy(EnemyType.UNDEAD_RUNNER, x = 80f, y = 100f)

        // Twin Daggers: 3rd combo hit crits
        val daggers = WeaponCatalog.get(WeaponId.TWIN_DAGGERS)
        player.comboIndex = 0
        assertFalse(combat.evaluateMeleeCrit(daggers, player, enemy))
        player.comboIndex = 2
        assertTrue(combat.evaluateMeleeCrit(daggers, player, enemy))

        // Assassin Dagger: backstab crits when player is behind enemy facing direction
        val assassin = WeaponCatalog.get(WeaponId.ASSASSINS_DAGGER)
        enemy.facing = 1 // enemy facing right, player is on left -> backstab!
        assertTrue(combat.evaluateMeleeCrit(assassin, player, enemy))
        enemy.facing = -1 // enemy facing player -> not backstab
        assertFalse(combat.evaluateMeleeCrit(assassin, player, enemy))

        // Frost Blade: crits frozen enemy
        val frost = WeaponCatalog.get(WeaponId.FROST_BLADE)
        assertFalse(combat.evaluateMeleeCrit(frost, player, enemy))
        enemy.applyStatus(StatusType.FROZEN, 2.0f)
        assertTrue(combat.evaluateMeleeCrit(frost, player, enemy))
    }

    @Test
    fun shieldParryStunsAttackerAndDealsCritDamage() {
        val combat = CombatSystem()
        val particles = ParticleSystem()
        val player = Player(x = 50f, y = 100f).apply {
            weapon2 = WeaponCatalog.createInstance(WeaponId.SPIKED_SHIELD, 1, withAffix = false)
            parryWindowTimer = 0.2f
        }
        val enemy = Enemy(EnemyType.DARK_SLASHER, x = 70f, y = 100f)
        val hpBefore = enemy.hp

        val parried = combat.tryParryMeleeAttack(enemy, player, particles, silentEvents, Random(1))
        assertTrue(parried)
        assertTrue("Enemy should take heavy parry damage", enemy.hp < hpBefore)
        assertTrue("Enemy should be stunned after parry", enemy.isStunned)
    }

    @Test
    fun rallyMechanicRecoversLostHealthWhenDealingDamage() {
        val combat = CombatSystem()
        val particles = ParticleSystem()
        val player = Player(x = 50f, y = 100f)
        player.maxHp = 100f
        player.hp = 100f
        player.rallyHp = 100f

        player.takeDamage(40f, 0f)
        assertEquals(60f, player.hp, 0.01f)
        assertEquals(100f, player.rallyHp, 0.01f)

        val enemy = Enemy(EnemyType.GOLEM_BRUISER, x = 75f, y = 100f)
        combat.damageEnemy(enemy, 30f, false, 0f, null, 0f, player, particles, silentEvents, Random(1))
        assertTrue("Player should recover Rally HP after dealing damage", player.hp > 60f)
    }
}
