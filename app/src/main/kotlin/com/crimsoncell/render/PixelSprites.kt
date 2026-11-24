package com.crimsoncell.render

import android.graphics.Bitmap
import com.crimsoncell.enemy.EnemyType

/**
 * Procedurally builds crisp retro pixel-art Bitmap sprites at startup from
 * compact color-indexed pixel matrices. Zero external asset dependencies.
 */
class PixelSprites {

    val heroIdle: Bitmap
    val heroRun1: Bitmap
    val heroRun2: Bitmap
    val heroRoll: Bitmap
    val chestClosed: Bitmap
    val chestOpen: Bitmap
    val shrine: Bitmap
    val merchant: Bitmap
    val forge: Bitmap
    val crate: Bitmap
    val enemySprites: Map<EnemyType, Bitmap>

    init {
        val heroPalette = mapOf(
            'F' to 0xFFFF2E63.toInt(), // Glowing flame head
            'Y' to 0xFFFFD166.toInt(), // Bright eye core
            'S' to 0xFFE63946.toInt(), // Crimson scarf
            'C' to 0xFF334155.toInt(), // Dark cloak
            'B' to 0xFF1E293B.toInt(), // Boots / dark outline
            'A' to 0xFF64748B.toInt()  // Armor trim
        )

        heroIdle = fromMatrix(
            listOf(
                "....FF......",
                "...FYYF.....",
                "...FFFF.....",
                "..SSSSSS....",
                "..CCACCC....",
                "..CCACCC....",
                "..CCCCCC....",
                "...CCCC.....",
                "...C..C.....",
                "...B..B.....",
                "...B..B.....",
                "............"
            ), heroPalette
        )

        heroRun1 = fromMatrix(
            listOf(
                ".....FF.....",
                "....FYYF....",
                "....FFFF....",
                "..SSSSSS....",
                "...CCACC....",
                "...CCACC....",
                "...CCCCC....",
                "..CC..CC....",
                ".BB....BB...",
                "............",
                "............",
                "............"
            ), heroPalette
        )

        heroRun2 = fromMatrix(
            listOf(
                "....FF......",
                "...FYYF.....",
                "...FFFF.....",
                "..SSSSSS....",
                "..CCACCC....",
                "..CCACCC....",
                "...CCCCC....",
                "...CC.CC....",
                "...B...B....",
                "..B....B....",
                "............",
                "............"
            ), heroPalette
        )

        heroRoll = fromMatrix(
            listOf(
                "............",
                "............",
                "....SSSS....",
                "..SSCCCCSS..",
                ".SCCFYYFCCS.",
                ".SCCCCCCCCS.",
                "..SSBBBBSS..",
                "....BBBB....",
                "............",
                "............",
                "............",
                "............"
            ), heroPalette
        )

        val chestPal = mapOf(
            'W' to 0xFF78350F.toInt(),
            'D' to 0xFF451A03.toInt(),
            'G' to 0xFFFACC15.toInt(),
            'K' to 0xFF0F172A.toInt()
        )
        chestClosed = fromMatrix(
            listOf(
                ".KKKKKKKKKK.",
                "KWWWWWWWWWWK",
                "KWWGGGGGGWWK",
                "KDDDDGGDDDDK",
                "KWWWWGGWWWWK",
                "KWWWWWWWWWWK",
                "KDDDDDDDDDDK",
                ".KKKKKKKKKK."
            ), chestPal
        )
        chestOpen = fromMatrix(
            listOf(
                "KDDDDDDDDDDK",
                "KWWWWWWWWWWK",
                ".KKKKKKKKKK.",
                "KGGGGGGGGGGK",
                "KWWWWGGWWWWK",
                "KWWWWWWWWWWK",
                "KDDDDDDDDDDK",
                ".KKKKKKKKKK."
            ), chestPal
        )

        val shrinePal = mapOf(
            'S' to 0xFF64748B.toInt(),
            'D' to 0xFF334155.toInt(),
            'G' to 0xFFFACC15.toInt(),
            'W' to 0xFFF8FAFC.toInt()
        )
        shrine = fromMatrix(
            listOf(
                "....GG....",
                "...GWWG...",
                "...GWWG...",
                "....GG....",
                "..SSSSSS..",
                "...SDDS...",
                "...SDDS...",
                "..SSSSSS..",
                ".DDDDDDDD.",
                "DDDDDDDDDD"
            ), shrinePal
        )

        val merchantPal = mapOf(
            'H' to 0xFF7E22CE.toInt(),
            'E' to 0xFFFACC15.toInt(),
            'C' to 0xFF581C87.toInt(),
            'B' to 0xFF1E1B4B.toInt(),
            'G' to 0xFFF59E0B.toInt()
        )
        merchant = fromMatrix(
            listOf(
                "...HHHH...",
                "..HHHHHH..",
                "..HBEBH...",
                "..CCCCCC..",
                ".CCGCCCC..",
                ".CCGGCCC..",
                "..CCCCCC..",
                "..C....C..",
                "..B....B.."
            ), merchantPal
        )

        val forgePal = mapOf(
            'M' to 0xFF475569.toInt(),
            'D' to 0xFF1E293B.toInt(),
            'C' to 0xFF38BDF8.toInt(),
            'W' to 0xFFE0F2FE.toInt()
        )
        forge = fromMatrix(
            listOf(
                "....CC....",
                "...CWWC...",
                "..CCWWCC..",
                "...CCCC...",
                ".MMMMMMMM.",
                "..DDDDDD..",
                "...MMMM...",
                "..MMMMMM..",
                ".DDDDDDDD."
            ), forgePal
        )

        val cratePal = mapOf(
            'W' to 0xFF92400E.toInt(),
            'L' to 0xFFB45309.toInt(),
            'D' to 0xFF451A03.toInt()
        )
        crate = fromMatrix(
            listOf(
                "DDDDDDDD",
                "DLLLLLLD",
                "DLWDDWLD",
                "DLWWWWLD",
                "DLWDDWLD",
                "DLLLLLLD",
                "DDDDDDDD"
            ), cratePal
        )

        enemySprites = EnemyType.entries.associateWith { buildEnemySprite(it) }
    }

