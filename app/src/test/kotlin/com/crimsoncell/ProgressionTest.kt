package com.crimsoncell

import com.crimsoncell.progression.ForgeUpgrade
import com.crimsoncell.progression.InMemoryProgressionRepository
import com.crimsoncell.progression.ProgressionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionTest {

    @Test
    fun cellForgePurchasesUpgradeAndPersistsState() {
        val repo = InMemoryProgressionRepository(ProgressionState(bankedCells = 50))
        val state = repo.load()

        assertTrue(state.canBuy(ForgeUpgrade.FLASK_CHARGES))
        assertTrue(state.buy(ForgeUpgrade.FLASK_CHARGES))
        assertEquals(1, state.flaskLevel)
        assertEquals(3, state.startingFlaskCharges)
        assertEquals(35, state.bankedCells)

        repo.save(state)
        val reloaded = repo.load()
        assertEquals(1, reloaded.flaskLevel)
        assertEquals(35, reloaded.bankedCells)

        // Cannot buy when cells insufficient
        reloaded.bankedCells = 2
        assertFalse(reloaded.buy(ForgeUpgrade.WEAPON_TIER))
    }
}
