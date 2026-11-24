package com.crimsoncell.world

import com.crimsoncell.combat.StatColor
import com.crimsoncell.combat.WeaponCatalog
import com.crimsoncell.enemy.Enemy
import kotlin.random.Random

/**
 * Generates a connected, side-scrolling roguelite dungeon with guaranteed
 * reachable platforms, vertical shafts, treasure rooms, merchant shop,
 * scroll shrines, elite rooms, and a boss/exit chamber.
 */
object LevelGenerator {

    fun generate(biome: BiomeConfig, seed: Long = System.nanoTime()): LevelMap {
        val rng = Random(seed)
        val widthTiles = 168
        val heightTiles = 44
        val map = LevelMap(biome, widthTiles, heightTiles)

        // Room blueprint along a dynamic horizontal + stepped vertical path
        val roomSpecs = listOf(
            RoomKind.START to 16,
            RoomKind.COMBAT to 22,
            RoomKind.SHRINE to 15,
            RoomKind.COMBAT to 24,
            RoomKind.SHOP to 17,
            RoomKind.TREASURE to 15,
            RoomKind.ELITE to 22,
            if (biome.bossType != null) RoomKind.BOSS to 28 else RoomKind.EXIT to 20
        )

        var cursorX = 3
        var cursorY = 18

        for ((index, spec) in roomSpecs.withIndex()) {
            val (kind, baseW) = spec
            val roomW = baseW.coerceAtMost(widthTiles - cursorX - 3)
            if (roomW < 10) break
            val roomH = when (kind) {
                RoomKind.BOSS -> 16
                RoomKind.COMBAT, RoomKind.ELITE -> 14
                else -> 11
            }

            // Slight vertical elevation variation between rooms (always reachable)
            val prevRoom = map.rooms.lastOrNull()
            if (prevRoom != null && kind != RoomKind.BOSS) {
                val shift = rng.nextInt(-3, 4)
                cursorY = (cursorY + shift).coerceIn(6, heightTiles - roomH - 4)
            } else if (kind == RoomKind.BOSS) {
                cursorY = (cursorY - 2).coerceIn(6, heightTiles - roomH - 4)
            }

            val room = RoomRect(index, kind, cursorX, cursorY, roomW, roomH, discovered = (index == 0))
            map.rooms.add(room)
            carveRoom(map, room, rng)

            // Connect to previous room with a clean doorway + stepping platforms if vertically offset
            if (prevRoom != null) {
                connectRooms(map, prevRoom, room)
            }

            // Populate room contents
            populateRoom(map, room, biome, rng)

            cursorX += roomW + 2
        }

        // Set spawn in START room
        val startRoom = map.rooms.first()
        map.spawnX = (startRoom.tileX + 3) * LevelMap.TILE_SIZE.toFloat()
        map.spawnY = (startRoom.tileY + startRoom.tileH - 2) * LevelMap.TILE_SIZE.toFloat() - 6f

        // Set exit portal in final room
        val lastRoom = map.rooms.last()
        val floorY = (lastRoom.tileY + lastRoom.tileH - 1) * LevelMap.TILE_SIZE.toFloat()
        map.exitX = (lastRoom.tileX + lastRoom.tileW - 4) * LevelMap.TILE_SIZE.toFloat()
        map.exitY = floorY - 24f
        map.interactables.add(
            Interactable(
                type = InteractableType.EXIT_PORTAL,
                x = map.exitX,
                y = map.exitY,
                label = if (biome.id.index < 3) "NEXT BIOME" else "VICTORY GATE"
            )
        )

        if (lastRoom.kind == RoomKind.BOSS) {
            map.bossRoom = lastRoom
        }

        return map
    }

