package com.crimsoncell.engine

import com.crimsoncell.combat.CombatEventListener
import com.crimsoncell.combat.CombatSystem
import com.crimsoncell.combat.ItemCategory
import com.crimsoncell.combat.WeaponCatalog
import com.crimsoncell.combat.WeaponInstance
import com.crimsoncell.enemy.Enemy
import com.crimsoncell.enemy.EnemyAI
import com.crimsoncell.entity.ParticleSystem
import com.crimsoncell.entity.Player
import com.crimsoncell.input.InputState
import com.crimsoncell.progression.ForgeUpgrade
import com.crimsoncell.progression.ProgressionRepository
import com.crimsoncell.progression.ProgressionState
import com.crimsoncell.world.BiomeCatalog
import com.crimsoncell.world.Interactable
import com.crimsoncell.world.InteractableType
import com.crimsoncell.world.LevelGenerator
import com.crimsoncell.world.LevelMap
import kotlin.math.hypot
import kotlin.random.Random

enum class GameMode {
    TITLE, PLAYING, PAUSED, SHOP, FORGE, GAME_OVER, VICTORY
}

interface EnginePlatformHooks {
    fun playSfx(name: String)
    fun setMusicBiome(biomeIndex: Int, muted: Boolean)
    fun triggerHaptic(durationMs: Long, amplitude: Int)
}

object NoOpPlatformHooks : EnginePlatformHooks {
    override fun playSfx(name: String) = Unit
    override fun setMusicBiome(biomeIndex: Int, muted: Boolean) = Unit
    override fun triggerHaptic(durationMs: Long, amplitude: Int) = Unit
}

data class ShopOffer(
    val weapon: WeaponInstance?,
    val isFoodHeal: Boolean = false,
    val price: Int,
    var sold: Boolean = false
)

