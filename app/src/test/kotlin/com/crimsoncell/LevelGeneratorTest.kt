package com.crimsoncell

import com.crimsoncell.enemy.EnemyType
import com.crimsoncell.world.BiomeCatalog
import com.crimsoncell.world.InteractableType
import com.crimsoncell.world.LevelGenerator
import com.crimsoncell.world.RoomKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelGeneratorTest {

    @Test
    fun generatesAllThreeBiomesWithValidStructureAndSpawns() {
        for (biome in BiomeCatalog.all) {
            val map = LevelGenerator.generate(biome, seed = 1337L + biome.id.index)
            assertTrue("Biome ${biome.name} should have at least 6 rooms", map.rooms.size >= 6)
            assertEquals(RoomKind.START, map.rooms.first().kind)
            assertTrue("Biome ${biome.name} should spawn enemies", map.enemies.isNotEmpty())

            // Spawn point must not be inside a solid tile
            val stx = map.worldToTile(map.spawnX)
            val sty = map.worldToTile(map.spawnY)
            assertFalse("Spawn must not be solid", map.isSolid(stx, sty))

            // Must contain Exit Portal, Merchant, Cell Forge, and at least one Scroll Shrine
            assertTrue(map.interactables.any { it.type == InteractableType.EXIT_PORTAL })
            assertTrue(map.interactables.any { it.type == InteractableType.MERCHANT })
            assertTrue(map.interactables.any { it.type == InteractableType.CELL_FORGE })
            assertTrue(map.interactables.any { it.type == InteractableType.SCROLL_SHRINE })
        }
    }

    @Test
    fun biomesTwoAndThreeSpawnCorrectBosses() {
        val sewers = LevelGenerator.generate(BiomeCatalog.forIndex(2), seed = 2026L)
        assertNotNull(sewers.bossRoom)
        assertTrue(sewers.enemies.any { it.type == EnemyType.BOSS_IRON_WARDEN })

        val spire = LevelGenerator.generate(BiomeCatalog.forIndex(3), seed = 2027L)
        assertNotNull(spire.bossRoom)
        assertTrue(spire.enemies.any { it.type == EnemyType.BOSS_CRIMSON_REAPER })
    }
}