    private fun carveRoom(map: LevelMap, room: RoomRect, rng: Random) {
        for (ty in room.tileY until room.tileY + room.tileH) {
            for (tx in room.tileX until room.tileX + room.tileW) {
                map.setTile(tx, ty, TileType.EMPTY)
            }
        }

        val floorY = room.tileY + room.tileH - 1
        // Place torches along back wall
        var torchX = room.tileX + 3
        while (torchX < room.tileX + room.tileW - 2) {
            map.torches.add(
                TorchLight(
                    x = torchX * LevelMap.TILE_SIZE + 8f,
                    y = (floorY - 3) * LevelMap.TILE_SIZE.toFloat()
                )
            )
            torchX += 6
        }

        // Add multi-tier one-way platforms inside combat/elite/boss/treasure rooms
        when (room.kind) {
            RoomKind.COMBAT, RoomKind.ELITE -> {
                val midY1 = floorY - 4
                val midY2 = floorY - 8
                placePlatform(map, room.tileX + 3, room.tileX + room.tileW / 2 - 1, midY1)
                placePlatform(map, room.tileX + room.tileW / 2 + 1, room.tileX + room.tileW - 4, midY1)
                placePlatform(map, room.tileX + room.tileW / 3, room.tileX + (room.tileW * 2) / 3, midY2)

                // Optional floor hazard pit (spikes or acid in Sewers) with safe platform above
                if (room.tileW >= 18 && rng.nextFloat() < 0.65f) {
                    val hazard = if (map.biome.id == BiomeId.TOXIC_SEWERS) TileType.ACID else TileType.SPIKES
                    val pitStart = room.tileX + room.tileW / 2 - 1
                    for (tx in pitStart..(pitStart + 2)) {
                        map.setTile(tx, floorY, hazard)
                    }
                }
            }
            RoomKind.BOSS -> {
                // Clean, wide arena with two high vantage platforms for dodging
                placePlatform(map, room.tileX + 4, room.tileX + 9, floorY - 4)
                placePlatform(map, room.tileX + room.tileW - 10, room.tileX + room.tileW - 5, floorY - 4)
                placePlatform(map, room.tileX + room.tileW / 2 - 3, room.tileX + room.tileW / 2 + 3, floorY - 8)
            }
            RoomKind.TREASURE, RoomKind.SHRINE -> {
                placePlatform(map, room.tileX + 4, room.tileX + room.tileW - 5, floorY - 4)
            }
            else -> Unit
        }
    }

    private fun placePlatform(map: LevelMap, startX: Int, endX: Int, ty: Int) {
        for (tx in startX..endX) {
            if (map.getTile(tx, ty) == TileType.EMPTY) {
                map.setTile(tx, ty, TileType.ONE_WAY)
            }
        }
    }

    private fun connectRooms(map: LevelMap, left: RoomRect, right: RoomRect) {
        val corridorStartX = left.tileX + left.tileW
        val corridorEndX = right.tileX - 1
        val leftFloor = left.tileY + left.tileH - 1
        val rightFloor = right.tileY + right.tileH - 1
        val topOpen = minOf(leftFloor, rightFloor) - 4
        val botOpen = maxOf(leftFloor, rightFloor)

        for (tx in corridorStartX..corridorEndX) {
            for (ty in topOpen..botOpen) {
                map.setTile(tx, ty, TileType.EMPTY)
            }
            // Ensure solid floor underneath the corridor
            map.setTile(tx, botOpen + 1, TileType.SOLID)
        }

        // If one room is higher than the other, add stepping one-way ledges
        if (leftFloor != rightFloor) {
            val higher = minOf(leftFloor, rightFloor)
            val lower = maxOf(leftFloor, rightFloor)
            var stepY = lower - 3
            while (stepY >= higher) {
                for (tx in (corridorStartX - 1)..(corridorEndX + 1)) {
                    if (map.getTile(tx, stepY) == TileType.EMPTY) {
                        map.setTile(tx, stepY, TileType.ONE_WAY)
                    }
                }
                stepY -= 3
            }
        }
    }