class GameEngine(
    private val progressionRepo: ProgressionRepository,
    private val hooks: EnginePlatformHooks = NoOpPlatformHooks,
    private val rng: Random = Random.Default
) : CombatEventListener {

    var mode: GameMode = GameMode.TITLE
    var progression: ProgressionState = progressionRepo.load()

    var currentBiomeIndex: Int = 1
    var map: LevelMap = LevelGenerator.generate(BiomeCatalog.forIndex(1), rng.nextLong())
    val player: Player = Player()
    val combat: CombatSystem = CombatSystem()
    val particles: ParticleSystem = ParticleSystem()

    // Camera (virtual 480x270 viewport)
    var cameraX: Float = 0f
    var cameraY: Float = 0f
    var shakeTimer: Float = 0f
    var shakeIntensity: Float = 0f
    var hitstopTimer: Float = 0f
    var biomeBannerTimer: Float = 0f
    var runTimeSec: Float = 0f

    // Nearby interactable prompt
    var nearbyInteractable: Interactable? = null

    // Menu cursors
    var pauseSelection: Int = 0
    var shopSelection: Int = 0
    var forgeSelection: Int = 0
    var showTouchControlsOverride: Boolean? = null
    val shopOffers = ArrayList<ShopOffer>()

    init {
        resetPlayerForCurrentMap(newRun = true)
    }

    fun startNewRun() {
        currentBiomeIndex = 1
        progression.totalRuns++
        progressionRepo.save(progression)
        runTimeSec = 0f
        loadBiome(1, newRun = true)
        mode = GameMode.PLAYING
        hooks.playSfx("scroll")
    }

    fun loadBiome(biomeIndex: Int, newRun: Boolean) {
        currentBiomeIndex = biomeIndex
        if (biomeIndex > progression.bestBiomeReached) {
            progression.bestBiomeReached = biomeIndex
            progressionRepo.save(progression)
        }
        val biome = BiomeCatalog.forIndex(biomeIndex)
        map = LevelGenerator.generate(biome, rng.nextLong())
        combat.clear()
        particles.clear()
        resetPlayerForCurrentMap(newRun)
        generateShopOffers(biome.baseTier)
        biomeBannerTimer = 2.8f
        hooks.setMusicBiome(biomeIndex, progression.audioMuted)
    }

    private fun resetPlayerForCurrentMap(newRun: Boolean) {
        if (newRun) {
            player.resetForNewRun(
                spawnX = map.spawnX,
                spawnY = map.spawnY,
                startingTier = progression.startingWeaponTier,
                startingFlasks = progression.startingFlaskCharges,
                startingGold = progression.startingGold,
                bonusMaxHp = progression.bonusMaxHp
            )
        } else {
            player.x = map.spawnX
            player.y = map.spawnY
            player.vx = 0f
            player.vy = 0f
            player.flaskCharges = (player.flaskCharges + 1).coerceAtMost(player.maxFlaskCharges)
            player.hp = (player.hp + player.maxHp * 0.35f).coerceAtMost(player.maxHp)
            player.rallyHp = player.hp
            player.resetScarf()
        }
        cameraX = (player.centerX - VIRTUAL_W * 0.5f).coerceIn(0f, (map.widthPx - VIRTUAL_W).coerceAtLeast(0).toFloat())
        cameraY = (player.centerY - VIRTUAL_H * 0.5f).coerceIn(0f, (map.heightPx - VIRTUAL_H).coerceAtLeast(0).toFloat())
    }

    private fun generateShopOffers(tier: Int) {
        shopOffers.clear()
        repeat(3) {
            val item = WeaponCatalog.randomAny(tier + 1, rng)
            shopOffers.add(ShopOffer(weapon = item, price = item.def.basePrice + tier * 30))
        }
        shopOffers.add(ShopOffer(weapon = null, isFoodHeal = true, price = 90))
    }

    fun step(dt: Float, input: InputState) {
        input.updateBuffers(dt)

        when (mode) {
            GameMode.TITLE -> updateTitle(input)
            GameMode.PLAYING -> updatePlaying(dt, input)
            GameMode.PAUSED -> updatePause(input)
            GameMode.SHOP -> updateShop(input)
            GameMode.FORGE -> updateForge(input)
            GameMode.GAME_OVER, GameMode.VICTORY -> updateEndScreen(input)
        }

        input.clearEdgeTriggers()
    }

    private fun updateTitle(input: InputState) {
        if (input.menuConfirmJustPressed || input.jumpJustPressed || input.weapon1JustPressed || input.pauseJustPressed) {
            startNewRun()
        }
    }

    private fun updatePlaying(dt: Float, input: InputState) {
        if (input.pauseJustPressed) {
            pauseSelection = 0
            mode = GameMode.PAUSED
            hooks.playSfx("menu")
            return
        }

        runTimeSec += dt
        biomeBannerTimer = (biomeBannerTimer - dt).coerceAtLeast(0f)

        // Hitstop freeze frame for juicy crits & parries
        if (hitstopTimer > 0f) {
            hitstopTimer = (hitstopTimer - dt).coerceAtLeast(0f)
            updateCamera(dt)
            return
        }

        // Player attacks & skills
        if (input.consumeWeapon1Buffer()) {
            player.tryStartWeaponAttack(1)
        } else if (input.consumeWeapon2Buffer()) {
            player.tryStartWeaponAttack(2)
        }

        if (input.skill1JustPressed) {
            combat.activateSkill(1, player, map, particles, this, rng)
        }
        if (input.skill2JustPressed) {
            combat.activateSkill(2, player, map, particles, this, rng)
        }

        if (input.flaskJustPressed) {
            if (player.useFlask()) {
                hooks.playSfx("heal")
                particles.spawnBurst(player.centerX, player.centerY, 16, 0xFF4ADE80.toInt(), 40f, 120f, -40f, 2.5f, 0.45f, rng)
                particles.spawnText(player.centerX, player.y - 10f, "+HEAL!", 0xFF4ADE80.toInt(), isCrit = true)
            }
        }

        // Update player, enemies, combat, particles
        player.update(dt, input, map, particles)
        map.markRoomDiscovered(player.centerX, player.centerY)

        EnemyAI.updateAll(dt, map, player, combat, particles, this, rng)
        combat.update(dt, player, map, particles, this, rng)
        particles.update(dt)

        // Check boss defeated flag
        val activeBoss = activeBoss()
        if (map.bossRoom != null && activeBoss == null) {
            map.bossDefeated = true
        }

        // Nearby interactables
        nearbyInteractable = findNearbyInteractable()
        if (input.interactJustPressed && nearbyInteractable != null) {
            interactWith(nearbyInteractable!!)
        }

        // Check death
        if (!player.isAlive) {
            bankRunProgress(victory = false)
            mode = GameMode.GAME_OVER
            hooks.playSfx("death")
        }

        updateCamera(dt)
    }

    private fun updatePause(input: InputState) {
        val itemCount = 4 // Resume, Audio Toggle, Touch Controls Toggle, Restart Run
        if (input.menuUpJustPressed) {
            pauseSelection = (pauseSelection - 1 + itemCount) % itemCount
            hooks.playSfx("menu")
        } else if (input.menuDownJustPressed) {
            pauseSelection = (pauseSelection + 1) % itemCount
            hooks.playSfx("menu")
        } else if (input.pauseJustPressed || input.menuBackJustPressed) {
            mode = GameMode.PLAYING
            hooks.playSfx("menu")
        } else if (input.menuConfirmJustPressed || input.jumpJustPressed) {
            when (pauseSelection) {
                0 -> mode = GameMode.PLAYING
                1 -> {
                    progression.audioMuted = !progression.audioMuted
                    progressionRepo.save(progression)
                    hooks.setMusicBiome(currentBiomeIndex, progression.audioMuted)
                }
                2 -> {
                    showTouchControlsOverride = when (showTouchControlsOverride) {
                        null -> true
                        true -> false
                        false -> null
                    }
                }
                3 -> startNewRun()
            }
            hooks.playSfx("menu")
        }
    }

    private fun updateShop(input: InputState) {
        if (input.menuBackJustPressed || input.pauseJustPressed) {
            mode = GameMode.PLAYING
            hooks.playSfx("menu")
            return
        }
        if (shopOffers.isEmpty()) return
        if (input.menuUpJustPressed) {
            shopSelection = (shopSelection - 1 + shopOffers.size) % shopOffers.size
            hooks.playSfx("menu")
        } else if (input.menuDownJustPressed) {
            shopSelection = (shopSelection + 1) % shopOffers.size
            hooks.playSfx("menu")
        } else if (input.menuConfirmJustPressed || input.jumpJustPressed || input.interactJustPressed) {
            val offer = shopOffers[shopSelection]
            if (!offer.sold && player.gold >= offer.price) {
                player.gold -= offer.price
                offer.sold = true
                if (offer.isFoodHeal) {
                    player.hp = (player.hp + player.maxHp * 0.5f).coerceAtMost(player.maxHp)
                    player.rallyHp = player.hp
                    hooks.playSfx("heal")
                } else if (offer.weapon != null) {
                    equipItem(offer.weapon)
                    hooks.playSfx("scroll")
                }
            } else {
                hooks.playSfx("clang")
            }
        }
    }

    private fun updateForge(input: InputState) {
        if (input.menuBackJustPressed || input.pauseJustPressed) {
            mode = GameMode.PLAYING
            hooks.playSfx("menu")
            return
        }
        val upgrades = ForgeUpgrade.entries
        if (input.menuUpJustPressed) {
            forgeSelection = (forgeSelection - 1 + upgrades.size) % upgrades.size
            hooks.playSfx("menu")
        } else if (input.menuDownJustPressed) {
            forgeSelection = (forgeSelection + 1) % upgrades.size
            hooks.playSfx("menu")
        } else if (input.menuConfirmJustPressed || input.jumpJustPressed || input.interactJustPressed) {
            // Bank any carried run cells first so user can spend them immediately!
            if (player.runCells > 0) {
                progression.bankedCells += player.runCells
                player.runCells = 0
                progressionRepo.save(progression)
            }
            val chosen = upgrades[forgeSelection]
            if (progression.buy(chosen)) {
                progressionRepo.save(progression)
                // Apply immediate vitality/flask bonus if purchased in start room
                if (chosen == ForgeUpgrade.VITALITY) {
                    player.maxHp += 25f
                    player.hp += 25f
                    player.rallyHp = player.hp
                } else if (chosen == ForgeUpgrade.FLASK_CHARGES) {
                    player.maxFlaskCharges++
                    player.flaskCharges++
                }
                hooks.playSfx("scroll")
            } else {
                hooks.playSfx("clang")
            }
        }
    }

    private fun updateEndScreen(input: InputState) {
        if (input.menuConfirmJustPressed || input.jumpJustPressed || input.weapon1JustPressed) {
            startNewRun()
        }
    }

    private fun findNearbyInteractable(): Interactable? {
        return map.interactables
            .filter { !it.used && it.type != InteractableType.CRATE }
            .minByOrNull { hypot((it.x - player.centerX).toDouble(), (it.y - player.centerY).toDouble()) }
            ?.takeIf { hypot((it.x - player.centerX).toDouble(), (it.y - player.centerY).toDouble()) <= 32f }
    }

    private fun interactWith(item: Interactable) {
        when (item.type) {
            InteractableType.CHEST, InteractableType.WEAPON_PEDESTAL -> {
                val weapon = item.weapon ?: WeaponCatalog.randomAny(map.biome.baseTier, rng)
                equipItem(weapon)
                item.used = true
                hooks.playSfx("scroll")
                particles.spawnBurst(item.x, item.y, 18, weapon.def.colorHex, 50f, 150f, 180f, 2.5f, 0.45f, rng)
                particles.spawnText(player.centerX, player.y - 14f, weapon.displayName, weapon.def.colorHex, isCrit = true)
            }
            InteractableType.SCROLL_SHRINE -> {
                val color = item.scrollColor ?: return
                player.applyScroll(color)
                item.used = true
                hooks.playSfx("scroll")
                particles.spawnBurst(item.x, item.y, 20, color.colorHex, 60f, 160f, 100f, 2.5f, 0.5f, rng)
                particles.spawnText(player.centerX, player.y - 14f, "+1 ${color.label}!", color.colorHex, isCrit = true)
            }
            InteractableType.MERCHANT -> {
                shopSelection = 0
                mode = GameMode.SHOP
                hooks.playSfx("menu")
            }
            InteractableType.CELL_FORGE -> {
                // Bank run cells on opening the forge
                if (player.runCells > 0) {
                    progression.bankedCells += player.runCells
                    player.runCells = 0
                    progressionRepo.save(progression)
                }
                forgeSelection = 0
                mode = GameMode.FORGE
                hooks.playSfx("menu")
            }
            InteractableType.EXIT_PORTAL -> {
                if (map.bossRoom != null && !map.bossDefeated) {
                    particles.spawnText(player.centerX, player.y - 12f, "DEFEAT BOSS FIRST!", 0xFFEF4444.toInt())
                    hooks.playSfx("clang")
                    return
                }
                // Bank collected cells between biomes!
                progression.bankedCells += player.runCells
                player.runCells = 0
                progressionRepo.save(progression)

                if (currentBiomeIndex < BiomeCatalog.all.size) {
                    loadBiome(currentBiomeIndex + 1, newRun = false)
                    hooks.playSfx("scroll")
                } else {
                    bankRunProgress(victory = true)
                    mode = GameMode.VICTORY
                    hooks.playSfx("scroll")
                }
            }
            InteractableType.CRATE -> Unit
        }
    }

    fun equipItem(item: WeaponInstance) {
        when (item.def.category) {
            ItemCategory.MELEE -> player.weapon1 = item
            ItemCategory.RANGED, ItemCategory.SHIELD -> player.weapon2 = item
            ItemCategory.SKILL -> {
                if (player.skill1 == null) player.skill1 = item
                else if (player.skill2 == null) player.skill2 = item
                else {
                    player.skill2 = player.skill1
                    player.skill1 = item
                }
            }
        }
    }

    private fun bankRunProgress(victory: Boolean) {
        progression.bankedCells += player.runCells
        progression.totalKills += player.kills
        if (victory) progression.victories++
        progressionRepo.save(progression)
    }

    fun activeBoss(): Enemy? = map.enemies.firstOrNull { it.type.isBoss && it.isAlive }

    private fun updateCamera(dt: Float) {
        val lookAheadX = player.facing * 42f
        val targetX = (player.centerX + lookAheadX - VIRTUAL_W * 0.5f)
            .coerceIn(0f, (map.widthPx - VIRTUAL_W).coerceAtLeast(0).toFloat())
        val targetY = (player.centerY - VIRTUAL_H * 0.52f)
            .coerceIn(0f, (map.heightPx - VIRTUAL_H).coerceAtLeast(0).toFloat())

        cameraX += (targetX - cameraX) * (8.5f * dt).coerceAtMost(1f)
        cameraY += (targetY - cameraY) * (7.5f * dt).coerceAtMost(1f)

        if (shakeTimer > 0f) {
            shakeTimer = (shakeTimer - dt).coerceAtLeast(0f)
        } else {
            shakeIntensity = 0f
        }
    }

    // CombatEventListener implementation
    override fun onSfx(sfx: String) = hooks.playSfx(sfx)
    override fun onHitstop(durationSec: Float) {
        hitstopTimer = maxOf(hitstopTimer, durationSec)
    }
    override fun onScreenShake(intensity: Float) {
        shakeIntensity = maxOf(shakeIntensity, intensity)
        shakeTimer = maxOf(shakeTimer, 0.16f)
    }
    override fun onHaptic(durationMs: Long, amplitude: Int) = hooks.triggerHaptic(durationMs, amplitude)

    companion object {
        const val VIRTUAL_W = 480
        const val VIRTUAL_H = 270
        const val FIXED_DT = 1f / 60f
    }
}