    private fun buildEnemySprite(type: EnemyType): Bitmap {
        val pal = mapOf(
            'P' to type.primaryColor,
            'A' to type.accentColor,
            'E' to 0xFFFACC15.toInt(),
            'W' to 0xFFF8FAFC.toInt(),
            'D' to 0xFF0F172A.toInt()
        )
        val matrix = when (type) {
            EnemyType.CRIMSON_BAT -> listOf(
                "A........A",
                "PA..PP..AP",
                "PPPEPPPEPP",
                ".PPPPPPPP.",
                "..PP..PP.."
            )
            EnemyType.SHIELDBEARER -> listOf(
                "...PPPP...",
                "..PPEEPP..",
                ".AAPPPP...",
                ".AAPPPPP..",
                ".AAPPPPP..",
                ".AAPPPP...",
                "...P..P...",
                "...D..D..."
            )
            EnemyType.GOLEM_BRUISER -> listOf(
                "..PPPPPP..",
                ".PPPEEPPP.",
                "PPPPPPPPPP",
                "PAPPPPPPAP",
                "PPPPPPPPPP",
                ".PPPPPPPP.",
                ".PPP..PPP.",
                ".DDD..DDD."
            )
            EnemyType.BOSS_IRON_WARDEN -> listOf(
                "...AAAAAA...",
                "..PPPPPPPP..",
                ".PPPEEEEPPP.",
                "PPPPPPPPPPPP",
                "PAAPPPPPPAAP",
                "PPPPPPPPPPPP",
                ".PPPPPPPPPP.",
                ".PPPP..PPPP.",
                ".DDDD..DDDD."
            )
            EnemyType.BOSS_CRIMSON_REAPER -> listOf(
                "....AAAA....",
                "...AAPAAA...",
                "..PPPEEPPP..",
                ".PPPPPPPPPP.",
                "AAAPPPPPPPAA",
                ".PPPPPPPPPP.",
                "..PP.PP.PP..",
                "..D..DD..D..",
                "............"
            )
            else -> listOf(
                "...PPPP...",
                "..PPEEPP..",
                "..PPPPPP..",
                ".APPPPPPA.",
                "..PPPPPP..",
                "...PPPP...",
                "...P..P...",
                "...D..D..."
            )
        }
        return fromMatrix(matrix, pal)
    }

    private fun fromMatrix(rows: List<String>, palette: Map<Char, Int>): Bitmap {
        val h = rows.size
        val w = rows.maxOf { it.length }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) {
            val row = rows[y]
            for (x in row.indices) {
                val c = row[x]
                val color = palette[c] ?: 0
                if (color != 0) bmp.setPixel(x, y, color)
            }
        }
        return bmp
    }
}