    private fun populateRoom(map: LevelMap, room: RoomRect, biome: BiomeConfig, rng: Random) {
        val floorPx = (room.tileY + room.tileH) * LevelMap.TILE_SIZE.toFloat()

        when (room.kind) {
            RoomKind.START -> {
                // Place Cell Forge in start room so player can spend banked Cells between runs!
                map.interactables.add(
                    Interactable(
                        type = InteractableType.CELL_FORGE,
                        x = (room.tileX + room.tileW - 5) * LevelMap.TILE_SIZE.toFloat(),
                        y = floorPx - 20f,
                        label = "CELL FORGE"
                    )
                )
                // Starter bonus weapon pedestal
                map.interactables.add(
                    Interactable(
                        type = InteractableType.WEAPON_PEDESTAL,
                        x = (room.tileX + 7) * LevelMap.TILE_SIZE.toFloat(),
                        y = floorPx - 14f,
                        weapon = WeaponCatalog.randomWeapon(biome.baseTier, rng),
                        label = "WEAPON"
                    )
                )
            }
            RoomKind.COMBAT -> {
                val enemyCount = rng.nextInt(3, 6)
                for (i in 0 until enemyCount) {
                    val type = biome.enemyPool[rng.nextInt(biome.enemyPool.size)]
                    val ex = (room.tileX + 3 + rng.nextInt(room.tileW - 6)) * LevelMap.TILE_SIZE.toFloat()
                    val ey = if (type.isFlying) {
                        (room.tileY + 3 + rng.nextInt(4)) * LevelMap.TILE_SIZE.toFloat()
                    } else if (i % 2 == 1) {
                        (room.tileY + room.tileH - 5) * LevelMap.TILE_SIZE.toFloat() - type.height
                    } else {
                        floorPx - type.height
                    }
                    map.enemies.add(Enemy(type, ex, ey, biome.difficultyScale))
                }
                // Destructible crates
                repeat(2) {
                    val cx = (room.tileX + 2 + rng.nextInt(room.tileW - 4)) * LevelMap.TILE_SIZE.toFloat()
                    map.interactables.add(Interactable(InteractableType.CRATE, cx, floorPx - 12f))
                }
            }
            RoomKind.SHRINE -> {
                val color = StatColor.entries[rng.nextInt(StatColor.entries.size)]
                map.interactables.add(
                    Interactable(
                        type = InteractableType.SCROLL_SHRINE,
                        x = (room.tileX + room.tileW / 2) * LevelMap.TILE_SIZE.toFloat(),
                        y = floorPx - 18f,
                        scrollColor = color,
                        label = "${color.label} SCROLL"
                    )
                )
                // Guarding enemy
                val guard = biome.enemyPool[rng.nextInt(biome.enemyPool.size)]
                map.enemies.add(
                    Enemy(guard, (room.tileX + room.tileW - 4) * LevelMap.TILE_SIZE.toFloat(), floorPx - guard.height, biome.difficultyScale)
                )
            }
            RoomKind.SHOP -> {
                map.interactables.add(
                    Interactable(
                        type = InteractableType.MERCHANT,
                        x = (room.tileX + room.tileW / 2) * LevelMap.TILE_SIZE.toFloat(),
                        y = floorPx - 20f,
                        label = "MERCHANT SHOP"
                    )
                )
            }
            RoomKind.TREASURE -> {
                map.interactables.add(
                    Interactable(
                        type = InteractableType.CHEST,
                        x = (room.tileX + room.tileW / 2) * LevelMap.TILE_SIZE.toFloat(),
                        y = floorPx - 14f,
                        weapon = WeaponCatalog.randomAny(biome.baseTier + 1, rng),
                        label = "TREASURE CHEST"
                    )
                )
            }
            RoomKind.ELITE -> {
                // 1 Elite + 2 minions + guaranteed Scroll Shrine reward
                val eliteType = biome.enemyPool[rng.nextInt(biome.enemyPool.size)]
                map.enemies.add(
                    Enemy(
                        type = eliteType,
                        x = (room.tileX + room.tileW / 2) * LevelMap.TILE_SIZE.toFloat(),
                        y = floorPx - eliteType.height * 1.15f,
                        difficultyScale = biome.difficultyScale,
                        isElite = true
                    )
                )
                repeat(2) { idx ->
                    val mType = biome.enemyPool[rng.nextInt(biome.enemyPool.size)]
                    val mx = (room.tileX + 4 + idx * (room.tileW - 8)) * LevelMap.TILE_SIZE.toFloat()
                    map.enemies.add(Enemy(mType, mx, floorPx - mType.height, biome.difficultyScale))
                }
                val scrollColor = StatColor.entries[rng.nextInt(StatColor.entries.size)]
                map.interactables.add(
                    Interactable(
                        type = InteractableType.SCROLL_SHRINE,
                        x = (room.tileX + room.tileW - 4) * LevelMap.TILE_SIZE.toFloat(),
                        y = floorPx - 18f,
                        scrollColor = scrollColor,
                        label = "${scrollColor.label} SCROLL"
                    )
                )
            }
            RoomKind.BOSS -> {
                val bossType = biome.bossType ?: return
                map.enemies.add(
                    Enemy(
                        type = bossType,
                        x = (room.tileX + room.tileW / 2 + 2) * LevelMap.TILE_SIZE.toFloat(),
                        y = floorPx - bossType.height,
                        difficultyScale = 1f,
                        isElite = false
                    )
                )
            }
            RoomKind.EXIT -> {
                // Small guard squad before exit
                repeat(2) { idx ->
                    val type = biome.enemyPool[rng.nextInt(biome.enemyPool.size)]
                    val ex = (room.tileX + 4 + idx * 5) * LevelMap.TILE_SIZE.toFloat()
                    map.enemies.add(Enemy(type, ex, floorPx - type.height, biome.difficultyScale))
                }
            }
        }
    }
}
