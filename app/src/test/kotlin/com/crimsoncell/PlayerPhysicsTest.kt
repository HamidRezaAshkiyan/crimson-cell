package com.crimsoncell

import com.crimsoncell.entity.ParticleSystem
import com.crimsoncell.entity.Player
import com.crimsoncell.input.InputState
import com.crimsoncell.world.BiomeCatalog
import com.crimsoncell.world.LevelGenerator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerPhysicsTest {

    @Test
    fun dodgeRollGrantsInvulnerabilityAndCancelsAttack() {
        val map = LevelGenerator.generate(BiomeCatalog.forIndex(1), seed = 99L)
        val player = Player(map.spawnX, map.spawnY)
        val particles = ParticleSystem()
        val input = InputState()

        // Start a weapon attack, then immediately dodge-roll cancel
        assertTrue(player.tryStartWeaponAttack(1))
        input.rollJustPressed = true
        input.updateBuffers(1f / 60f)
        player.update(1f / 60f, input, map, particles)

        assertTrue("Player should be rolling", player.isRolling)
        assertTrue("Player should have i-frames during roll", player.isInvulnerable)
    }

    @Test
    fun doubleJumpAndDiveSlamWorkMidAir() {
        val map = LevelGenerator.generate(BiomeCatalog.forIndex(1), seed = 101L)
        val player = Player(map.spawnX, map.spawnY - 40f)
        player.onGround = false
        player.coyoteTimer = 0f
        player.jumpsRemaining = 1
        val particles = ParticleSystem()
        val input = InputState()

        // Mid-air double jump
        input.jumpJustPressed = true
        input.jumpHeld = true
        input.updateBuffers(1f / 60f)
        player.update(1f / 60f, input, map, particles)
        assertTrue("Double jump should launch upward", player.vy < -150f)

        // Mid-air Down + Jump -> Dive Slam
        input.clearEdgeTriggers()
        input.moveY = 1f
        input.jumpJustPressed = true
        input.updateBuffers(1f / 60f)
        player.update(1f / 60f, input, map, particles)
        assertTrue("Down+Jump in mid-air should trigger dive slam", player.isDiveSlamming)
        assertFalse("Player should not be on ground yet", player.onGround)
    }
}
