package com.crimsoncell.world

import com.crimsoncell.combat.StatColor
import com.crimsoncell.combat.WeaponInstance
import com.crimsoncell.enemy.Enemy

enum class TileType {
    EMPTY, SOLID, ONE_WAY, SPIKES, ACID
}

enum class RoomKind {
    START, COMBAT, TREASURE, SHOP, SHRINE, ELITE, BOSS, EXIT
}

data class RoomRect(
    val id: Int,
    val kind: RoomKind,
    val tileX: Int,
    val tileY: Int,
    val tileW: Int,
    val tileH: Int,
    var discovered: Boolean = false
)

enum class InteractableType {
    CHEST, WEAPON_PEDESTAL, SCROLL_SHRINE, MERCHANT, CELL_FORGE, EXIT_PORTAL, CRATE
}

data class Interactable(
    val type: InteractableType,
    var x: Float,
    var y: Float,
    var used: Boolean = false,
    var weapon: WeaponInstance? = null,
    var scrollColor: StatColor? = null,
    var price: Int = 0,
    var label: String = ""
)

data class TorchLight(val x: Float, val y: Float)

class LevelMap(
    val biome: BiomeConfig,
    val widthTiles: Int,
    val heightTiles: Int
) {
    val tiles = Array(heightTiles) { Array(widthTiles) { TileType.SOLID } }
    val rooms = ArrayList<RoomRect>()
    val interactables = ArrayList<Interactable>()
    val enemies = ArrayList<Enemy>()
    val torches = ArrayList<TorchLight>()

    var spawnX: Float = 48f
    var spawnY: Float = 48f
    var exitX: Float = 100f
    var exitY: Float = 48f
    var bossRoom: RoomRect? = null
    var bossDefeated: Boolean = false

    val widthPx: Int get() = widthTiles * TILE_SIZE
    val heightPx: Int get() = heightTiles * TILE_SIZE

    fun getTile(tx: Int, ty: Int): TileType {
        if (tx < 0 || ty < 0 || tx >= widthTiles || ty >= heightTiles) return TileType.SOLID
        return tiles[ty][tx]
    }

    fun setTile(tx: Int, ty: Int, type: TileType) {
        if (tx in 0 until widthTiles && ty in 0 until heightTiles) {
            tiles[ty][tx] = type
        }
    }

    fun isSolid(tx: Int, ty: Int): Boolean = getTile(tx, ty) == TileType.SOLID
    fun isOneWay(tx: Int, ty: Int): Boolean = getTile(tx, ty) == TileType.ONE_WAY
    fun isHazard(tx: Int, ty: Int): Boolean {
        val t = getTile(tx, ty)
        return t == TileType.SPIKES || t == TileType.ACID
    }

    fun worldToTile(px: Float): Int = kotlin.math.floor(px / TILE_SIZE).toInt()

    fun isStandingOnOneWay(x: Float, bottomY: Float, width: Float): Boolean {
        val ty = worldToTile(bottomY + 1f)
        val left = worldToTile(x + 1f)
        val right = worldToTile(x + width - 1f)
        for (tx in left..right) {
            if (isOneWay(tx, ty)) return true
        }
        return false
    }

    fun markRoomDiscovered(px: Float, py: Float) {
        val tx = worldToTile(px)
        val ty = worldToTile(py)
        for (room in rooms) {
            if (tx in room.tileX until (room.tileX + room.tileW) &&
                ty in room.tileY until (room.tileY + room.tileH)
            ) {
                room.discovered = true
            }
        }
    }

    companion object {
        const val TILE_SIZE = 16
    }
}
